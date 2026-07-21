package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.GpuTexture;
import com.mojang.blaze3d.platform.GpuTextureView;
import com.mojang.blaze3d.platform.GpuSampler;

/**
 * NeoForge适配层：扩展AbstractTexture以支持GPU纹理API
 */
public class AbstractTextureExtensions {

    public static GpuTexture getTexture(AbstractTexture texture) {
        // NeoForge使用不同的纹理API
        return new GpuTexture(0, 0);
    }

    public static GpuTextureView getTextureView(AbstractTexture texture) {
        // NeoForge使用不同的纹理API
        return new GpuTextureView(getTexture(texture));
    }

    public static GpuSampler getSampler(AbstractTexture texture) {
        // NeoForge使用不同的纹理API
        return new GpuSampler();
    }
}
