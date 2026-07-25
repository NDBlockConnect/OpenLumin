package com.mojang.blaze3d.pipeline;

/**
 * fabric-1.21.10 shim：ColorTargetState 在 1.21.10 中已移除。
 * 保留此类使 common 源码可编译。RenderPipeline.Builder 中的 withColorTargetState() 调用
 * 若在 1.21.10 builder 上不存在，需要同时在 LuminRenderPipelines 中移除该调用。
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
