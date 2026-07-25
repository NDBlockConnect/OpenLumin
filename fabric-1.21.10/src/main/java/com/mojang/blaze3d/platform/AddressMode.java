package com.mojang.blaze3d.platform;

/**
 * fabric-1.21.10 shim：AddressMode 已移至 com.mojang.blaze3d.textures.AddressMode。
 * 提供与原 platform 包相同的常量，并可转换到原生 textures 包类型。
 */
public enum AddressMode {
    CLAMP_TO_EDGE,
    REPEAT,
    MIRRORED_REPEAT;

    public com.mojang.blaze3d.textures.AddressMode toNative() {
        return switch (this) {
            case CLAMP_TO_EDGE -> com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE;
            case REPEAT -> com.mojang.blaze3d.textures.AddressMode.REPEAT;
            // MIRRORED_REPEAT 在 textures.AddressMode 中不存在（1.21.10 删除），回退到 REPEAT
            case MIRRORED_REPEAT -> com.mojang.blaze3d.textures.AddressMode.REPEAT;
        };
    }
}
