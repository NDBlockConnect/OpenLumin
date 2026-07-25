package io.github.openlumin.math;

import java.awt.Color;

/**
 * 颜色工具类：RGBA 插值、十六进制解析、亮度调整、透明度操作。
 */
public final class ColorUtils {

    private ColorUtils() {}

    // ────────────────────────────── 插值 ──────────────────────────────

    /**
     * 在两个颜色之间线性插值（RGBA 各分量分别插值）。
     *
     * @param from 起始颜色
     * @param to   目标颜色
     * @param t    进度 [0, 1]
     */
    public static Color lerp(Color from, Color to, float t) {
        t = clamp01(t);
        int r = (int) (from.getRed()   + (to.getRed()   - from.getRed())   * t);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = (int) (from.getBlue()  + (to.getBlue()  - from.getBlue())  * t);
        int a = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t);
        return new Color(clampI(r), clampI(g), clampI(b), clampI(a));
    }

    /**
     * 对颜色序列按照归一化进度 t 进行多段插值。
     * stops 长度至少为 2；t=0 返回第一个，t=1 返回最后一个。
     */
    public static Color lerpStops(Color[] stops, float t) {
        if (stops.length == 0) throw new IllegalArgumentException("stops must not be empty");
        if (stops.length == 1) return stops[0];
        t = clamp01(t);
        float scaled = t * (stops.length - 1);
        int idx = (int) scaled;
        if (idx >= stops.length - 1) return stops[stops.length - 1];
        return lerp(stops[idx], stops[idx + 1], scaled - idx);
    }

    // ────────────────────────────── 十六进制解析 ──────────────────────────────

    /**
     * 从十六进制字符串解析颜色，支持以下格式：
     * <ul>
     *   <li>{@code #RGB}     → 每分量复制，Alpha=255</li>
     *   <li>{@code #RRGGBB}  → Alpha=255</li>
     *   <li>{@code #RRGGBBAA}</li>
     *   <li>以上不带 # 前缀</li>
     * </ul>
     *
     * @throws IllegalArgumentException 格式不符
     */
    public static Color fromHex(String hex) {
        if (hex == null || hex.isEmpty()) throw new IllegalArgumentException("hex string is empty");
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        return switch (s.length()) {
            case 3 -> {
                int r = Integer.parseInt(s.substring(0, 1), 16);
                int g = Integer.parseInt(s.substring(1, 2), 16);
                int b = Integer.parseInt(s.substring(2, 3), 16);
                yield new Color(r * 17, g * 17, b * 17, 255);
            }
            case 6 -> {
                int rgb = Integer.parseInt(s, 16);
                yield new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
            }
            case 8 -> {
                long rgba = Long.parseLong(s, 16);
                yield new Color((int) ((rgba >> 24) & 0xFF), (int) ((rgba >> 16) & 0xFF),
                        (int) ((rgba >> 8) & 0xFF), (int) (rgba & 0xFF));
            }
            default -> throw new IllegalArgumentException("Invalid hex color: " + hex);
        };
    }

    /**
     * 从 ARGB 整数构造颜色（格式：0xAARRGGBB）。
     */
    public static Color fromArgb(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                (argb >> 24) & 0xFF);
    }

    /**
     * 从 RGBA 整数构造颜色（格式：0xRRGGBBAA）。
     */
    public static Color fromRgba(int rgba) {
        return new Color((rgba >> 24) & 0xFF, (rgba >> 16) & 0xFF,
                (rgba >> 8) & 0xFF, rgba & 0xFF);
    }

    /** 转换为 ARGB 整数（0xAARRGGBB）。 */
    public static int toArgb(Color c) {
        return (c.getAlpha() << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    // ────────────────────────────── 调整 ──────────────────────────────

    /** 返回指定 alpha 值的同色颜色（alpha ∈ [0, 255]）。 */
    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clampI(alpha));
    }

    /** 返回指定 alpha 比例的同色颜色（factor ∈ [0, 1]）。 */
    public static Color withAlphaF(Color c, float factor) {
        return withAlpha(c, Math.round(c.getAlpha() * clamp01(factor)));
    }

    /**
     * 调整亮度：factor > 1 变亮，factor < 1 变暗，factor = 1 不变。
     * 只影响 RGB 分量，Alpha 不变。
     */
    public static Color brighten(Color c, float factor) {
        int r = clampI(Math.round(c.getRed()   * factor));
        int g = clampI(Math.round(c.getGreen() * factor));
        int b = clampI(Math.round(c.getBlue()  * factor));
        return new Color(r, g, b, c.getAlpha());
    }

    /**
     * 颜色叠加（正片叠底 / multiply）：每分量相乘后除以255。
     */
    public static Color multiply(Color base, Color overlay) {
        int r = (base.getRed()   * overlay.getRed())   / 255;
        int g = (base.getGreen() * overlay.getGreen()) / 255;
        int b = (base.getBlue()  * overlay.getBlue())  / 255;
        int a = (base.getAlpha() * overlay.getAlpha()) / 255;
        return new Color(r, g, b, a);
    }

    /**
     * 返回颜色的感知亮度（Rec.709 标准），范围 [0, 1]。
     * 可用于判断前景色应使用黑或白。
     */
    public static float luminance(Color c) {
        float r = c.getRed()   / 255.0f;
        float g = c.getGreen() / 255.0f;
        float b = c.getBlue()  / 255.0f;
        return 0.2126f * linearize(r) + 0.7152f * linearize(g) + 0.0722f * linearize(b);
    }

    /** 根据背景亮度自动返回黑色或白色（对比色），用于文字颜色自适应。 */
    public static Color contrastColor(Color background) {
        return luminance(background) > 0.179f ? Color.BLACK : Color.WHITE;
    }

    // ────────────────────────────── 内部工具 ──────────────────────────────

    private static float linearize(float channel) {
        return channel <= 0.04045f ? channel / 12.92f
                : (float) Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static int clampI(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
