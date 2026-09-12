package io.github.openlumin.chunk.vertex;

import java.nio.ByteBuffer;

/**
 * 量化顶点写入器：把一个顶点的完整属性编码进 20 字节布局（小端）。
 * <p>编码原则见 {@link LuminQuantizedVertexFormat}；
 * 渗色偏置由 {@link #beginQuad} 记录 quad 质心后逐顶点判定。</p>
 */
public final class LuminVertexWriter {

    private final ByteBuffer buffer;
    private float centroidU;
    private float centroidV;
    private boolean quadActive;

    public LuminVertexWriter(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
    }

    /** 计算并记录本 quad 的 UV 质心（在写入 4 个顶点前调用一次）。 */
    public void beginQuad(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        centroidU = (u0 + u1 + u2 + u3) * 0.25f;
        centroidV = (v0 + v1 + v2 + v3) * 0.25f;
        quadActive = true;
    }

    /**
     * 写入一个顶点（20 字节）。位置输入 section 局部坐标；UV 输入 [0,1]；
     * 颜色输入 0-255 RGBA 分量；光照自动钳制。
     *
     * @param sectionIndex section 索引（0-255，region 内槽位标识）
     * @param biasU        UV u 渗色偏置方向：null = 自动按质心判定
     * @param biasV        UV v 渗色偏置方向：null = 自动按质心判定
     */
    public void putVertex(float x, float y, float z,
                          int r, int g, int b, int a,
                          float u, float v,
                          int blockLight, int skyLight,
                          int material, int sectionIndex,
                          Boolean biasU, Boolean biasV) {
        if (!quadActive) {
            throw new IllegalStateException("beginQuad must be called before writing vertices");
        }
        if (sectionIndex < 0 || sectionIndex > 255) {
            throw new IllegalArgumentException("sectionIndex must be in [0,255], got " + sectionIndex);
        }
        int px = LuminQuantizedVertexFormat.quantizePosition(x);
        int py = LuminQuantizedVertexFormat.quantizePosition(y);
        int pz = LuminQuantizedVertexFormat.quantizePosition(z);

        int positionHi = (px >>> LuminQuantizedVertexFormat.POSITION_SPLIT_BITS)
                | ((py >>> LuminQuantizedVertexFormat.POSITION_SPLIT_BITS) << 10)
                | ((pz >>> LuminQuantizedVertexFormat.POSITION_SPLIT_BITS) << 20);
        int positionLo = (px & LuminQuantizedVertexFormat.POSITION_LOW_MASK)
                | ((py & LuminQuantizedVertexFormat.POSITION_LOW_MASK) << 10)
                | ((pz & LuminQuantizedVertexFormat.POSITION_LOW_MASK) << 20);

        int color = clamp8(r) | (clamp8(g) << 8) | (clamp8(b) << 16) | (clamp8(a) << 24);

        int uBiasBit = biasU != null
                ? (biasU ? 1 : 0)
                : LuminQuantizedVertexFormat.texCoordBiasBit(u, centroidU);
        int vBiasBit = biasV != null
                ? (biasV ? 1 : 0)
                : LuminQuantizedVertexFormat.texCoordBiasBit(v, centroidV);
        int quantizedU = LuminQuantizedVertexFormat.quantizeTexCoord(u);
        int quantizedV = LuminQuantizedVertexFormat.quantizeTexCoord(v);
        int texWord = quantizedU | (uBiasBit << LuminQuantizedVertexFormat.TEXCOORD_BITS)
                | (quantizedV << 16) | (vBiasBit << 31);

        int block = LuminQuantizedVertexFormat.clampLight(blockLight);
        int sky = LuminQuantizedVertexFormat.clampLight(skyLight);
        int lightWord = (block & 0xFF) | ((sky & 0xFF) << 8)
                | ((material & 0xFF) << 16) | ((sectionIndex & 0xFF) << 24);

        buffer.putInt(positionHi);
        buffer.putInt(positionLo);
        buffer.putInt(color);
        buffer.putInt(texWord);
        buffer.putInt(lightWord);
    }

    /** 分量钳制到 [0,255]（语义为饱和而非回绕——300 不应变成 44）。 */
    private static int clamp8(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** 便捷重载：自动按质心判定渗色偏置。 */
    public void putVertex(float x, float y, float z,
                          int r, int g, int b, int a,
                          float u, float v,
                          int blockLight, int skyLight,
                          int material, int sectionIndex) {
        putVertex(x, y, z, r, g, b, a, u, v, blockLight, skyLight, material, sectionIndex, null, null);
    }

    public int bytesWritten() {
        return buffer.position();
    }

    public void reset() {
        quadActive = false;
    }
}
