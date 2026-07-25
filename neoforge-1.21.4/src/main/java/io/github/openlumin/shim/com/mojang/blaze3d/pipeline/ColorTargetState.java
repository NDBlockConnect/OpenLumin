package io.github.openlumin.shim.com.mojang.blaze3d.pipeline;

/**
 * NeoForge适配层：模拟Fabric的ColorTargetState API
 */
public class ColorTargetState {

    private final BlendFunction blendFunction;

    public ColorTargetState(BlendFunction blendFunction) {
        this.blendFunction = blendFunction;
    }

    public BlendFunction getBlendFunction() {
        return blendFunction;
    }
}
