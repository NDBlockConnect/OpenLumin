package io.github.openlumin.rhi;

import java.io.Closeable;

/** 纹理（可关闭资源） */
public interface LuminTexture extends Closeable {
    int width();
    int height();
    int depth();
    LuminFormat format();
    LuminTextureView view();
    @Override void close();
}
