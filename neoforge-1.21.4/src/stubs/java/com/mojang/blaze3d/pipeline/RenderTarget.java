package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.platform.GpuTexture;
import com.mojang.blaze3d.platform.GpuTextureView;

/**
 * NeoForge适配层：模拟Fabric的RenderTarget API
 */
public class RenderTarget {

    public int width;
    public int height;
    public boolean useDepth;
    public int frameBufferId; // OpenGL FBO ID

    public RenderTarget(boolean useDepth) {
        this.useDepth = useDepth;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        // NeoForge使用不同的API
    }

    public GpuTexture getColorTexture() {
        // NeoForge使用不同的API
        return new GpuTexture(1920, 1080, 0);
    }

    public GpuTextureView getColorTextureView() {
        // NeoForge使用不同的API
        return new GpuTextureView();
    }

    public GpuTextureView getDepthTextureView() {
        // NeoForge使用不同的API
        return new GpuTextureView();
    }
}
