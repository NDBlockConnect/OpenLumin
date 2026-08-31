package io.github.openlumin.chunk;

import java.util.ArrayDeque;

/**
 * 忙碌度时间账本：按插入序（= 完成时间序）维护任务执行区间，
 * 查询时对滑动窗口内的忙碌时间求重叠并裁剪过期区间。
 * <p>时间基为 {@code System.nanoTime()}；查询为纯时间戳函数，可用合成时间单测。</p>
 */
final class BusyTracker {

    private final ArrayDeque<long[]> intervals = new ArrayDeque<>();

    public void record(long startNanos, long endNanos) {
        if (endNanos <= startNanos) {
            return;
        }
        synchronized (this) {
            intervals.addLast(new long[]{startNanos, endNanos});
        }
    }

    /**
     * @param nowNanos     查询时刻
     * @param windowNanos  滑动窗口长度（如一帧时长）
     * @param threadBudget 忙碌度预算的线程数分母（总预算 = windowNanos × threadBudget）
     * @return 窗口内忙碌线程时间 / 总预算，恒在 [0, 1]
     */
    public float fraction(long nowNanos, long windowNanos, int threadBudget) {
        if (windowNanos <= 0 || threadBudget <= 0) {
            throw new IllegalArgumentException("windowNanos>0 and threadBudget>0 required");
        }
        long windowStart = nowNanos - windowNanos;
        long busy;
        synchronized (this) {
            long[] oldest;
            while ((oldest = intervals.peekFirst()) != null && oldest[1] <= windowStart) {
                intervals.removeFirst();
            }
            busy = 0;
            for (long[] interval : intervals) {
                long start = Math.max(interval[0], windowStart);
                long end = Math.min(interval[1], nowNanos);
                if (end > start) {
                    busy += end - start;
                }
            }
        }
        double budget = (double) windowNanos * threadBudget;
        double value = busy / budget;
        return (float) Math.min(1.0, Math.max(0.0, value));
    }
}
