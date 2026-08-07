package io.github.openlumin.compat;

/**
 * 1.21.10 兼容性桩：ColorTargetState 在 1.21.10 中已移除。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
public class ColorTargetStateShim {

    private final BlendFunctionShim blendFunction;

    public ColorTargetStateShim(BlendFunctionShim blendFunction) {
        this.blendFunction = blendFunction;
    }

    public BlendFunctionShim getBlendFunction() {
        return blendFunction;
    }
}
