package io.github.openlumin.platform;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * Fabric 1.21.10 平台实现
 *
 * 直接委托到 Minecraft 1.21.10 的现代 GPU API。
 * 此版本使用原生的 RenderSystem、GpuDevice、DynamicUniforms。
 */
public class Fabric1210Platform implements LuminPlatform {

    @Override
    public GpuDevice getDevice() {
        return RenderSystem.getDevice();
    }

    @Override
    public DynamicUniforms getDynamicUniforms() {
        return RenderSystem.getDynamicUniforms();
    }

    @Override
    public RenderPass createRenderPass(GpuTextureView colorTarget, GpuTextureView depthTarget) {
        // 1.21.10: RenderPass 通过 CommandEncoder 创建
        // 此方法暂不实现，业务代码直接调用 RenderSystem API
        throw new UnsupportedOperationException("Use RenderSystem.getDevice().createCommandEncoder().createRenderPass() directly");
    }

    @Override
    public com.mojang.blaze3d.buffers.GpuBuffer getSequentialBuffer(VertexFormat.Mode mode, int indexCount) {
        return RenderSystem.getSequentialBuffer(mode).getBuffer(indexCount);
    }

    @Override
    public org.joml.Matrix4f getModelViewMatrix() {
        return RenderSystem.getModelViewMatrix();
    }

    @Override
    public GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator,
                                         Vector3fc modelOffset, Matrix4fc textureMatrix,
                                         float lineWidth) {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, colorModulator,
                                                                 modelOffset, textureMatrix, lineWidth);
    }

    @Override
    public void bindDefaultUniforms(RenderPass pass) {
        RenderSystem.bindDefaultUniforms(pass);
    }

    @Override
    public GpuTextureView resolveColorView() {
        return Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
    }

    @Override
    public GpuTextureView resolveDepthView() {
        return Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
    }

    @Override
    public void beginRenderFrame() {
        // 帧开始逻辑（如果需要）
    }

    @Override
    public void endRenderFrame() {
        // 帧结束逻辑（如果需要）
    }
}
