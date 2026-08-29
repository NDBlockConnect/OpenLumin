package io.github.openlumin.rhi;

/** 顶点格式 */
public class LuminVertexFormat {
    private final LuminVertexAttribute[] attributes;
    public LuminVertexFormat(LuminVertexAttribute... attrs) { this.attributes = attrs; }
    public LuminVertexAttribute[] attributes() { return attributes; }
    public int stride() { int s=0; for (var a:attributes) s=Math.max(s,a.offset()+4); return s; }
}
