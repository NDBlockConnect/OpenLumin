package io.github.openlumin.chunk.schedule;

/**
 * 帧预算计算器（原理参照：以"线程池一帧容量"为提交上限）。
 *
 * <p>核心量：</p>
 * <ul>
 *   <li><b>平均帧时长</b>：帧时长的指数移动平均（更新比例 {@value #FRAME_DURATION_UPDATE_RATIO}），
 *       钳制在 [{@value #MIN_FRAME_DURATION_NANOS}, {@value #MAX_FRAME_DURATION_NANOS}] 纳秒；</li>
 *   <li><b>忙碌度</b> {@code busyFraction} = 队列总估计耗时 /（平均帧时长 × 线程数）——反映
 *       线程池已被占用的比例；</li>
 *   <li><b>剩余容量</b> {@code remainingDuration} = max(0, 线程数 × 平均帧时长 − 队列总估计耗时)
 *       ——本帧还允许新提交多少估计耗时。</li>
 * </ul>
 *
 * <p>提交方按"逐任务扣减剩余容量"的方式限制本帧入队量；容量耗尽即停止提交（延后到下一帧）。
 * 上传预算同理，按估计上传耗时与估计字节数双重约束（字节上限由上传层给出）。</p>
 *
 * <p><b>线程安全</b>：队列账目（{@link #onQueued}/{@link #onCompleted} 与读取）由
 * 原子量维护——提交在调度线程、完成回收在 Worker 线程，二者并发。<b>帧时长EMA
 * （{@link #recordFrameDuration}）与 {@link #averageFrameDurationNanos()} 仍为
 * 单线程使用</b>（调度线程每帧调用一次）。</p>
 */
public final class LuminFrameBudget {

    public static final double FRAME_DURATION_UPDATE_RATIO = 0.05;
    public static final long MIN_FRAME_DURATION_NANOS = 1_000_000L;
    public static final long MAX_FRAME_DURATION_NANOS = 100_000_000L;
    public static final double UPLOAD_DURATION_FRACTION = 0.3;
    public static final long MIN_UPLOAD_DURATION_NANOS = 10_000_000L;

    private final int threadCount;

    private volatile long averageFrameDurationNanos = MIN_FRAME_DURATION_NANOS;
    private volatile boolean averageInitialized;
    private final java.util.concurrent.atomic.AtomicLong queuedEstimatedDurationNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong queuedEstimatedUploadBytes =
            new java.util.concurrent.atomic.AtomicLong();

    public LuminFrameBudget(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("threadCount must be >= 1, got " + threadCount);
        }
        this.threadCount = threadCount;
    }

    public int threadCount() {
        return threadCount;
    }

    public long averageFrameDurationNanos() {
        return averageFrameDurationNanos;
    }

    /** 以实测帧时长更新移动平均（超范围值被钳制）。首次调用直接设定初值（冷启动修复）。 */
    public void recordFrameDuration(long frameDurationNanos) {
        long clamped = Math.max(MIN_FRAME_DURATION_NANOS, Math.min(MAX_FRAME_DURATION_NANOS, frameDurationNanos));
        if (!averageInitialized) {
            averageInitialized = true;
            averageFrameDurationNanos = clamped;
            return;
        }
        // 增量式 EMA：步长至少 1ns，保证恒定输入下能精确收敛（朴素浮点 EMA 会在目标附近停滞）
        long delta = clamped - averageFrameDurationNanos;
        if (delta == 0) {
            return;
        }
        long step = Math.round(FRAME_DURATION_UPDATE_RATIO * delta);
        if (step == 0) {
            step = delta > 0 ? 1 : -1;
        }
        averageFrameDurationNanos += step;
        averageFrameDurationNanos = Math.max(MIN_FRAME_DURATION_NANOS,
                Math.min(MAX_FRAME_DURATION_NANOS, averageFrameDurationNanos));
    }

    /** 入队：累加估计耗时与估计上传字节（线程安全）。 */
    public void onQueued(long estimatedDurationNanos, long estimatedUploadBytes) {
        if (estimatedDurationNanos < 0 || estimatedUploadBytes < 0) {
            throw new IllegalArgumentException("estimates must be >= 0");
        }
        queuedEstimatedDurationNanos.addAndGet(estimatedDurationNanos);
        queuedEstimatedUploadBytes.addAndGet(estimatedUploadBytes);
    }

    /** 出队/完成：扣减估计耗时与估计上传字节（线程安全，不会降到负值）。 */
    public void onCompleted(long estimatedDurationNanos, long estimatedUploadBytes) {
        if (estimatedDurationNanos < 0 || estimatedUploadBytes < 0) {
            throw new IllegalArgumentException("estimates must be >= 0");
        }
        subtractClamped(queuedEstimatedDurationNanos, estimatedDurationNanos);
        subtractClamped(queuedEstimatedUploadBytes, estimatedUploadBytes);
    }

    private static void subtractClamped(java.util.concurrent.atomic.AtomicLong counter, long amount) {
        long previous;
        do {
            previous = counter.get();
            if (previous == 0L) {
                return;
            }
        } while (!counter.compareAndSet(previous, Math.max(0L, previous - amount)));
    }

    public long queuedEstimatedDurationNanos() {
        return queuedEstimatedDurationNanos.get();
    }

    public long queuedEstimatedUploadBytes() {
        return queuedEstimatedUploadBytes.get();
    }

    /** 忙碌度 = 队列总估计耗时 /（平均帧时长 × 线程数），钳制 [0,1]。 */
    public float busyFraction() {
        double capacity = (double) averageFrameDurationNanos * threadCount;
        if (capacity <= 0.0) {
            return 1.0f;
        }
        double fraction = queuedEstimatedDurationNanos.get() / capacity;
        return (float) Math.max(0.0, Math.min(1.0, fraction));
    }

    /** 本帧剩余可提交的估计耗时（纳秒，非负）。 */
    public long remainingDurationNanos() {
        long capacity = averageFrameDurationNanos * threadCount;
        return Math.max(0L, capacity - queuedEstimatedDurationNanos.get());
    }

    /** 本帧剩余上传时长预算（纳秒）；上传字节上限由上传层另行给出。 */
    public long uploadDurationBudgetNanos() {
        return Math.max(MIN_UPLOAD_DURATION_NANOS,
                (long) (averageFrameDurationNanos * UPLOAD_DURATION_FRACTION));
    }
}
