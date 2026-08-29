package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.openlumin.rhi.LuminPipeline;
import io.github.openlumin.rhi.LuminPipelineState;
import io.github.openlumin.rhi.LuminShader;
import io.github.openlumin.rhi.LuminVertexFormat;

/** LuminPipeline GL 后端实现 — 懒绑定 MC RenderPipeline */
public final class LuminPipelineGL implements LuminPipeline {
    public final LuminShader shader;
    public final LuminVertexFormat vertexFormat;
    public final LuminPipelineState state;
    public RenderPipeline mcPipeline;
    public LuminPipelineGL(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state) {
        this.shader = shader; this.vertexFormat = vertexFormat; this.state = state;
    }
    @Override public LuminShader shader() { return shader; }
    @Override public LuminVertexFormat vertexFormat() { return vertexFormat; }
    @Override public LuminPipelineState state() { return state; }
}
