/**
 * 1.21.10 兼容性桩：FilterMode 在 1.21.10 中已移除或不同。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
package io.github.openlumin.compat;

/**
 * fabric-1.21.10 shim：FilterMode 已移至 com.mojang.blaze3d.textures.FilterMode。
 */
public enum FilterModeShim {
    NEAREST,
    LINEAR;

    public com.mojang.blaze3d.textures.FilterMode toNative() {
        return switch (this) {
            case NEAREST -> com.mojang.blaze3d.textures.FilterMode.NEAREST;
            case LINEAR -> com.mojang.blaze3d.textures.FilterMode.LINEAR;
        };
    }
}
