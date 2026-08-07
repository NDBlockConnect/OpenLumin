package io.github.openlumin.compat;

/**
 * 1.21.10 兼容性桩：DepthStencilState 在 1.21.10 中已移除。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
public class DepthStencilStateShim {

    private final CompareOpShim compareOp;
    private final boolean writeMask;

    public DepthStencilStateShim(CompareOpShim compareOp, boolean writeMask) {
        this.compareOp = compareOp;
        this.writeMask = writeMask;
    }

    public CompareOpShim compareOp() {
        return compareOp;
    }

    public boolean writeMask() {
        return writeMask;
    }
}
