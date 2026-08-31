package io.github.openlumin.chunk;

/**
 * 区块构建任务：Worker 线程上执行的纯 CPU 网格生成单元。
 * <p>任务必须只依赖传入的 {@code context} 与外部不可变输入，不做任何 GPU 调用；
 * 返回 {@code null} 视为无产出（结果仍为 COMPLETED，output 为 null）。</p>
 *
 * @param <O> 产出类型
 */
@FunctionalInterface
public interface LuminChunkTask<O extends LuminBuildOutput> {

    O build(LuminBuildContext context, CancellationToken cancel);
}
