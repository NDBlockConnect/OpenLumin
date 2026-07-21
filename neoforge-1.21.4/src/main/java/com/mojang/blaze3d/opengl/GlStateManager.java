package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.platform.GlConst;

/**
 * NeoForge适配层：模拟Fabric的GlStateManager API
 * 添加NeoForge缺少的GL常量
 */
public class GlStateManager {

    // OpenGL纹理常量
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_MIRRORED_REPEAT = 0x8370;

    // NeoForge使用不同的API
}
