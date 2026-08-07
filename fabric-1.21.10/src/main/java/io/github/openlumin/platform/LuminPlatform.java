package io.github.openlumin.platform;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * LuminShot Platform 核心接口
 *
 * 封装 Minecraft 渲染 API 的平台差异，提供统一的抽象层。
 * fabric-1.21.10 使用现代 GPU API，其他版本可能使用 OpenGL 或 Vulkan。
 */
public interface LuminPlatform {

    /**
     * 获取 GPU 设备（现代 API）或等效抽象
     */
    GpuDevice getDevice();

    /**
     * 获取动态 Uniform 管理器
     */
    DynamicUniforms getDynamicUniforms();

    /**
     * 创建渲染通道
     * @param colorTarget 颜色附件
     * @param depthTarget 深度附件（可选）
     */
    RenderPass createRenderPass(GpuTextureView colorTarget, GpuTextureView depthTarget);

    /**
     * 获取顺序索引缓冲区（用于 quad/triangle 绘制）
     * @param mode 顶点模式（QUADS, TRIANGLES 等）
     * @param indexCount 索引数量
     * @return 索引缓冲区
     */
    com.mojang.blaze3d.buffers.GpuBuffer getSequentialBuffer(VertexFormat.Mode mode, int indexCount);

    /**
     * 获取当前模型视图矩阵
     */
    org.joml.Matrix4f getModelViewMatrix();

    /**
     * 写入动态变换 Uniform
     * @param modelView 模型视图矩阵
     * @param colorModulator 颜色调制器
     * @param modelOffset 模型偏移
     * @param textureMatrix 纹理矩阵
     * @param lineWidth 线宽
     * @return Uniform 缓冲区切片
     */
    GpuBufferSlice writeTransform(Matrix4fc modelView, org.joml.Vector4fc colorModulator,
                                   Vector3fc modelOffset, Matrix4fc textureMatrix,
                                   float lineWidth);

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
     * 帧开始回调
     */
    void beginRenderFrame();

    /**
     * 帧结束回调
     */
    void endRenderFrame();
}
