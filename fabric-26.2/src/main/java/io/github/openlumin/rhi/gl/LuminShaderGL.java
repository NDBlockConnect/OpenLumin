package io.github.openlumin.rhi.gl;

import io.github.openlumin.rhi.LuminShader;

/** LuminShader — 路径占位（Alpha 2 范围：MC 26.2 的 RenderPipeline 强绑定 shader） */
public final class LuminShaderGL implements LuminShader {
    public final String label;
    public final String vertexPath;
    public final String fragmentPath;
    public LuminShaderGL(String vertexPath, String fragmentPath) {
        this.vertexPath = vertexPath; this.fragmentPath = fragmentPath;
        this.label = (vertexPath == null ? "?" : vertexPath) + "+" + (fragmentPath == null ? "?" : fragmentPath);
    }
    @Override public String label() { return label; }
}
