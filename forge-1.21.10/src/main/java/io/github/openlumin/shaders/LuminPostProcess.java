package io.github.openlumin.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

/**
 * OpenLumin 后处理效果统一门面。
 * <p>
 * 所有方法均为静态调用，内部委托给对应的单例 Shader 实例，无需手动管理生命周期。
 * 在游戏渲染循环中（通常是 RenderGuiLayerEvents 或 HudRenderCallback）调用即可。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 对 HUD 中某个矩形区域应用圆角毛玻璃模糊
 * LuminPostProcess.blur(x, y, width, height, 12f, 0.8f);
 *
 * // 对全屏应用 FXAA 抗锯齿
 * LuminPostProcess.fxaa();
 *
 * // 对整个主目标叠加半透明彩色滤镜
 * LuminPostProcess.filter(new Color(0, 0, 0, 80));
 *
 * // 播放内置 GLSL Sandbox 动态背景
 * LuminPostProcess.sandbox(LuminPostProcess.Sandbox.SEA, mouseX, mouseY);
 * }</pre>
 */
public final class LuminPostProcess {

    private LuminPostProcess() {}

    // ══════════════════════════════════════════════════════════════════
    //  Blur — 区域高斯模糊（毛玻璃效果）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 对屏幕上指定矩形区域执行模糊，四角使用相同圆角半径。
     *
     * @param x            区域左上角 X（屏幕像素坐标）
     * @param y            区域左上角 Y
     * @param width        区域宽度
     * @param height       区域高度
     * @param radius       圆角半径，0 = 直角
     * @param blurStrength 模糊强度，采样偏移缩放系数，原值直传 shader（EpsilonBC 惯用 int 1~16）
     */
    public static void blur(float x, float y, float width, float height,
                            float radius, float blurStrength) {
        BlurShader.INSTANCE.render(x, y, width, height, radius, blurStrength);
    }

    /**
     * 对指定矩形区域执行模糊，四角可分别指定圆角半径。
     *
     * @param rTL 左上圆角半径
     * @param rTR 右上圆角半径
     * @param rBR 右下圆角半径
     * @param rBL 左下圆角半径
     */
    public static void blur(float x, float y, float width, float height,
                            float rTL, float rTR, float rBR, float rBL,
                            float blurStrength) {
        BlurShader.INSTANCE.render(x, y, width, height, rTL, rTR, rBR, rBL, blurStrength);
    }

    /**
     * 对3D空间中的 AABB 包围盒投影区域执行模糊（适用于游戏内物体模糊高亮）。
     *
     * @param box          世界坐标包围盒
     * @param blurStrength 模糊强度
     */
    public static void blur3DBox(AABB box, double blurStrength) {
        BlurShader.INSTANCE.render3DBox(box, blurStrength);
    }

    // ══════════════════════════════════════════════════════════════════
    //  FXAA — 快速近似抗锯齿
    // ══════════════════════════════════════════════════════════════════

    /**
     * 对主渲染目标（Minecraft 主帧缓冲）执行 FXAA 抗锯齿。
     * 通常在所有游戏内渲染完成后、UI 渲染前调用。
     */
    public static void fxaa() {
        FXAAShader.INSTANCE.renderMainTarget();
    }

    /**
     * 对指定 {@link RenderTarget} 执行 FXAA。
     */
    public static void fxaa(RenderTarget target) {
        FXAAShader.INSTANCE.render(target);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Filter — 颜色滤镜叠加
    // ══════════════════════════════════════════════════════════════════

    /**
     * 对主渲染目标叠加颜色滤镜。颜色的 Alpha 决定混合强度。
     * <p>
     * 常见用途：全屏变暗（死亡效果）、色调叠加（夜视、幻觉等）。
     *
     * @param color 滤镜颜色，包含 Alpha 通道
     */
    public static void filter(Color color) {
        FilterShader.INSTANCE.renderToMainTarget(color);
    }

    /**
     * 对指定 RenderTarget 叠加颜色滤镜。
     */
    public static void filter(RenderTarget target, Color color) {
        FilterShader.INSTANCE.render(target, color);
    }

    /**
     * 快捷方法：对主目标应用黑色遮罩，{@code alpha} 控制暗度（0=透明，255=全黑）。
     */
    public static void dim(int alpha) {
        filter(new Color(0, 0, 0, Math.clamp(alpha, 0, 255)));
    }

    /**
     * 快捷方法：对主目标叠加指定 ARGB 整数滤镜（格式 0xAARRGGBB）。
     */
    public static void filterArgb(int argb) {
        filter(new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                (argb >> 24) & 0xFF));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Sandbox — 运行时 GLSL 动态背景
    // ══════════════════════════════════════════════════════════════════

    /**
     * 渲染指定 GLSL Fragment Shader（Shadertoy 风格）作为全屏动态背景。
     * Shader 接收 {@code iResolution}、{@code iTime}、{@code iMouse} 等标准 uniform。
     *
     * @param fragmentShader Fragment Shader 资源路径（JSON 管线定义）
     * @param mouseX         鼠标 X（屏幕像素）
     * @param mouseY         鼠标 Y（屏幕像素）
     * @see Sandbox 内置预设常量
     */
    public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY) {
        GlslSandBox.INSTANCE.render(fragmentShader, mouseX, mouseY);
    }

    /**
     * 渲染 GLSL 动态背景，使用自定义动画起始时间（适合暂停/重播动画）。
     *
     * @param startTimeMs 动画起始时间（{@link System#currentTimeMillis()} 格式）
     */
    public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY,
                               long startTimeMs) {
        GlslSandBox.INSTANCE.render(fragmentShader, mouseX, mouseY, startTimeMs);
    }

    /**
     * 重置所有 Sandbox 动画的计时器（从头开始播放动画）。
     */
    public static void sandboxResetTime() {
        GlslSandBox.INSTANCE.resetTime();
    }

    /**
     * 释放所有 Sandbox 管线缓存（当不再需要动态背景时调用以回收显存）。
     */
    public static void sandboxClose() {
        GlslSandBox.INSTANCE.close();
    }

    // ══════════════════════════════════════════════════════════════════
    //  Sandbox 内置预设快捷方法
    // ══════════════════════════════════════════════════════════════════

    /**
     * 内置 GLSL 动态背景预设的 ResourceLocation 常量，与 {@link #sandbox} 配合使用。
     */
    public static final class Sandbox {
        private Sandbox() {}

        /** 海平面波浪效果 */
        public static final ResourceLocation SEA         = GlslSandBox.SEA_LEVEL;
        /** 流动云彩效果 */
        public static final ResourceLocation CLOUDS      = GlslSandBox.CLOUDS;
        /** 外星地形效果 */
        public static final ResourceLocation ALIEN       = GlslSandBox.ALIEN_TERRAIN;
        /** 地狱烈焰效果 */
        public static final ResourceLocation INFERNO     = GlslSandBox.INFERNO;
        /** 星球轨道效果 */
        public static final ResourceLocation PLANET      = GlslSandBox.PLANET;
        /** 黑洞效果 */
        public static final ResourceLocation BLACK_HOLE  = GlslSandBox.BLACK_HOLE;
        /** Minecraft 风格像素效果 */
        public static final ResourceLocation MINECRAFT   = GlslSandBox.MINECRAFT;
    }

    /** 渲染海平面波浪动态背景。 */
    public static void sandboxSea(double mouseX, double mouseY) {
        sandbox(Sandbox.SEA, mouseX, mouseY);
    }

    /** 渲染流动云彩动态背景。 */
    public static void sandboxClouds(double mouseX, double mouseY) {
        sandbox(Sandbox.CLOUDS, mouseX, mouseY);
    }

    /** 渲染外星地形动态背景。 */
    public static void sandboxAlien(double mouseX, double mouseY) {
        sandbox(Sandbox.ALIEN, mouseX, mouseY);
    }

    /** 渲染地狱烈焰动态背景。 */
    public static void sandboxInferno(double mouseX, double mouseY) {
        sandbox(Sandbox.INFERNO, mouseX, mouseY);
    }

    /** 渲染星球轨道动态背景。 */
    public static void sandboxPlanet(double mouseX, double mouseY) {
        sandbox(Sandbox.PLANET, mouseX, mouseY);
    }

    /** 渲染黑洞动态背景。 */
    public static void sandboxBlackHole(double mouseX, double mouseY) {
        sandbox(Sandbox.BLACK_HOLE, mouseX, mouseY);
    }

    /** 渲染 Minecraft 风格像素动态背景。 */
    public static void sandboxMinecraft(double mouseX, double mouseY) {
        sandbox(Sandbox.MINECRAFT, mouseX, mouseY);
    }
}
