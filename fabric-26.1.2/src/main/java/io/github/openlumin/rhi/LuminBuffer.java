package io.github.openlumin.rhi;

import java.io.Closeable;

/** 缓冲（可关闭资源） */
public interface LuminBuffer extends Closeable {
    long size();
    LuminBufferView view(long offset, long length);
    @Override void close();
}
