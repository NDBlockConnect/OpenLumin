package io.github.openlumin.platform;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.OptionalDouble;
import java.util.Optional;

/**
 * Fabric 26.2 平台实现，委托到 Minecraft 26.2 GPU API。
 */
public class Fabric2622Platform implements LuminPlatform {

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
                Optional.empty(),
                depthTarget,
                OptionalDouble.empty()
        );
    }

    @Override
    public GpuBuffer getSequentialBuffer(PrimitiveTopology topology, int indexCount) {
        return RenderSystem.getSequentialBuffer(topology).getBuffer(indexCount);
    }

    @Override
    public Matrix4f getModelViewMatrix() {
        return RenderSystem.getModelViewMatrixCopy();
    }

    @Override
    public GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator,
                                         Vector3fc modelOffset, Matrix4fc textureMatrix) {
        return RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), new Vector4f(colorModulator),
                                                                 new org.joml.Vector3f(modelOffset), new Matrix4f(textureMatrix));
    }

    @Override
    public void bindDefaultUniforms(RenderPass pass) {
        RenderSystem.bindDefaultUniforms(pass);
    }

    @Override
    public GpuTextureView resolveColorView() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
    }

    @Override
    public GpuTextureView resolveDepthView() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
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
