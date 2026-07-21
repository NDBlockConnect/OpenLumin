package com.mojang.blaze3d.platform;

import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge适配层：模拟Fabric的GpuTexture API
 */
public class GpuTexture {

    public static final int USAGE_TEXTURE_BINDING = 0x01;
    public static final int USAGE_RENDER_ATTACHMENT = 0x02;
    public static final int USAGE_COPY_DST = 0x04;
    public static final int USAGE_COPY_SRC = 0x08;

    private final int width;
    private final int height;
    private final int format;
    private final int id;

    public GpuTexture(int width, int height, int format) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.id = 0; // 占位
    }

    public GpuTexture(int width, int height) {
        this(width, height, 0);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int format() {
        return format;
    }

    public int getId() {
        return id;
    }

    public GpuTextureView createView() {
        return new GpuTextureView(this);
    }

    public void upload(int level, int xOffset, int yOffset, int width, int height, byte[] data) {
        // NeoForge使用不同的API
    }

    public void close() {
        // NeoForge使用不同的API
    }
}
