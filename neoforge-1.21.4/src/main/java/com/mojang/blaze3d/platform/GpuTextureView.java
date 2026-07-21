package com.mojang.blaze3d.platform;

/**
 * NeoForge适配层：模拟Fabric的GpuTextureView API
 */
public class GpuTextureView {

    private final GpuTexture texture;

    public GpuTextureView(GpuTexture texture) {
        this.texture = texture;
    }

    public GpuTextureView() {
        this(null);
    }

    public GpuTexture texture() {
        return texture;
    }

    public void close() {
        // NeoForge使用不同的API
    }
}
