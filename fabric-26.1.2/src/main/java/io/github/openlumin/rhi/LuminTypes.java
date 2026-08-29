package io.github.openlumin.rhi;

/**
 * RHI 资源类型枚举与基类
 *
 * 设计要点：
 * - LuminFormat 跨后端统一命名；后端内部处理字节序
 * - LuminBuffer/LuminTexture 为资源句柄；close() 释放
 * - LuminBufferView/LuminTextureView 是资源上的视图（子范围/特定 mip）
 *
 * 重要：业务层不得写裸字节；颜色字节序由后端内部处理。
 */

import java.nio.ByteBuffer;
import java.io.Closeable;

/** 像素格式 */
enum LuminFormat {
    R8G8B8A8_UNORM, R8G8B8A8_SRGB,
    R16G16B16A16_FLOAT,
    R32G32B32A32_FLOAT,
    R11G11B10_FLOAT,
    A8_UNORM,
    D32_FLOAT, D24_UNORM_S8_UINT, D32_FLOAT_S8X24_UINT,
    BC1_UNORM, BC3_UNORM, BC5_UNORM, BC7_UNORM
}

/** 索引元素宽度 */
enum LuminIndexType { UINT16, UINT32 }

/** 纹理过滤 */
enum LuminFilter { NEAREST, LINEAR }

/** 寻址模式 */
enum LuminAddressMode { REPEAT, CLAMP_TO_EDGE, MIRRORED_REPEAT }

/** 多边形模式 */
enum LuminPolygonMode { FILL, LINE, POINT }

/** 背面剔除 */
enum LuminCullMode { NONE, FRONT, BACK }

/** 正面绕序 */
enum LuminFrontFace { CCW, CW }

/** 深度比较 */
enum LuminCompareOp { NEVER, LESS, EQUAL, LEQUAL, GREATER, NOTEQUAL, GEQUAL, ALWAYS }

/** 混合 */
enum LuminBlendFactor { ZERO, ONE, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, SRC_COLOR, ONE_MINUS_SRC_COLOR, DST_ALPHA, ONE_MINUS_DST_ALPHA, DST_COLOR, ONE_MINUS_DST_COLOR }

/** 缓冲用途 */
enum LuminBufferUsage {
    VERTEX, INDEX, UNIFORM, STORAGE, COPY_SRC, COPY_DST
}

/** 缓冲（可关闭资源） */
interface LuminBuffer extends Closeable {
    long size();
    LuminBufferView view(long offset, long length);
    @Override void close();
}

/** 缓冲视图（offset/length 子范围） */
record LuminBufferView(LuminBuffer buffer, long offset, long length) {}

/** 纹理（可关闭资源） */
interface LuminTexture extends Closeable {
    int width();
    int height();
    int depth();
    LuminFormat format();
    LuminTextureView view();
    @Override void close();
}

/** 纹理视图 */
interface LuminTextureView {
    LuminTexture texture();
}

/** 采样器 */
interface LuminSampler {
    LuminFilter minFilter();
    LuminFilter magFilter();
    LuminAddressMode addressU();
    LuminAddressMode addressV();
    LuminAddressMode addressW();
}

/** 着色器（顶点 + 片段） */
interface LuminShader {
    String label();
}

/** 顶点格式（条目） */
record LuminVertexAttribute(String name, LuminFormat format, int offset) {}

/** 顶点格式 */
class LuminVertexFormat {
    private final LuminVertexAttribute[] attributes;
    public LuminVertexFormat(LuminVertexAttribute... attrs) { this.attributes = attrs; }
    public LuminVertexAttribute[] attributes() { return attributes; }
    public int stride() { int s=0; for (var a:attributes) s=Math.max(s,a.offset()+4); return s; }
}
