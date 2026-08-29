package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.textures.GpuTexture;
import io.github.openlumin.rhi.LuminFormat;
import io.github.openlumin.rhi.LuminTexture;
import io.github.openlumin.rhi.LuminTextureView;

/** LuminTexture → 包装 GpuTexture + GpuTextureView */
public final class LuminTextureGL implements LuminTexture {
    public final GpuTexture gtex;
    private final int w, h, d;
    private final LuminFormat format;
    public LuminTextureGL(GpuTexture gtex, int w, int h, int d, LuminFormat format) {
        this.gtex = gtex; this.w = w; this.h = h; this.d = d; this.format = format;
    }
    @Override public int width() { return w; }
    @Override public int height() { return h; }
    @Override public int depth() { return d; }
    @Override public LuminFormat format() { return format; }
    @Override public LuminTextureView view() { return new LuminTextureViewGL(this); }
    @Override public void close() { gtex.close(); }
}
