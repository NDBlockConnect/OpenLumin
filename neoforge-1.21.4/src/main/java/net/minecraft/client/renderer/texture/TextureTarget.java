package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * NeoForge适配层：TextureTarget适配
 */
public class TextureTarget extends RenderTarget {

    public TextureTarget(int width, int height, boolean useDepth) {
        super(useDepth);
        this.width = width;
        this.height = height;
        // NeoForge使用不同的RenderTarget API
        // 这是一个桩实现，仅用于编译通过
    }
}
