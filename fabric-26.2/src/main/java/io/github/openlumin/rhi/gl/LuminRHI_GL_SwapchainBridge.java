package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.rhi.LuminFormat;
import net.minecraft.client.Minecraft;

/**
 * LuminRHI GL 后端：MC 26.2 Swapchain 桥接
 *
 * 26.2 用 {@code GpuSurface} 替代了 26.1.2 的 {@code Minecraft.getMainRenderTarget()}：
 *   - 业务层 LuminSwapchainImage 不直接引用 MC 主目标
 *   - 而是通过 {@link #blitToWindow} 将任意 {@code com.mojang.blaze3d.textures.GpuTextureView} blit 到 MC 主表面
 *   - 走 MC {@code GpuSurface.blitFromTexture(cmdEncoder, view)} 模式
 *
 * 26.2 client jar 中无 {@code LightweightRenderTarget}；RenderTarget 是 abstract 基类。
 * B5 仅提供 surface bridge + 窗口尺寸；RenderTarget 子类化由 LuminRenderTarget（独立类，B5.1）提供。
 */
public final class LuminRHI_GL_SwapchainBridge {

    private LuminRHI_GL_SwapchainBridge() {}

    /**
     * MC 26.2 主窗口的实际尺寸（用于 Swapchain.width/height）。
     */
    public static int windowWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    public static int windowHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    /**
     * 默认 surface 格式（MC 26.2 主窗口 = RGBA8_UNORM via GpuSurface）。
     */
    public static LuminFormat windowFormat() {
        return LuminFormat.R8G8B8A8_UNORM;
    }

    /**
     * 业务层把 LuminRHI 渲染目标 blit 到 MC 主表面（在 RenderPass 后调用）。
     * 等价于 26.1.2 的 {@code RenderTarget.blitToScreen()}。
     *
     * @param sourceView  业务层 GpuTextureView（来自 LuminTextureViewGL.toMc()）
     */
    public static void blitToWindow(com.mojang.blaze3d.textures.GpuTextureView sourceView) {
        GpuSurface surface = Minecraft.getInstance().windowSurface();
        com.mojang.blaze3d.systems.CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        try {
            surface.blitFromTexture(enc, sourceView);
            enc.submit();
        } finally {
            // 26.2 client jar CommandEncoder 不实现 AutoCloseable
        }
    }
}
