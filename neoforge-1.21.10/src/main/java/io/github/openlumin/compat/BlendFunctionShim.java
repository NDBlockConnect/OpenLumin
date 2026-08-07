package io.github.openlumin.compat;

/**
 * 1.21.10 兼容性桩：BlendFunction 在 1.21.10 中已移除。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
public enum BlendFunctionShim {
    ZERO,
    ONE,
    SRC_COLOR,
    ONE_MINUS_SRC_COLOR,
    DST_COLOR,
    ONE_MINUS_DST_COLOR,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA,
    CONSTANT_COLOR,
    ONE_MINUS_CONSTANT_COLOR,
    CONSTANT_ALPHA,
    ONE_MINUS_CONSTANT_ALPHA,
    SRC_ALPHA_SATURATE
}
