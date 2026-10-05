package io.github.openlumin.shaderpack.plan;

import io.github.openlumin.shaderpack.LuminBufferFormat;
import io.github.openlumin.shaderpack.LuminTargetId;

import java.util.Arrays;

/**
 * 单个渲染目标的资源分配（WP-2 M4，纯 CPU 规划层）：格式、尺寸、清屏与乒乓策略。
 *
 * <p>平台执行层消费本结构完成 GPU 侧惰性分配（设计 §4 第 2 步）。</p>
 *
 * @param target    目标标识
 * @param format    已解析格式（未声明时为按目标类别的引擎默认）
 * @param size      尺寸策略（相对/绝对，X/Y 独立）
 * @param clear     是否每帧清屏
 * @param clearColor 清屏色（符号来源或字面量）
 * @param mipmap    是否生成 mipmap
 * @param pingPong  是否为 main/alt 乒乓对（colortex 为真）
 */
public record LuminResourceAllocation(
        LuminTargetId target,
        LuminBufferFormat format,
        SizeSpec size,
        boolean clear,
        ClearColor clearColor,
        boolean mipmap,
        boolean pingPong) {

    public LuminResourceAllocation {
        if (target == null) {
            throw new NullPointerException("target");
        }
        if (format == null) {
            throw new NullPointerException("format");
        }
        size = size == null ? SizeSpec.full() : size;
        clearColor = clearColor == null ? ClearColor.transparentBlack() : clearColor;
    }

    /** 尺寸策略：相对（屏幕倍数）或绝对像素，两轴独立（语义同 Iris TextureScaleOverride）。 */
    public record SizeSpec(float width, float height,
                           boolean widthRelative, boolean heightRelative) {
        public SizeSpec {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException(
                        "size must be >= 0, got " + width + "x" + height);
            }
        }

        public static SizeSpec full() {
            return new SizeSpec(1f, 1f, true, true);
        }

        public int resolveWidth(int baseWidth) {
            return Math.round(widthRelative ? baseWidth * width : width);
        }

        public int resolveHeight(int baseHeight) {
            return Math.round(heightRelative ? baseHeight * height : height);
        }
    }

    /**
     * 清屏色：字面量或符号来源（雾色/白/透明黑/深度远平面）。
     * <p>符号来源由执行层按帧上下文解析（如雾色随生物群系变化）。</p>
     */
    public record ClearColor(float[] rgba, Source source) {

        public enum Source {
            /** 字面量 RGBA（rgba 非空）。 */
            LITERAL,
            /** 雾色（colortex0 默认；α=1）。 */
            FOG,
            /** 纯白（colortex1 默认）。 */
            WHITE,
            /** 透明黑（其余 colortex 默认）。 */
            TRANSPARENT_BLACK,
            /** 深度远平面（depthtex/shadowtex 默认）。 */
            DEPTH_FAR
        }

        public ClearColor {
            if (source == null) {
                throw new NullPointerException("source");
            }
            if (source == Source.LITERAL && rgba == null) {
                throw new IllegalArgumentException("LITERAL clear color requires rgba");
            }
            rgba = rgba == null ? null : rgba.clone();
        }

        @Override
        public float[] rgba() {
            return rgba == null ? null : rgba.clone();
        }

        public static ClearColor literal(float[] rgba) {
            return new ClearColor(rgba, Source.LITERAL);
        }

        public static ClearColor fog() {
            return new ClearColor(null, Source.FOG);
        }

        public static ClearColor white() {
            return new ClearColor(null, Source.WHITE);
        }

        public static ClearColor transparentBlack() {
            return new ClearColor(null, Source.TRANSPARENT_BLACK);
        }

        public static ClearColor depthFar() {
            return new ClearColor(null, Source.DEPTH_FAR);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ClearColor that)) {
                return false;
            }
            return source == that.source && Arrays.equals(rgba, that.rgba);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode() + Arrays.hashCode(rgba);
        }
    }
}
