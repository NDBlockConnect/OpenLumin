/**
 * 1.21.10 兼容性桩：CompareOp 在 1.21.10 中已移除或不同。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
package io.github.openlumin.compat;

/**
 * fabric-1.21.10 shim：CompareOp 在 1.21.10 中已移除。
 */
public enum CompareOpShim {
    ALWAYS_PASS, NEVER_PASS,
    LESS, LESS_OR_EQUAL,
    EQUAL, NOT_EQUAL,
    GREATER, GREATER_OR_EQUAL
}
