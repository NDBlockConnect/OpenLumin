package io.github.openlumin.rhi;

/** 管线（着色器 + 顶点格式 + 状态） */
public record LuminPipeline(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state) {}
