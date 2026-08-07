package io.github.openlumin;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * fabric-26.2 适配：继承 AbstractTexture 并直接赋值 protected 字段；
 * 采样器来自 SamplerCache 共享缓存，close() 不释放它。
 */
public class LuminTexture extends AbstractTexture {

    private final boolean closeTexture;

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, boolean closeTexture) {
        this.texture = texture;
        this.textureView = textureView;
        this.closeTexture = closeTexture;
        this.sampler = RenderSystem.getSamplerCache()
                .getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.LINEAR, false);
    }

    public LuminTexture(GpuTexture texture, GpuTextureView textureView) {
        this(texture, textureView, true);
    }

    @Override
    public void close() {
        if (closeTexture) {
            super.close();
        }
    }
}
