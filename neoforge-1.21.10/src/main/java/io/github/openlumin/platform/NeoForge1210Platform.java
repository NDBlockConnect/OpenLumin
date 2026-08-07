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
 * NeoForge 1.21.10 平台实现
 *
 * 直接委托到 Minecraft 1.21.10 现代 GPU API。
 */
public class NeoForge1210Platform implements LuminPlatform {

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
        throw new UnsupportedOperationException("NeoForge 1.21.10 platform does not support explicit render pass creation");
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
    }

    @Override
    public void endRenderFrame() {
    }
}
