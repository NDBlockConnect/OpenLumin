package com.mojang.blaze3d.platform;

/**
 * fabric-1.21.10 shim：FilterMode 已移至 com.mojang.blaze3d.textures.FilterMode。
 */
public enum FilterMode {
    NEAREST,
    LINEAR;

    public com.mojang.blaze3d.textures.FilterMode toNative() {
        return switch (this) {
            case NEAREST -> com.mojang.blaze3d.textures.FilterMode.NEAREST;
            case LINEAR -> com.mojang.blaze3d.textures.FilterMode.LINEAR;
        };
    }
}
