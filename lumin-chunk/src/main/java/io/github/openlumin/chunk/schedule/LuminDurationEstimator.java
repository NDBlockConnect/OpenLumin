package io.github.openlumin.chunk.schedule;

/**
 * 在线耗时估计器（原理参照：指数衰减线性回归）。
 * <p>模型 {@code duration ≈ slope × effort + intercept}，以指数衰减加权的最小二乘在线更新，
 * 并限制新样本在总权重中的占比（{@value #NEW_SAMPLE_WEIGHT_CAP}），使模型对分布突变
 * 有响应但不被单次离群样本带偏。</p>
 * <p>初始模型：斜率为 0、截距为 {@code initialDurationNanos}（保守常量），
 * 在样本不足时退化为"按初始常量估计"，随样本积累逐步收敛到实测关系。
 * 估计值恒为非负。</p>
 * <p><b>线程安全</b>：样本记录可能来自多个 Worker 线程，读取来自调度线程；
 * 累加与重拟合在 {@code synchronized} 内完成，估计读取亦同步（短临界区，无阻塞风险）。</p>
 */
public final class LuminDurationEstimator {

    private static final double NEW_SAMPLE_WEIGHT_CAP = 0.05;
    private static final double MIN_TOTAL_WEIGHT = 1e-9;

    private final long initialDurationNanos;

    private double totalWeight;
    private double weightedEffortSum;
    private double weightedDurationSum;
    private double weightedEffortSquaredSum;
    private double weightedEffortDurationSum;

    private double slope;
    private double intercept;

    /**
     * @param initialDurationNanos 无足够样本时的保守耗时估计（>0）
     */
    public LuminDurationEstimator(long initialDurationNanos) {
        if (initialDurationNanos <= 0) {
            throw new IllegalArgumentException("initialDurationNanos must be > 0, got " + initialDurationNanos);
        }
        this.initialDurationNanos = initialDurationNanos;
        this.intercept = initialDurationNanos;
        this.slope = 0.0;
    }

    /**
     * 记录一次实测样本并更新模型。
     */
    public void record(long effort, long durationNanos) {
        record(new LuminJobEffort(effort, durationNanos));
    }

    public synchronized void record(LuminJobEffort sample) {
        double x = sample.effort();
        double y = sample.durationNanos();

        // 权重上限：新样本最多占已有总权重的 NEW_SAMPLE_WEIGHT_CAP 份额
        double weight = Math.max(1.0, totalWeight * NEW_SAMPLE_WEIGHT_CAP);

        totalWeight += weight;
        weightedEffortSum += weight * x;
        weightedDurationSum += weight * y;
        weightedEffortSquaredSum += weight * x * x;
        weightedEffortDurationSum += weight * x * y;

        refit();
    }

    /**
     * 估计给定工作量的耗时（纳秒，非负）。
     */
    public synchronized long estimateDurationNanos(long effort) {
        if (effort < 0) {
            throw new IllegalArgumentException("effort must be >= 0, got " + effort);
        }
        if (totalWeight < MIN_TOTAL_WEIGHT) {
            return initialDurationNanos;
        }
        double predicted = slope * effort + intercept;
        return predicted <= 0.0 ? 0L : (long) predicted;
    }

    /** 当前模型斜率（纳秒/工作量单位）。 */
    public synchronized double slope() {
        return slope;
    }

    /** 当前模型截距（纳秒）。 */
    public synchronized double intercept() {
        return intercept;
    }

    private void refit() {
        if (totalWeight < MIN_TOTAL_WEIGHT) {
            return;
        }
        double meanX = weightedEffortSum / totalWeight;
        double meanY = weightedDurationSum / totalWeight;
        double varX = weightedEffortSquaredSum / totalWeight - meanX * meanX;
        double covXY = weightedEffortDurationSum / totalWeight - meanX * meanY;
        if (varX <= MIN_TOTAL_WEIGHT) {
            // 工作量方差不足（样本工作量近似相同）→ 只更新截距，斜率保持
            intercept = meanY;
            return;
        }
        double nextSlope = covXY / varX;
        if (nextSlope < 0.0) {
            // 负斜率无物理意义（更多工作量不可能更快）→ 钳平
            nextSlope = 0.0;
        }
        slope = nextSlope;
        intercept = meanY - nextSlope * meanX;
        if (intercept < 0.0) {
            intercept = 0.0;
        }
    }
}
