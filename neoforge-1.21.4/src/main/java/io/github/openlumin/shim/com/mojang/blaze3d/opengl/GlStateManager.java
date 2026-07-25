package io.github.openlumin.shim.com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.platform.GlConst;

/**
 * NeoForge适配层：模拟Fabric的GlStateManager API
 * 添加NeoForge缺少的GL常量
 */
public class GlStateManager {

    // OpenGL纹理常量
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_MIRRORED_REPEAT = 0x8370;

    public static void _disableScissorTest() {}
    public static void _enableScissorTest() {}
    public static void _scissorBox(int x, int y, int width, int height) {}
    // NeoForge使用不同的API
}
