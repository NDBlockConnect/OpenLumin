package io.github.openlumin.shaderpack;

import java.util.Locale;

/**
 * 缓冲格式（在 OptiFine/Iris 的格式名与后端格式间做中立映射）。
 *
 * <p>引擎在编译/执行层把本枚举映射到具体后端格式（GL 内部格式 / Vulkan GpuFormat），
 * 解析层只做名称识别与通道/位宽信息携带。</p>
 */
public enum LuminBufferFormat {
    RGBA8(8, true, false),
    RGBA16(16, true, false),
    RGBA16F(16, true, true),
    RGBA32F(32, true, true),
    RGB16F(16, false, true),
    RGB32F(32, false, true),
    RG16(16, true, false),
    RG16F(16, true, true),
    RG32F(32, true, true),
    R8(8, false, false),
    R16F(16, false, true),
    R32F(32, false, true),
    RGB10_A2(10, true, false),
    RGB5_A1(5, true, false);

    private final int bitsPerChannel;
    private final boolean hasAlpha;
    private final boolean floatingPoint;

    LuminBufferFormat(int bitsPerChannel, boolean hasAlpha, boolean floatingPoint) {
        this.bitsPerChannel = bitsPerChannel;
        this.hasAlpha = hasAlpha;
        this.floatingPoint = floatingPoint;
    }

    public int bitsPerChannel() {
        return bitsPerChannel;
    }

    public boolean hasAlpha() {
        return hasAlpha;
    }

    public boolean isFloatingPoint() {
        return floatingPoint;
    }

    /** 按名称解析（大小写不敏感；未知返回 null）。 */
    public static LuminBufferFormat parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        for (LuminBufferFormat format : values()) {
            if (format.name().equals(normalized)) {
                return format;
            }
        }
        return null;
    }
}
