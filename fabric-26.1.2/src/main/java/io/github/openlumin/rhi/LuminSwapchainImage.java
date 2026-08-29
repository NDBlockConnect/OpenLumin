package io.github.openlumin.rhi;

/**
 * 交换链图像——单帧后端图像（颜色 attachment）
 *
 * 继承 LuminTextureView（可作为 LuminRenderPassDesc 的颜色附件）。
 * present() 提交此图像到屏幕呈现（vsync=true 时按显示刷新率同步）。
 */
public interface LuminSwapchainImage extends LuminTextureView {
    /**
     * 提交此图像到屏幕呈现
     */
    void present(boolean vsync);
}
