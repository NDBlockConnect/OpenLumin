package io.github.openlumin;

import io.github.openlumin.shaders.GlslSandBox;
import net.minecraft.resources.ResourceLocation;

/**
 * OpenLumin 内置 GLSL 动态背景预设常量。
 * <p>
 * 与 {@link OpenLumin#sandbox(ResourceLocation, double, double)} 配合使用：
 * <pre>{@code
 * OpenLumin.sandbox(Sandbox.SEA, mouseX, mouseY);
 * }</pre>
 * 也可直接调用 {@code OpenLumin.sandboxSea(mx, my)} 等快捷方法。
 */
public final class Sandbox {

    private Sandbox() {}

    /** 海平面波浪效果 */
    public static final ResourceLocation SEA        = GlslSandBox.SEA_LEVEL;
    /** 流动云彩效果 */
    public static final ResourceLocation CLOUDS     = GlslSandBox.CLOUDS;
    /** 外星地形效果 */
    public static final ResourceLocation ALIEN      = GlslSandBox.ALIEN_TERRAIN;
    /** 地狱烈焰效果 */
    public static final ResourceLocation INFERNO    = GlslSandBox.INFERNO;
    /** 星球轨道效果 */
    public static final ResourceLocation PLANET     = GlslSandBox.PLANET;
    /** 黑洞效果 */
    public static final ResourceLocation BLACK_HOLE = GlslSandBox.BLACK_HOLE;
    /** Minecraft 风格像素效果 */
    public static final ResourceLocation MINECRAFT  = GlslSandBox.MINECRAFT;
}
