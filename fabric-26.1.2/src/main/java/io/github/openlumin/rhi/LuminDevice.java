package io.github.openlumin.rhi;

import java.nio.ByteBuffer;

/**
 * 设备与命令录制 / 提交
 *
 * LuminDevice 暴露物理设备能力；LuminCommandEncoder 提供命令录制，
 * 完成后通过 LuminRHI.submit() 提交。
 */
public interface LuminDevice {
    LuminBuffer createBuffer(LuminBufferUsage usage, long size);
    LuminTexture createTexture2D(int width, int height, LuminFormat format);
    LuminTexture createTexture3D(int width, int height, int depth, LuminFormat format);
    LuminSampler createSampler(LuminFilter min, LuminFilter mag, LuminAddressMode u, LuminAddressMode v, LuminAddressMode w);
    LuminShader createShader(String vertexPath, String fragmentPath);
    LuminPipeline createPipeline(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state);
}

/** 管线状态（深度/混合/光栅化） */
record LuminPipelineState(
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

/** 管线（着色器 + 顶点格式 + 状态） */
record LuminPipeline(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state) {}

/** 混合状态 */
record LuminBlendState(
        boolean enabled,
        LuminBlendFactor srcColor, LuminBlendFactor dstColor,
        LuminBlendFactor srcAlpha, LuminBlendFactor dstAlpha
) {
    public static final LuminBlendState OFF = new LuminBlendState(false, LuminBlendFactor.ONE, LuminBlendFactor.ZERO, LuminBlendFactor.ONE, LuminBlendFactor.ZERO);
    public static final LuminBlendState SRC_ALPHA = new LuminBlendState(true, LuminBlendFactor.SRC_ALPHA, LuminBlendFactor.ONE_MINUS_SRC_ALPHA, LuminBlendFactor.ONE, LuminBlendFactor.ONE_MINUS_SRC_ALPHA);
}
