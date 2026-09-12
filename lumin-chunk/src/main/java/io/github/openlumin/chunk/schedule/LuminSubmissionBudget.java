package io.github.openlumin.chunk.schedule;

/**
 * 本帧提交预算（原理参照：预算逐任务扣减，耗尽即停）。
 * <p>由 {@link LuminFrameBudget#remainingDurationNanos()} 与上传时长预算构造；
 * 每次接受一个任务即扣减其估计耗时与估计上传字节；任一预算耗尽则 {@link #hasBudgetRemaining()} 为 false。</p>
 */
public final class LuminSubmissionBudget {

    private long durationRemaining;
    private long uploadDurationRemaining;
    private long uploadBytesRemaining;
    private final boolean unlimitedUpload;

    /**
     * @param durationBudgetNanos        本帧可提交的估计耗时预算
     * @param uploadDurationBudgetNanos  本帧可提交的估计上传耗时预算
     * @param uploadBytesBudget          本帧可提交的估计上传字节预算
     * @param unlimitedUpload            true = 忽略上传双重约束（本帧阻塞档）
     */
    public LuminSubmissionBudget(long durationBudgetNanos, long uploadDurationBudgetNanos,
                                 long uploadBytesBudget, boolean unlimitedUpload) {
        if (durationBudgetNanos < 0 || uploadDurationBudgetNanos < 0 || uploadBytesBudget < 0) {
            throw new IllegalArgumentException("budgets must be >= 0");
        }
        this.durationRemaining = durationBudgetNanos;
        this.uploadDurationRemaining = uploadDurationBudgetNanos;
        this.uploadBytesRemaining = uploadBytesBudget;
        this.unlimitedUpload = unlimitedUpload;
    }

    public boolean hasBudgetRemaining() {
        if (durationRemaining <= 0) {
            return false;
        }
        if (unlimitedUpload) {
            return true;
        }
        return uploadDurationRemaining > 0 && uploadBytesRemaining > 0;
    }

    /**
     * 扣减一次提交的估计成本。
     *
     * @return true = 接受（预算已扣减）；false = 拒绝（预算不足，未扣减）
     */
    public boolean tryConsume(long estimatedDurationNanos, long estimatedUploadDurationNanos,
                              long estimatedUploadBytes) {
        if (estimatedDurationNanos < 0 || estimatedUploadDurationNanos < 0 || estimatedUploadBytes < 0) {
            throw new IllegalArgumentException("estimates must be >= 0");
        }
        if (estimatedDurationNanos > durationRemaining) {
            return false;
        }
        if (!unlimitedUpload) {
            if (estimatedUploadDurationNanos > uploadDurationRemaining
                    || estimatedUploadBytes > uploadBytesRemaining) {
                return false;
            }
        }
        durationRemaining -= estimatedDurationNanos;
        if (!unlimitedUpload) {
            uploadDurationRemaining -= estimatedUploadDurationNanos;
            uploadBytesRemaining -= estimatedUploadBytes;
        }
        return true;
    }

    public long durationRemainingNanos() {
        return durationRemaining;
    }

    public long uploadDurationRemainingNanos() {
        return uploadDurationRemaining;
    }

    public long uploadBytesRemaining() {
        return uploadBytesRemaining;
    }
}
