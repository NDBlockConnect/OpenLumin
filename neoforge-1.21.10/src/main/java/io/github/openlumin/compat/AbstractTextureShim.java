package io.github.openlumin.compat;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * 1.21.10 兼容性接口：AbstractTexture 扩展方法的统一抽象。
 * 避免在 net.minecraft.* 包中创建类导致模块冲突。
 */
public interface AbstractTextureShim {

    default GpuTextureView getColorTextureView() {
        return null;
    }
}
