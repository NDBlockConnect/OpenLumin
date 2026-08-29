package io.github.openlumin.rhi;

/**
 * 交换链与表面
 *
 * 抽象后端的"图像获取 → 渲染 → 显示"循环。
 * Alpha 2 范围内：LuminRHI_GL 适配 MC 26.x 的 RenderTarget / Minecraft 游戏主目标；
 * Alpha 5 范围内：LuminRHI_DX12 / LuminRHI_Metal 独立 surface 抽象。
 */
public interface LuminSwapchain {
    int width();
    int height();
    LuminFormat format();

    /**
     * 获取当前帧的图像（后端内部处理 vsync 等待/present 同步）
     */
    LuminSwapchainImage acquireNextImage();

    /**
     * 调整交换链大小（窗口尺寸变更）
     */
    void resize(int width, int height);
}
