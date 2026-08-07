package io.github.openlumin;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * fabric-26.1.2 适配：26.1.2 恢复了独立 GpuSampler，
 * LuminTexture 在父类默认采样器之外提供与 1.21.10 行为一致的重复采样配置。
 * 注意：采样器来自 SamplerCache 共享缓存，close() 不释放它（与 MC AbstractTexture 行为一致）。
 */
public class LuminTexture extends AbstractTexture {

    private final boolean closeTexture;
    private final GpuSampler luminSampler;

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, boolean closeTexture) {
        this.texture = texture;
        this.textureView = textureView;
        this.closeTexture = closeTexture;
        this.luminSampler = RenderSystem.getSamplerCache()
                .getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.LINEAR, false);
    }

    public LuminTexture(GpuTexture texture, GpuTextureView textureView) {
        this(texture, textureView, true);
    }

    @Override
    public GpuSampler getSampler() {
        return luminSampler;
    }

    @Override
    public void close() {
        if (closeTexture) {
            super.close();
        }
    }
}
