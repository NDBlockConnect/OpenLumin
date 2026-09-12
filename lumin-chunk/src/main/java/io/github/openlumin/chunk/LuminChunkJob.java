package io.github.openlumin.chunk;

import java.util.concurrent.TimeUnit;

/**
 * 已提交任务的句柄：取消、完成状态与结果获取。
 *
 * @param <O> 产出类型
 */
public interface LuminChunkJob<O extends LuminBuildOutput> {

    /** 未提供工作量提示时的哨兵值。 */
    long EFFORT_UNKNOWN = -1L;

    /** 请求取消。已在运行的任务经令牌协作中断；已完成的任务不受影响。 */
    void cancel();

    boolean isCancelled();

    boolean isDone();

    /** 提交时提供的工作量提示；{@link #EFFORT_UNKNOWN} 表示未提供。 */
    long effortHint();

    /** 提交时按估计器预算的单任务耗时（纳秒）。 */
    long estimatedDurationNanos();

    /**
     * 阻塞等待完成。
     *
     * @return 结果；超时返回 {@code null}
     */
    LuminChunkJobResult<O> await(long timeout, TimeUnit unit) throws InterruptedException;
}
