package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.platform.GpuTexture;
import com.mojang.blaze3d.platform.GpuTextureView;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * NeoForge适配层：模拟Fabric的CommandEncoder API
 */
public class CommandEncoder {

    public RenderPass beginRenderPass() {
        return new RenderPass();
    }

    public void endRenderPass() {
        // NeoForge使用不同的API
    }

    /** FXAAShader: encoder.createRenderPass(supplier, colorView, OptionalInt.empty()) */
    public RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorView, OptionalInt clearColor) {
        return new RenderPass();
    }

    /** GlslSandBox / TtfTextRenderer: encoder.createRenderPass(supplier, colorView, OptionalInt, depthView, OptionalDouble) */
    public RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorView, OptionalInt clearColor,
                                       GpuTextureView depthView, OptionalDouble clearDepth) {
        return new RenderPass();
    }

    public void copyBufferToBuffer(Object src, long srcOffset, Object dst, long dstOffset, long size) {
        // NeoForge使用不同的API
    }

    /** LuminRingBuffer: mapBuffer(buffers.GpuBuffer, boolean, boolean) */
    public com.mojang.blaze3d.buffers.GpuBuffer.MappedView mapBuffer(
            com.mojang.blaze3d.buffers.GpuBuffer buffer, boolean read, boolean write) {
        // NeoForge使用不同的API
        return new com.mojang.blaze3d.buffers.GpuBuffer.MappedView();
    }

    /** LuminRingBuffer: copyToBuffer(src, dst) */
    public void copyToBuffer(com.mojang.blaze3d.buffers.GpuBuffer src, com.mojang.blaze3d.buffers.GpuBuffer dst) {
        // NeoForge使用不同的API
    }

    /** 旧的2参数重载，保留兼容性 */
    public void copyTextureToTexture(Object src, Object dst) {
        // NeoForge使用不同的API
    }

    /** FXAAShader: encoder.copyTextureToTexture(src, dst, srcMip, srcX, srcY, dstX, dstY, width, height) */
    public void copyTextureToTexture(Object src, Object dst, int srcMip, int srcX, int srcY,
                                     int dstX, int dstY, int width, int height) {
        // NeoForge使用不同的API
    }

    /** TtfGlyphAtlas: writeToTexture(texture, buffer, format, mip, dstLayer, dstX, dstY, width, height) */
    public void writeToTexture(GpuTexture texture, java.nio.ByteBuffer data,
                               com.mojang.blaze3d.platform.NativeImage.Format format,
                               int mipLevel, int dstLayer, int dstX, int dstY, int width, int height) {
        // NeoForge使用不同的API
    }

    /** SystemEmojiAtlas / TextureRenderer: writeToTexture(texture, image) */
    public void writeToTexture(GpuTexture texture, NativeImage image) {
        // NeoForge使用不同的API
    }
}
