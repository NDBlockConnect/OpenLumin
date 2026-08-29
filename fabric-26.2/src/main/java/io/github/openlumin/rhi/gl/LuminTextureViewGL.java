package io.github.openlumin.rhi.gl;

import io.github.openlumin.rhi.LuminTexture;
import io.github.openlumin.rhi.LuminTextureView;

/** LuminTextureView → 包装 LuminTextureGL */
public final class LuminTextureViewGL implements LuminTextureView {
    public final LuminTextureGL tex;
    public LuminTextureViewGL(LuminTextureGL tex) { this.tex = tex; }
    @Override public LuminTexture texture() { return tex; }
}
