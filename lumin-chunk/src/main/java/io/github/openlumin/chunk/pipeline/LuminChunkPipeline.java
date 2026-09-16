package io.github.openlumin.chunk.pipeline;

import io.github.openlumin.chunk.CancellationToken;
import io.github.openlumin.chunk.LuminChunkBuilder;
import io.github.openlumin.chunk.LuminChunkJob;
import io.github.openlumin.chunk.LuminChunkJobResult;
import io.github.openlumin.chunk.LuminChunkTask;
import io.github.openlumin.chunk.cull.LuminCullRequest;
import io.github.openlumin.chunk.cull.LuminCullResult;
import io.github.openlumin.chunk.cull.LuminOcclusionCuller;
import io.github.openlumin.chunk.cull.LuminSectionPos;
import io.github.openlumin.chunk.schedule.LuminDeferMode;
import io.github.openlumin.chunk.schedule.LuminSubmissionBudget;
import io.github.openlumin.chunk.store.LuminSectionAllocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 区块渲染管线协调器（WP-1 M5c）：把"构建调度（M4a）→ 量化网格（M4b）→ Store 槽位记账（M2/M4c）
 * → 遮挡剔除（M3b/M5a）"串成一条每帧可驱动的 CPU 侧流水。
 *
 * <p>每帧时序（调用方驱动）：</p>
 * <pre>
 *   beginFrame(frameDurationNanos)       // 记录帧时长，刷新帧预算
 *   requestBuild(section, effort, mode)  // 入队构建（受帧预算与延迟档约束）
 *   ...                                  // 构建在线程池执行，完成回调回收产出
 *   cull(camera, frustum, distance, onVisible)  // 剔除得到可见集
 *   endFrame()                           // 汇总统计
 * </pre>
 *
 * <p>与 GPU 的边界：本类不触碰任何 GPU 资源——构建产出的 {@link LuminChunkMesh} 与
 * {@link LuminSectionAllocation} 由调用方（基线 GPU 层）负责上传与绘制。</p>
 *
 * <p>非线程安全（每帧由渲染线程驱动）；内部任务执行在 {@link LuminChunkBuilder} 的线程池上。</p>
 */
public final class LuminChunkPipeline implements AutoCloseable {

    /** 构建完成回调（在 Worker 线程执行）：产出网格 + 记账槽位。 */
    @FunctionalInterface
    public interface MeshConsumer {
        void onMeshReady(LuminSectionPos section, LuminChunkMesh mesh, LuminSectionAllocation allocation);
    }

    /** 可见集回调（由剔除遍历驱动）。 */
    @FunctionalInterface
    public interface VisibleConsumer {
        void onVisible(LuminSectionPos section);
    }

    private final LuminChunkBuilder builder;
    private final LuminOcclusionCuller culler;

    /**
     * 可变状态的统一锁：{@code requestBuild} 在渲染线程写，{@code onBuildCompleted}
     * 在 Worker 线程读写——所有对这些映射的访问都必须持有本锁
     * （M4a 的"预算账目并发丢失更新"教训：跨线程共享的账目必须先设计并发性）。
     */
    private final Object stateLock = new Object();
    private final Map<Long, PendingBuild> pending = new HashMap<>();
    private final Map<Long, LuminSectionPos> pendingPositions = new HashMap<>();
    private final Map<Long, LuminSectionAllocation> allocated = new HashMap<>();
    private final Map<Long, Long> lastEffort = new HashMap<>();

    private long frameIndex;
    private int submittedThisFrame;
    private int deferredByBudget;
    private LuminSubmissionBudget frameSubmissionBudget;
    private MeshConsumer meshConsumer;

    private record PendingBuild(LuminSectionPos section, long effortHint, LuminDeferMode mode) {
    }

    public LuminChunkPipeline(LuminChunkBuilder builder, LuminOcclusionCuller culler) {
        if (builder == null || culler == null) {
            throw new NullPointerException("builder and culler are required");
        }
        this.builder = builder;
        this.culler = culler;
    }

    /** 设置构建产出回调（在任意线程调用；Worker 端经 stateLock 读取，保证可见性）。 */
    public void setMeshConsumer(MeshConsumer consumer) {
        synchronized (stateLock) {
            this.meshConsumer = consumer;
        }
    }

    /**
     * 开始一帧：记录帧时长并构造本帧提交预算。
     *
     * @param frameDurationNanos 上一帧实测时长（用于平均帧时长 EMA）
     * @param uploadDurationBudgetNanos 本帧允许的估计上传耗时
     * @param uploadBytesBudget         本帧允许的估计上传字节
     */
    public void beginFrame(long frameDurationNanos, long uploadDurationBudgetNanos, long uploadBytesBudget) {
        builder.frameBudget().recordFrameDuration(frameDurationNanos);
        frameSubmissionBudget = new LuminSubmissionBudget(
                builder.frameBudget().remainingDurationNanos(),
                uploadDurationBudgetNanos,
                uploadBytesBudget,
                false);
        submittedThisFrame = 0;
        deferredByBudget = 0;
    }

    /**
     * 请求构建一个 section（受帧预算与延迟档约束）。
     *
     * <p>{@link LuminDeferMode#ZERO_FRAMES} 不受预算限制（本帧必须完成）；
     * 其余档位在预算耗尽时被延后（返回 null，调用方下一帧重试）。</p>
     *
     * @param task 构建任务（产出 {@link LuminChunkMesh}）
     * @return 已提交的任务句柄；被预算延后时返回 null
     */
    public LuminChunkJob<LuminChunkMesh> requestBuild(LuminSectionPos section, long effortHint,
                                                      LuminDeferMode mode,
                                                      LuminChunkTask<LuminChunkMesh> task) {
        if (section == null || task == null) {
            throw new NullPointerException("section and task are required");
        }
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        long estimatedDuration = builder.estimateDurationNanos(effortHint);
        long estimatedUploadBytes = effortHint;
        if (frameSubmissionBudget != null && !mode.allowsUnlimitedUploadDuration()) {
            if (!frameSubmissionBudget.tryConsume(estimatedDuration, 0L, estimatedUploadBytes)) {
                deferredByBudget++;
                return null;
            }
        }
        long key = section.pack();
        synchronized (stateLock) {
            pending.put(key, new PendingBuild(section, effortHint, mode));
            pendingPositions.put(key, section);
            lastEffort.put(key, effortHint);
        }
        submittedThisFrame++;
        return builder.scheduleTask(task, mode == LuminDeferMode.ZERO_FRAMES,
                result -> onBuildCompleted(key, result), effortHint);
    }

    private void onBuildCompleted(long key, LuminChunkJobResult<LuminChunkMesh> result) {
        LuminSectionPos section;
        synchronized (stateLock) {
            section = pendingPositions.remove(key);
            pending.remove(key);
        }
        if (section == null) {
            // 未知 section（已被淘汰或重复回调）：关闭产出避免泄漏
            if (result.output() != null) {
                result.output().close();
            }
            return;
        }
        if (result.state() != LuminChunkJobResult.State.COMPLETED) {
            // 取消/失败：产出（若有）由构建器负责，此处只记账
            return;
        }
        LuminChunkMesh mesh = result.output();
        if (mesh == null) {
            return;
        }
        LuminSectionAllocation allocation;
        MeshConsumer consumer;
        synchronized (stateLock) {
            allocation = allocated.get(key);
            consumer = meshConsumer;
        }
        if (consumer != null) {
            consumer.onMeshReady(section, mesh, allocation);
        }
    }

    /**
     * 登记/更新某 section 的 Store 槽位记账（由上传路径在成功后调用）。
     */
    public void recordAllocation(LuminSectionPos section, LuminSectionAllocation allocation) {
        synchronized (stateLock) {
            allocated.put(section.pack(), allocation);
        }
    }

    /** 淘汰 section：清除记账（GPU 侧由调用方释放槽位/缓冲）。 */
    public void evict(LuminSectionPos section) {
        long key = section.pack();
        synchronized (stateLock) {
            allocated.remove(key);
            pending.remove(key);
            pendingPositions.remove(key);
            lastEffort.remove(key);
        }
    }

    /**
     * 执行剔除并把可见集交给回调。
     *
     * @return 剔除结果（含可见集与统计）
     */
    public LuminCullResult cull(LuminCullRequest request, CancellationToken cancel, VisibleConsumer consumer) {
        LuminCullResult result = culler.cull(request, cancel);
        if (consumer != null) {
            for (LuminSectionPos section : result.visibleSections()) {
                consumer.onVisible(section);
            }
        }
        return result;
    }

    /** 上一帧该 section 的构建工作量估计（用于下一帧 effortHint 与上传预算）。 */
    public long lastEffortFor(LuminSectionPos section) {
        synchronized (stateLock) {
            return lastEffort.getOrDefault(section.pack(), 0L);
        }
    }

    /** 已登记槽位的 section 数。 */
    public int allocatedSectionCount() {
        synchronized (stateLock) {
            return allocated.size();
        }
    }

    /** 在途构建数。 */
    public int inFlightBuildCount() {
        synchronized (stateLock) {
            return pending.size();
        }
    }

    /** 本帧已提交的构建数。 */
    public int submittedThisFrame() {
        return submittedThisFrame;
    }

    /** 本帧因预算不足被延后的请求数。 */
    public int deferredByBudget() {
        return deferredByBudget;
    }

    /** 当前帧序号（beginFrame 递增）。 */
    public long frameIndex() {
        return frameIndex;
    }

    /** 结束一帧：递增帧序号并返回本帧汇总统计。 */
    public FrameStats endFrame() {
        frameIndex++;
        int inFlight;
        int allocatedCount;
        synchronized (stateLock) {
            inFlight = pending.size();
            allocatedCount = allocated.size();
        }
        return new FrameStats(frameIndex - 1, submittedThisFrame, deferredByBudget,
                inFlight, allocatedCount,
                builder.frameBudget().busyFraction(),
                builder.frameBudget().remainingDurationNanos());
    }

    /** 一帧统计快照。 */
    public record FrameStats(long frameIndex, int submitted, int deferredByBudget,
                             int inFlightBuilds, int allocatedSections,
                             float busyFraction, long remainingDurationNanos) {
    }

    /** 全量待构建 section 列表（调试/诊断用）。 */
    public List<LuminSectionPos> pendingSections() {
        synchronized (stateLock) {
            return new ArrayList<>(pendingPositions.values());
        }
    }

    @Override
    public void close() {
        builder.close();
    }
}
