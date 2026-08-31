package io.github.openlumin.chunk;

/**
 * 构建上下文：Worker 线程本地资源的访问入口。
 * <p>M1 为占位（纯 CPU 阶段无共享可变状态）；M2 将挂载 scratch 缓冲 /
 * arena 分配器等线程本地设施，避免任务间竞争。</p>
 */
public final class LuminBuildContext {

    LuminBuildContext() {
    }
}
