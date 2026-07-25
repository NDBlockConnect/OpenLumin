package io.github.openlumin;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * fabric-1.21.10 override：移除了 GpuSampler（1.21.10 中已不存在），
 * 纹理类型改为 com.mojang.blaze3d.textures.*。
 */
public class LuminTexture extends AbstractTexture {

    private final boolean closeTexture;
    private final GpuTexture texture;
    private final GpuTextureView textureView;

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, boolean closeTexture) {
        this.texture = texture;
        this.textureView = textureView;
        this.closeTexture = closeTexture;
    }

    public LuminTexture(GpuTexture texture, GpuTextureView textureView) {
        this(texture, textureView, true);
    }

    public GpuTexture getTexture() {
        return texture;
    }

    public GpuTextureView getTextureView() {
        return textureView;
    }

    @Override
    public void close() {
        if (closeTexture) {
            textureView.close();
            texture.close();
        }
    }
}
