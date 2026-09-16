package io.github.openlumin.shaderpack;

/**
 * 渲染目标规格（指令解析结果）。
 *
 * @param format       缓冲格式；null = 使用引擎默认
 * @param clear        是否每帧清屏（{@code colortexNClear}）
 * @param clearColor   清屏色 RGBA（0..1）；null = 使用引擎默认
 * @param mipmapEnabled 是否生成 mipmap（{@code colortexNMipmapEnabled}）
 * @param sizeScale    相对尺寸系数（{@code size.buffer.<b>}）；null = 与主目标同尺寸
 */
public record LuminTargetSpec(
        LuminBufferFormat format,
        boolean clear,
        float[] clearColor,
        boolean mipmapEnabled,
        SizeScale sizeScale) {

    /** 相对/绝对尺寸（{@code size.buffer.<b> = <w> <h>}，可为倍数或绝对值）。 */
    public record SizeScale(float width, float height, boolean absolute) {
        public SizeScale {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("size scale must be > 0, got " + width + "x" + height);
            }
        }

        public static SizeScale relative(float width, float height) {
            return new SizeScale(width, height, false);
        }

        public static SizeScale absolute(float width, float height) {
            return new SizeScale(width, height, true);
        }
    }

    public LuminTargetSpec {
        if (clearColor != null && clearColor.length != 4) {
            throw new IllegalArgumentException("clearColor must have exactly 4 components");
        }
    }

    /** 引擎默认规格占位（未声明任何指令时）。 */
    public static LuminTargetSpec defaults() {
        return new LuminTargetSpec(null, true, null, false, null);
    }

    public LuminTargetSpec withFormat(LuminBufferFormat value) {
        return new LuminTargetSpec(value, clear, clearColor, mipmapEnabled, sizeScale);
    }

    public LuminTargetSpec withClear(boolean value) {
        return new LuminTargetSpec(format, value, clearColor, mipmapEnabled, sizeScale);
    }

    public LuminTargetSpec withClearColor(float[] value) {
        return new LuminTargetSpec(format, clear, value, mipmapEnabled, sizeScale);
    }

    public LuminTargetSpec withMipmap(boolean value) {
        return new LuminTargetSpec(format, clear, clearColor, value, sizeScale);
    }

    public LuminTargetSpec withSizeScale(SizeScale value) {
        return new LuminTargetSpec(format, clear, clearColor, mipmapEnabled, value);
    }
}
