package io.github.openlumin.chunk.schedule;

/**
 * 任务成本训练样本：{@code effort}（估计工作量，如网格字节数）→ {@code durationNanos}（实测耗时）。
 * <p>估计器以在线回归维持 {@code duration ≈ slope × effort + intercept}。</p>
 */
public record LuminJobEffort(long effort, long durationNanos) {

    public LuminJobEffort {
        if (effort < 0) {
            throw new IllegalArgumentException("effort must be >= 0, got " + effort);
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must be >= 0, got " + durationNanos);
        }
    }
}
