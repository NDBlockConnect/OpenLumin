package io.github.openlumin.rhi.gl;

import io.github.openlumin.rhi.LuminTexture;
import io.github.openlumin.rhi.LuminTextureView;

/** LuminTextureView → 包装 LuminTextureGL */
public final class LuminTextureViewGL implements LuminTextureView {
    public final LuminTextureGL tex;
    public LuminTextureViewGL(LuminTextureGL tex) { this.tex = tex; }
    @Override public LuminTexture texture() { return tex; }

    /**
     * 桥接为 MC 26.2 GpuTextureView（用于 RenderPass.bindTexture / ColorTarget）。
     * 26.2 GpuTexture 自身无 getColorTextureView()（仅 RenderTarget 有）。
     * Alpha 2.1 占位：返回 null 桥接；B5 完整实现 surface 后用 RenderTarget 包装。
     * 调用方在 bindTexture 前需自行包装（见 LuminRHI_GL.Swapchain 桥接 + RenderTarget 路径）。
     */
    public com.mojang.blaze3d.textures.GpuTextureView toMc() {
        return null;  // B5: return target.getColorTextureView()（surface 桥接后填充）
    }
}
