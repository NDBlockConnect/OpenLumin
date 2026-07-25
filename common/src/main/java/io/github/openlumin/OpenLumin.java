package io.github.openlumin;

import io.github.openlumin.shaders.LuminPostProcess;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

/**
 * OpenLumin 高层 2D 特效统一入口。
 * <p>
 * 这是面向下游模组的**公开契约入口**：所有方法均为静态，内部按 MC 版本路由至对应后端
 * （1.21.x OpenGL / 26.x Vulkan），调用层代码版本无关。在游戏渲染循环中直接调用即可，
 * 无需手动管理 shader 生命周期。
 *
 * <h3>坐标系约定</h3>
 * Blur / Filter 的 {@code x, y, width, height} 均为 <b>GUI 逻辑坐标</b>（已过 guiScale），
 * OpenLumin 内部负责换算为帧缓冲像素坐标，调用方无需感知。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * OpenLumin.blur(x, y, w, h, 12f, 10f);   // 圆角毛玻璃
 * OpenLumin.fxaa();                        // 全屏抗锯齿
 * OpenLumin.filter(new Color(0, 0, 0, 80)); // 颜色滤镜
 * OpenLumin.sandboxSea(mouseX, mouseY);    // 动态背景
 * }</pre>
 *
 * @see Sandbox 内置动态背景预设常量
 */
public final class OpenLumin {

    private OpenLumin() {}

    // ══════════════════════════════════════════════════════════════════
    //  Blur — 区域高斯模糊（毛玻璃）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 对屏幕矩形区域执行模糊，四角等圆角。
     *
     * @param x        区域左上角 X（GUI 逻辑坐标）
     * @param y        区域左上角 Y（GUI 逻辑坐标）
     * @param width    区域宽度（GUI 逻辑坐标）
     * @param height   区域高度（GUI 逻辑坐标）
     * @param radius   圆角半径（GUI 逻辑坐标），0 = 直角
     * @param strength 模糊强度，采样偏移缩放系数（EpsilonBC 惯用 int 1~16，原值直传）
     */
    public static void blur(float x, float y, float width, float height,
                            float radius, float strength) {
        LuminPostProcess.blur(x, y, width, height, radius, strength);
    }

    /**
     * 对屏幕矩形区域执行模糊，四角独立圆角。
     *
     * @param rTL 左上圆角  @param rTR 右上圆角  @param rBR 右下圆角  @param rBL 左下圆角
     */
    public static void blur(float x, float y, float width, float height,
                            float rTL, float rTR, float rBR, float rBL,
                            float strength) {
        LuminPostProcess.blur(x, y, width, height, rTL, rTR, rBR, rBL, strength);
    }

    /**
     * 对 3D 世界坐标 AABB 投影区域执行模糊（游戏内物体模糊高亮）。
     */
    public static void blur3DBox(AABB box, double strength) {
        LuminPostProcess.blur3DBox(box, strength);
    }

    // ══════════════════════════════════════════════════════════════════
    //  FXAA — 快速近似抗锯齿
    // ══════════════════════════════════════════════════════════════════

    /** 对主渲染目标执行 FXAA。 */
    public static void fxaa() {
        LuminPostProcess.fxaa();
    }

    /** 对指定 {@link RenderTarget} 执行 FXAA。 */
    public static void fxaa(RenderTarget target) {
        LuminPostProcess.fxaa(target);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Filter — 颜色滤镜叠加
    // ══════════════════════════════════════════════════════════════════

    /** 对主渲染目标叠加颜色滤镜（Alpha 控制混合强度）。 */
    public static void filter(Color color) {
        LuminPostProcess.filter(color);
    }

    /** 对指定 RenderTarget 叠加颜色滤镜。 */
    public static void filter(RenderTarget target, Color color) {
        LuminPostProcess.filter(target, color);
    }

    /** 快捷：对主目标应用黑色遮罩，{@code alpha} 控制暗度（0=透明，255=全黑）。 */
    public static void dim(int alpha) {
        LuminPostProcess.dim(alpha);
    }

    /** 快捷：对主目标叠加 ARGB 整数滤镜（格式 0xAARRGGBB）。 */
    public static void filterArgb(int argb) {
        LuminPostProcess.filterArgb(argb);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Sandbox — 运行时 GLSL 动态背景
    // ══════════════════════════════════════════════════════════════════

    /**
     * 渲染指定 GLSL Fragment Shader（Shadertoy 风格）作为全屏动态背景。
     *
     * @param fragmentShader Fragment Shader 资源路径
     * @param mouseX         鼠标 X（屏幕像素）
     * @param mouseY         鼠标 Y（屏幕像素）
     * @see Sandbox
     */
    public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY) {
        LuminPostProcess.sandbox(fragmentShader, mouseX, mouseY);
    }

    /** 渲染 GLSL 动态背景，使用自定义动画起始时间。 */
    public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY,
                               long startTimeMs) {
        LuminPostProcess.sandbox(fragmentShader, mouseX, mouseY, startTimeMs);
    }

    /** 重置所有 Sandbox 动画计时器。 */
    public static void sandboxResetTime() {
        LuminPostProcess.sandboxResetTime();
    }

    /** 释放所有 Sandbox 管线缓存（回收显存）。 */
    public static void sandboxClose() {
        LuminPostProcess.sandboxClose();
    }

    /** 海平面波浪动态背景。 */
    public static void sandboxSea(double mouseX, double mouseY) {
        LuminPostProcess.sandboxSea(mouseX, mouseY);
    }

    /** 流动云彩动态背景。 */
    public static void sandboxClouds(double mouseX, double mouseY) {
        LuminPostProcess.sandboxClouds(mouseX, mouseY);
    }

    /** 外星地形动态背景。 */
    public static void sandboxAlien(double mouseX, double mouseY) {
        LuminPostProcess.sandboxAlien(mouseX, mouseY);
    }

    /** 地狱烈焰动态背景。 */
    public static void sandboxInferno(double mouseX, double mouseY) {
        LuminPostProcess.sandboxInferno(mouseX, mouseY);
    }

    /** 星球轨道动态背景。 */
    public static void sandboxPlanet(double mouseX, double mouseY) {
        LuminPostProcess.sandboxPlanet(mouseX, mouseY);
    }

    /** 黑洞动态背景。 */
    public static void sandboxBlackHole(double mouseX, double mouseY) {
        LuminPostProcess.sandboxBlackHole(mouseX, mouseY);
    }

    /** Minecraft 风格像素动态背景。 */
    public static void sandboxMinecraft(double mouseX, double mouseY) {
        LuminPostProcess.sandboxMinecraft(mouseX, mouseY);
    }
}
