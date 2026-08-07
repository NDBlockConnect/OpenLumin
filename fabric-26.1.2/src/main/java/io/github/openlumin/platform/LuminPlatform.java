package io.github.openlumin.platform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * LuminShot Platform 核心接口（26.1 基线）
 *
 * 封装 Minecraft 26.1.2 渲染 API 的平台差异，提供统一的抽象层。
 * 与 1.21.10 基线的差异：
 * - writeTransform 不再携带 lineWidth（26.1.2 的 DynamicUniforms.Transform 无该字段）
 * - 采样器为独立 GpuSampler 对象，经 resolveSampler() 提供
 * - createRenderPass 提供真实实现（CommandEncoder 四参重载）
 */
public interface LuminPlatform {

    /**
     * 获取 GPU 设备
     */
    GpuDevice getDevice();

    /**
     * 获取动态 Uniform 管理器
     */
    DynamicUniforms getDynamicUniforms();

    /**
     * 创建渲染通道
     * @param colorTarget 颜色附件
     * @param depthTarget 深度附件（可为 null）
     */
    RenderPass createRenderPass(GpuTextureView colorTarget, GpuTextureView depthTarget);

    /**
     * 获取顺序索引缓冲区（用于 quad/triangle 绘制）
     * @param mode 顶点模式（QUADS, TRIANGLES 等）
     * @param indexCount 索引数量
     * @return 索引缓冲区
     */
    GpuBuffer getSequentialBuffer(VertexFormat.Mode mode, int indexCount);

    /**
     * 获取当前模型视图矩阵
     */
    Matrix4f getModelViewMatrix();

    /**
     * 写入动态变换 Uniform
     * @param modelView 模型视图矩阵
     * @param colorModulator 颜色调制器
     * @param modelOffset 模型偏移
     * @param textureMatrix 纹理矩阵
     * @return Uniform 缓冲区切片
     */
    GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator,
                                   Vector3fc modelOffset, Matrix4fc textureMatrix);

    /**
     * 绑定默认 Uniform（投影矩阵等）到渲染通道
     */
    void bindDefaultUniforms(RenderPass pass);

    /**
     * 解析当前颜色附件视图
     */
    GpuTextureView resolveColorView();

    /**
     * 解析当前深度附件视图
     */
    GpuTextureView resolveDepthView();

    /**
     * 解析默认采样器（26.1.2：独立 GpuSampler 对象）
     */
    GpuSampler resolveSampler();

    /**
     * 帧开始回调
     */
    void beginRenderFrame();

    /**
     * 帧结束回调
     */
    void endRenderFrame();
}
