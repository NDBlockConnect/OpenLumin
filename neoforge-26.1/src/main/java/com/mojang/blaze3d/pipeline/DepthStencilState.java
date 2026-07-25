package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.platform.CompareOp;

/**
 * fabric-1.21.10 shim：DepthStencilState 在 1.21.10 中已移除。
 */
public class DepthStencilState {

    private final CompareOp compareOp;
    private final boolean writeMask;

    public DepthStencilState(CompareOp compareOp, boolean writeMask) {
        this.compareOp = compareOp;
        this.writeMask = writeMask;
    }

    public CompareOp compareOp() {
        return compareOp;
    }

    public boolean writeMask() {
        return writeMask;
    }
}
