package io.github.openlumin.rhi;

import java.io.Closeable;

/** 提交命令缓冲 */
public interface LuminCommandBuffer extends Closeable {
    int drawCallCount();
    @Override void close();
}
