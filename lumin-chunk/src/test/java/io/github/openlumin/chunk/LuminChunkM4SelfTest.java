package io.github.openlumin.chunk;

import io.github.openlumin.chunk.schedule.LuminDeferMode;
import io.github.openlumin.chunk.schedule.LuminDurationEstimator;
import io.github.openlumin.chunk.schedule.LuminFrameBudget;
import io.github.openlumin.chunk.schedule.LuminJobEffort;
import io.github.openlumin.chunk.schedule.LuminSubmissionBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WP-1 M4a 自测：构建调度增强——在线耗时估计（指数衰减回归）、帧预算（忙碌度/剩余容量）、
 * 三档延迟与提交预算双重约束；聚合 M3（含 M2/M1）全量回归。
 */
public final class LuminChunkM4SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M4 全部测试节（先聚合 M3+M2+M1 回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM3SelfTest.runAll();
        section("estimator constant fallback", LuminChunkM4SelfTest::testEstimatorFallback);
        section("estimator learns slope", LuminChunkM4SelfTest::testEstimatorConvergence);
        section("estimator clamps negative slope", LuminChunkM4SelfTest::testEstimatorNegativeSlopeClamp);
        section("frame budget math", LuminChunkM4SelfTest::testFrameBudgetMath);
        section("frame budget frame-time EMA", LuminChunkM4SelfTest::testFrameDurationEma);
        section("submission budget dual limits", LuminChunkM4SelfTest::testSubmissionBudget);
        section("defer mode upload policy", LuminChunkM4SelfTest::testDeferModePolicy);
        section("builder budget integration", LuminChunkM4SelfTest::testBuilderIntegration);
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void testEstimatorFallback() {
        LuminDurationEstimator estimator = new LuminDurationEstimator(5_000_000L);
        check(estimator.estimateDurationNanos(0) == 5_000_000L, "no samples -> constant fallback");
        check(estimator.estimateDurationNanos(1_000_000) == 5_000_000L, "fallback independent of effort");
        check(estimator.slope() == 0.0, "slope starts at zero");
    }

    private static void testEstimatorConvergence() {
        LuminDurationEstimator estimator = new LuminDurationEstimator(5_000_000L);
        // 真实模型：duration = 1000 * effort（纳秒），effort 以字节计
        for (int i = 1; i <= 400; i++) {
            long effort = i * 100L;
            estimator.record(new LuminJobEffort(effort, effort * 1000L));
        }
        long predicted = estimator.estimateDurationNanos(100_000L);
        // 目标真值 100_000 * 1000 = 100ms；容差 20%
        check(predicted > 80_000_000L && predicted < 120_000_000L,
                "learned prediction within 20%, got " + predicted);
        check(estimator.slope() > 800.0 && estimator.slope() < 1200.0,
                "learned slope near 1000, got " + estimator.slope());
    }

    private static void testEstimatorNegativeSlopeClamp() {
        LuminDurationEstimator estimator = new LuminDurationEstimator(1_000_000L);
        // 注入噪声：大工作量反而更快 -> 斜率会被钳平
        estimator.record(new LuminJobEffort(1_000_000L, 1_000_000L));
        estimator.record(new LuminJobEffort(10L, 9_000_000L));
        check(estimator.slope() >= 0.0, "slope must never be negative, got " + estimator.slope());
        check(estimator.estimateDurationNanos(10L) >= 0L, "estimate must be non-negative");
    }

    private static void testFrameBudgetMath() {
        LuminFrameBudget budget = new LuminFrameBudget(4);
        long frame = 16_000_000L; // 16ms
        // EMA 永不精确等于输入：迭代足够多次后应收敛到误差带内
        for (int i = 0; i < 300; i++) {
            budget.recordFrameDuration(frame);
        }
        long ema = budget.averageFrameDurationNanos();
        check(Math.abs(ema - frame) < frame / 50, "EMA converges into 2% band, got " + ema);
        check(budget.busyFraction() == 0f, "empty queue -> zero busy fraction");
        long capacity = ema * 4;
        check(budget.remainingDurationNanos() == capacity, "full capacity available when idle");

        budget.onQueued(capacity / 2, 0L);
        check(Math.abs(budget.busyFraction() - 0.5f) < 1e-2f, "half queue -> 0.5 busy");
        check(budget.remainingDurationNanos() == capacity - capacity / 2, "remaining halved");

        budget.onQueued(capacity, 0L);
        check(budget.busyFraction() == 1.0f, "over-queued clamps busy fraction to 1");
        check(budget.remainingDurationNanos() == 0L, "no remaining capacity when saturated");

        budget.onCompleted(capacity, 0L);
        budget.onCompleted(capacity, 0L);
        check(budget.queuedEstimatedDurationNanos() == 0L, "completions drain the queue estimate");
        check(budget.remainingDurationNanos() == capacity, "capacity fully restored");
    }

    private static void testFrameDurationEma() {
        LuminFrameBudget budget = new LuminFrameBudget(2);
        // 单帧超长：输入被钳制到上限，EMA 平滑逼近（不是瞬间跳到上限）
        long before = budget.averageFrameDurationNanos();
        budget.recordFrameDuration(1_000_000_000L);
        long after = budget.averageFrameDurationNanos();
        check(after > before, "huge frame moves the EMA up, " + before + " -> " + after);
        // 持续超长输入 -> 收敛到上限（增量式 EMA 每步至少 1ns，需要足够迭代次数）
        for (int i = 0; i < 600; i++) {
            budget.recordFrameDuration(1_000_000_000L);
        }
        check(budget.averageFrameDurationNanos() == LuminFrameBudget.MAX_FRAME_DURATION_NANOS,
                "sustained huge frames converge to max, got " + budget.averageFrameDurationNanos());
        // 极小输入被钳制到下限
        budget.recordFrameDuration(1L);
        check(budget.averageFrameDurationNanos() >= LuminFrameBudget.MIN_FRAME_DURATION_NANOS,
                "tiny frame duration never drives EMA below min");
        // 上传预算 = 30% 帧时长，且不低于 10ms
        LuminFrameBudget b2 = new LuminFrameBudget(1);
        for (int i = 0; i < 40; i++) {
            b2.recordFrameDuration(2_000_000L); // 2ms
        }
        check(b2.uploadDurationBudgetNanos() == LuminFrameBudget.MIN_UPLOAD_DURATION_NANOS,
                "upload duration budget floor at 10ms, got " + b2.uploadDurationBudgetNanos());
    }

    private static void testSubmissionBudget() {
        LuminSubmissionBudget budget = new LuminSubmissionBudget(1_000_000L, 500_000L, 4096L, false);
        check(budget.hasBudgetRemaining(), "fresh budget has room");
        check(budget.tryConsume(400_000L, 100_000L, 1024L), "first consume fits");
        check(budget.durationRemainingNanos() == 600_000L, "duration deducted");
        check(budget.uploadBytesRemaining() == 3072L, "bytes deducted");
        // 时长够但上传字节不够 -> 拒绝且不扣减
        check(!budget.tryConsume(100_000L, 100_000L, 10_000L), "byte limit rejects");
        check(budget.durationRemainingNanos() == 600_000L, "rejection must not deduct duration");
        // 时长不够 -> 拒绝
        check(!budget.tryConsume(900_000L, 1L, 1L), "duration limit rejects");
        // 单一限制耗尽 -> hasBudgetRemaining false
        check(budget.tryConsume(600_000L, 100_000L, 3072L), "drain both budgets");
        check(!budget.hasBudgetRemaining(), "exhausted budget reports no room");

        // 本帧阻塞档忽略上传约束
        LuminSubmissionBudget blocking = new LuminSubmissionBudget(10_000L, 1L, 1L, true);
        check(blocking.hasBudgetRemaining(), "blocking tier ignores upload limits");
        check(blocking.tryConsume(10_000L, 0L, 0L), "blocking consume limited only by duration");
        check(!blocking.hasBudgetRemaining(), "duration exhausted ends blocking budget");
    }

    private static void testDeferModePolicy() {
        check(LuminDeferMode.ZERO_FRAMES.allowsUnlimitedUploadDuration(),
                "ZERO_FRAMES blocks this frame, so no upload cap");
        check(!LuminDeferMode.ONE_FRAME.allowsUnlimitedUploadDuration(),
                "ONE_FRAME defers, so uploads stay budgeted");
        check(!LuminDeferMode.ALWAYS_FRAME.allowsUnlimitedUploadDuration(),
                "ALWAYS_FRAME defers, so uploads stay budgeted");
    }

    private static void testBuilderIntegration() throws Exception {
        try (LuminChunkBuilder builder = new LuminChunkBuilder(2)) {
            // 初值：无样本 -> 常量回退
            check(builder.estimateDurationNanos(0) == 5_000_000L, "builder uses constant fallback initially");
            LuminFrameBudget budget = builder.frameBudget();
            check(budget.threadCount() == 2, "frame budget knows the pool size");
            check(budget.busyFraction() == 0f, "idle builder has zero busy fraction");

            // 提交一批带工作量提示的任务，验证预算被计入并最终排空
            List<LuminChunkJob<LuminChunkM1SelfTest.TestMesh>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                final int size = (i + 1) * 8;
                jobs.add(builder.scheduleTask(
                        (context, cancel) -> new LuminChunkM1SelfTest.TestMesh(new float[size], new int[0]),
                        false, null, size));
            }
            for (LuminChunkJob<LuminChunkM1SelfTest.TestMesh> job : jobs) {
                check(job.await(15, TimeUnit.SECONDS) != null, "job completes");
                check(job.estimatedDurationNanos() > 0L, "estimated duration is budgeted");
                check(job.effortHint() > 0L, "effort hint is retained");
            }
            check(builder.isQueueEmpty(), "all jobs drained");
            // isQueueEmpty 只表示无待取任务；in-flight 任务的预算回收是异步的（Worker finally 块内）
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (budget.queuedEstimatedDurationNanos() != 0L && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(2);
            }
            check(budget.queuedEstimatedDurationNanos() == 0L,
                    "queue estimate fully drained, left=" + budget.queuedEstimatedDurationNanos());

            // 估计器已获得样本：斜率非负（本用例工作量线性增长，耗时为正相关）
            check(builder.durationEstimatorSlope() >= 0.0, "slope non-negative after samples");
            check(builder.estimateDurationNanos(1024L) > 0L, "estimate positive after samples");

            // 记录帧时长后容量可计算
            builder.frameBudget().recordFrameDuration(8_000_000L);
            check(builder.frameBudget().remainingDurationNanos() > 0L, "capacity available next frame");
        }
    }

    private LuminChunkM4SelfTest() {
    }
}
