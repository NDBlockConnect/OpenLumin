package io.github.openlumin.chunk.vertex;

import java.nio.ByteBuffer;

/**
 * 量化顶点读取器：从 20 字节布局解码一个顶点（小端），用于校验与测试工具链。
 * <p>解码与 {@link LuminVertexWriter} 逐字段对应；位置/纹理反量化遵循
 * {@link LuminQuantizedVertexFormat} 的尺度与偏移。</p>
 */
public final class LuminVertexReader {

    private final ByteBuffer buffer;

    public LuminVertexReader(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
    }

    public boolean hasRemaining() {
        return buffer.remaining() >= LuminQuantizedVertexFormat.STRIDE_BYTES;
    }

    /**
     * 解码一个顶点的 20 字节。
     *
     * @return 解码结果（位置为 section 局部坐标）
     */
    public DecodedVertex getVertex() {
        int positionHi = buffer.getInt();
        int positionLo = buffer.getInt();
        int color = buffer.getInt();
        int texWord = buffer.getInt();
        int lightWord = buffer.getInt();

        int px = (positionHi & LuminQuantizedVertexFormat.POSITION_LOW_MASK)
                << LuminQuantizedVertexFormat.POSITION_SPLIT_BITS
                | (positionLo & LuminQuantizedVertexFormat.POSITION_LOW_MASK);
        int py = ((positionHi >>> 10) & LuminQuantizedVertexFormat.POSITION_LOW_MASK)
                << LuminQuantizedVertexFormat.POSITION_SPLIT_BITS
                | ((positionLo >>> 10) & LuminQuantizedVertexFormat.POSITION_LOW_MASK);
        int pz = ((positionHi >>> 20) & LuminQuantizedVertexFormat.POSITION_LOW_MASK)
                << LuminQuantizedVertexFormat.POSITION_SPLIT_BITS
                | ((positionLo >>> 20) & LuminQuantizedVertexFormat.POSITION_LOW_MASK);

        int r = color & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = (color >>> 16) & 0xFF;
        int a = (color >>> 24) & 0xFF;

        int quantizedU = texWord & LuminQuantizedVertexFormat.TEXCOORD_MASK;
        int uBiasBit = (texWord >>> LuminQuantizedVertexFormat.TEXCOORD_BITS) & 1;
        int quantizedV = (texWord >>> 16) & LuminQuantizedVertexFormat.TEXCOORD_MASK;
        int vBiasBit = (texWord >>> 31) & 1;

        int block = lightWord & 0xFF;
        int sky = (lightWord >>> 8) & 0xFF;
        int material = (lightWord >>> 16) & 0xFF;
        int sectionIndex = (lightWord >>> 24) & 0xFF;

        return new DecodedVertex(
                LuminQuantizedVertexFormat.dequantizePosition(px),
                LuminQuantizedVertexFormat.dequantizePosition(py),
                LuminQuantizedVertexFormat.dequantizePosition(pz),
                r, g, b, a,
                LuminQuantizedVertexFormat.dequantizeTexCoord(quantizedU),
                LuminQuantizedVertexFormat.dequantizeTexCoord(quantizedV),
                uBiasBit == 1, vBiasBit == 1,
                block, sky, material, sectionIndex);
    }

    /** 解码结果（不可变）。 */
    public record DecodedVertex(
            float x, float y, float z,
            int r, int g, int b, int a,
            float u, float v,
            boolean biasU, boolean biasV,
            int blockLight, int skyLight, int material, int sectionIndex) {
    }
}
