package com.mojang.blaze3d.platform;

/**
 * NeoForge适配层：模拟Fabric的CommandEncoder API
 * 继承 systems.CommandEncoder 以解决 GpuDevice.createCommandEncoder() 的返回类型兼容问题。
 */
public class CommandEncoder extends com.mojang.blaze3d.systems.CommandEncoder {

    public com.mojang.blaze3d.buffers.GpuBuffer.MappedView mapBuffer(
            com.mojang.blaze3d.buffers.GpuBuffer buffer, boolean read, boolean write) {
        // NeoForge使用不同的API
        return new com.mojang.blaze3d.buffers.GpuBuffer.MappedView();
    }

    public void clearColorTexture(GpuTexture texture, int mipLevel) {
        // NeoForge使用不同的API
    }

    public void clearColorAndDepthTextures(GpuTexture colorTexture, int colorMipLevel, GpuTexture depthTexture, double depthValue) {
        // NeoForge使用不同的API
    }
}
