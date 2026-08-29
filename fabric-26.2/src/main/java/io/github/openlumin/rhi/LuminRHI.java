package io.github.openlumin.rhi;

/**
 * OpenLumin 渲染硬件抽象（RHI）顶级接口
 *
 * Alpha 2 设计：LuminRHI 是业务层与硬件后端之间的契约，
 * 业务层（render2d/render3d/immediate/shaders）只与本接口交互。
 *
 * 当前 LuminPlatform 是本接口的临时形态（直接委托 MC GpuDevice），
 * Alpha 2 将拆分为 LuminRHI 接口 + 多个后端实现（GL/GLES/Vulkan/DX12/Metal）。
 */
public interface LuminRHI {

    /**
     * 获取后端信息（名称/版本/能力）
     */
    LuminRHIInfo info();

    /**
     * 获取设备
     */
    LuminDevice device();

    /**
     * 获取交换链
     */
    LuminSwapchain swapchain();

    /**
     * 创建命令录制器
     */
    LuminCommandEncoder createEncoder(String label);

    /**
     * 提交命令缓冲到设备队列
     */
    void submit(LuminCommandBuffer buffer);

    /**
     * 显示交换链图像
     */
    void present(LuminSwapchainImage image, boolean vsync);
}
