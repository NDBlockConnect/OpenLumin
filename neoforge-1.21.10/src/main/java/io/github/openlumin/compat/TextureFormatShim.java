/**
 * 1.21.10 兼容性桩：TextureFormat 在 1.21.10 中已移除或不同。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
package io.github.openlumin.compat;

/**
 * fabric-1.21.10 shim：TextureFormat 已移至 com.mojang.blaze3d.textures.TextureFormat。
 */
public enum TextureFormatShim {
    RGBA8,
    DEPTH32;

    public com.mojang.blaze3d.textures.TextureFormat toNative() {
        return switch (this) {
            case RGBA8 -> com.mojang.blaze3d.textures.TextureFormat.RGBA8;
            case DEPTH32 -> com.mojang.blaze3d.textures.TextureFormat.DEPTH32;
        };
    }
}
