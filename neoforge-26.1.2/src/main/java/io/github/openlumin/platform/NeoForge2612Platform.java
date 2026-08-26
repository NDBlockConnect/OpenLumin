package io.github.openlumin.platform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * NeoForge 26.1.2 平台实现
 *
 * 直接委托到 Minecraft 26.1.2 现代 GPU API。
 */
public class NeoForge2612Platform implements LuminPlatform {

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
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "lumin render pass",
                colorTarget,
                OptionalInt.empty(),
                depthTarget,
                OptionalDouble.empty()
        );
    }

    @Override
    public GpuBuffer getSequentialBuffer(VertexFormat.Mode mode, int indexCount) {
        return RenderSystem.getSequentialBuffer(mode).getBuffer(indexCount);
    }

    @Override
    public Matrix4f getModelViewMatrix() {
        return RenderSystem.getModelViewMatrix();
    }

    @Override
    public GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator,
                                         Vector3fc modelOffset, Matrix4fc textureMatrix) {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, colorModulator,
                                                                 modelOffset, textureMatrix);
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
    public GpuSampler resolveSampler() {
        return RenderSystem.getSamplerCache()
                .getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.LINEAR, false);
    }

    @Override
    public void beginRenderFrame() {
    }

    @Override
    public void endRenderFrame() {
    }
}
