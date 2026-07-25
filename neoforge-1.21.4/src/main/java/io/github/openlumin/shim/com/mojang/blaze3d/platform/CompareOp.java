package io.github.openlumin.shim.com.mojang.blaze3d.platform;

/**
 * NeoForge适配层：模拟Fabric的CompareOp API
 */
public enum CompareOp {
    NEVER,
    LESS,
    EQUAL,
    LESS_OR_EQUAL,
    GREATER,
    NOT_EQUAL,
    GREATER_OR_EQUAL,
    ALWAYS,
    ALWAYS_PASS  // 添加ALWAYS_PASS别名
}
