package io.github.openlumin.rhi;

/** 管线状态（深度/混合/光栅化） */
public record LuminPipelineState(
        LuminPolygonMode polygonMode,
        LuminCullMode cullMode,
        LuminFrontFace frontFace,
        boolean depthTest,
        boolean depthWrite,
        LuminCompareOp depthCompare,
        LuminBlendState blend
) {
    public static LuminPipelineState default2D() {
        return new LuminPipelineState(
                LuminPolygonMode.FILL, LuminCullMode.NONE, LuminFrontFace.CCW,
                false, false, LuminCompareOp.ALWAYS,
                LuminBlendState.SRC_ALPHA);
    }
    public static LuminPipelineState default3D() {
        return new LuminPipelineState(
                LuminPolygonMode.FILL, LuminCullMode.BACK, LuminFrontFace.CCW,
                true, true, LuminCompareOp.LESS,
                LuminBlendState.OFF);
    }
}
