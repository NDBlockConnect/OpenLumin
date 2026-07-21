package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderPass;

/**
 * NeoForge适配层：模拟Fabric的CommandEncoder API
 */
public class CommandEncoder {

    public GpuBuffer.MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write) {
        // NeoForge使用不同的API
        return new GpuBuffer.MappedView();
    }

    public RenderPass createRenderPass(Object... params) {
        // NeoForge使用不同的API
        return new RenderPass();
    }

    public void clearColorTexture(GpuTexture texture, int mipLevel) {
        // NeoForge使用不同的API
    }

    public void clearColorAndDepthTextures(GpuTexture colorTexture, int colorMipLevel, GpuTexture depthTexture, double depthValue) {
        // NeoForge使用不同的API
    }
}
