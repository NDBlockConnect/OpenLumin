package com.mojang.blaze3d.platform;

/**
 * fabric-1.21.10 shim：TextureFormat 已移至 com.mojang.blaze3d.textures.TextureFormat。
 */
public enum TextureFormat {
    RGBA8,
    DEPTH32;

    public com.mojang.blaze3d.textures.TextureFormat toNative() {
        return switch (this) {
            case RGBA8 -> com.mojang.blaze3d.textures.TextureFormat.RGBA8;
            case DEPTH32 -> com.mojang.blaze3d.textures.TextureFormat.DEPTH32;
        };
    }
}
