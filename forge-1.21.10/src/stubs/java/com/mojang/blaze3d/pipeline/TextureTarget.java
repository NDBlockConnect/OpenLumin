package com.mojang.blaze3d.pipeline;

/**
 * NeoForge适配层：模拟Fabric的TextureTarget（带标签名称的RenderTarget）
 */
public class TextureTarget extends RenderTarget {

    public TextureTarget(String name, int width, int height, boolean useDepth) {
        super(useDepth);
        this.width = width;
        this.height = height;
    }
}
