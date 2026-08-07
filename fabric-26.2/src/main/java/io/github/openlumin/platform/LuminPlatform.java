package io.github.openlumin.platform;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * LuminShot Platform 核心接口（26.2 Vulkan 基底）
 *
 * 与 26.1 基线的差异：
 * - 顶点图元用 PrimitiveTopology 替代 VertexFormat.Mode
 * - 顶点缓冲绑定为 slice（setVertexBuffer(slot, slice)）
 * - 纹理格式用 GpuFormat，映射/清屏参数变化
 */
public interface LuminPlatform {

    GpuDevice getDevice();

    DynamicUniforms getDynamicUniforms();

    /**
     * 创建渲染通道
     * @param colorTarget 颜色附件
     * @param depthTarget 深度附件（可为 null）
     */
    RenderPass createRenderPass(GpuTextureView colorTarget, GpuTextureView depthTarget);

    /**
     * 获取顺序索引缓冲区
     */
    GpuBuffer getSequentialBuffer(PrimitiveTopology topology, int indexCount);

    Matrix4f getModelViewMatrix();

    GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator,
                                   Vector3fc modelOffset, Matrix4fc textureMatrix);

    void bindDefaultUniforms(RenderPass pass);

    GpuTextureView resolveColorView();

    GpuTextureView resolveDepthView();

    GpuSampler resolveSampler();

    void beginRenderFrame();

    void endRenderFrame();
}
