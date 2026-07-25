package io.github.openlumin;

import com.mojang.blaze3d.platform.GpuSampler;
import com.mojang.blaze3d.platform.GpuTexture;
import com.mojang.blaze3d.platform.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class LuminTexture extends AbstractTexture {

    private final GpuTexture texture;
    private final GpuTextureView textureView;
    private final GpuSampler sampler;
    private final boolean closeTexture;
    private final boolean closeSampler;

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, GpuSampler sampler, boolean closeTexture, boolean closeSampler) {
        this.texture = texture;
        this.textureView = textureView;
        this.sampler = sampler;
        this.closeTexture = closeTexture;
        this.closeSampler = closeSampler;
    }

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, GpuSampler sampler) {
        this(texture, textureView, sampler, true, true);
    }

    public GpuTexture getTexture() {
        return texture;
    }

    public GpuTextureView getTextureView() {
        return textureView;
    }

    public GpuSampler getSampler() {
        return sampler;
    }

    @Override
    public void close() {
        if (closeSampler) {
            sampler.close();
        }
        if (closeTexture) {
            textureView.close();
            texture.close();
        }
    }

}
