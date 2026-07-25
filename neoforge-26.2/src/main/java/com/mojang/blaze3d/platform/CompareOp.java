package com.mojang.blaze3d.platform;

/**
 * fabric-1.21.10 shim：CompareOp 在 1.21.10 中已移除。
 */
public enum CompareOp {
    ALWAYS_PASS, NEVER_PASS,
    LESS, LESS_OR_EQUAL,
    EQUAL, NOT_EQUAL,
    GREATER, GREATER_OR_EQUAL
}
