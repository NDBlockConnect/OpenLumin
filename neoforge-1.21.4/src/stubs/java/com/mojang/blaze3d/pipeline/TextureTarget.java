package com.mojang.blaze3d.pipeline;

/**
 * NeoForge适配层：模拟Fabric的TextureTarget（带标签名称的RenderTarget）。
 * 优先于 vanilla MC 的 TextureTarget（后者只有 3 参构造）生效，提供 4 参 (label,w,h,depth) 重载。
 */
public class TextureTarget extends RenderTarget {

    public TextureTarget(String name, int width, int height, boolean useDepth) {
        super(useDepth);
        this.width = width;
        this.height = height;
    }
}
