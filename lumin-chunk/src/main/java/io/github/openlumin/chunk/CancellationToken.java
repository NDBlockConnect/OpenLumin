package io.github.openlumin.chunk;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 区块构建任务的协作式取消令牌。
 * <p>任务实现应在构建循环中周期性轮询 {@link #isCancelled()}，尽早让出；
 * 令牌只表达"请求取消"，真正的中断点由任务自行决定。</p>
 */
public interface CancellationToken {

    boolean isCancelled();

    CancellationToken CANCELLED = () -> true;

    static CancellationToken create() {
        return new Token();
    }

    /**
     * 可变的取消令牌：{@link LuminChunkBuilder} 每个任务持有一个，
     * {@code cancel()} 由持有者（如 {@link LuminChunkJob}）触发。
     */
    final class Token implements CancellationToken {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        /**
         * @return true 若本次调用完成了从未取消到取消的状态跃迁
         */
        public boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
