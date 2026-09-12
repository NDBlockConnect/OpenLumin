package io.github.openlumin.chunk.vertex;

/**
 * 量化顶点格式常量与编解码工具（原理参照：紧凑量化顶点，**20 字节/顶点**）。
 *
 * <p>布局（每顶点 20 字节，5 个 32 位字，小端）：</p>
 * <pre>
 *   word0 (4B): 位置高位 = x[10 位] | y[10 位] &lt;&lt; 10 | z[10 位] &lt;&lt; 20
 *   word1 (4B): 位置低位 = x[10 位] | y[10 位] &lt;&lt; 10 | z[10 位] &lt;&lt; 20
 *   word2 (4B): 颜色 RGBA8（R | G&lt;&lt;8 | B&lt;&lt;16 | A&lt;&lt;24）
 *   word3 (4B): 纹理 = u[15 位] | biasU[1 位] &lt;&lt; 15 | v[15 位] &lt;&lt; 16 | biasV[1 位] &lt;&lt; 31
 *   word4 (4B): 光照 = block[8 位] | sky[8 位] &lt;&lt; 8 | material[8 位] &lt;&lt; 16 | sectionIndex[8 位] &lt;&lt; 24
 * </pre>
 *
 * <p>位置：section 局部坐标按 {@code (8 + v) / 32} 归一化到 [0,1]，再量化 20 位/轴
 * （精度 ≈ 32/2^20 ≈ 3.05e-5 方块）。x/y/z 各 20 位共 60 位，拆两个 32 位字
 * （低 10 位入 word1，高 10 位入 word0）。</p>
 *
 * <p>纹理：抗图集渗色采用"向 quad 的 UV 质心收缩一个可寻址单位"，
 * 只存该收缩的<b>方向符号</b>（1 位），GPU 侧重建 epsilon——省 7 位/分量。</p>
 *
 * <p>光照：sky/block 各钳制 [{@value #LIGHT_MIN}, {@value #LIGHT_MAX}]（避免纯黑/纯白条带）。</p>
 */
public final class LuminQuantizedVertexFormat {

    /** 每顶点字节数。 */
    public static final int STRIDE_BYTES = 20;
    /** 每字字节数。 */
    public static final int WORD_BYTES = 4;
    /** 每字数量。 */
    public static final int WORD_COUNT = 5;

    /** 位置量化位宽（每轴）。 */
    public static final int POSITION_BITS = 20;
    public static final int POSITION_SPLIT_BITS = 10;
    public static final int POSITION_MASK = (1 << POSITION_BITS) - 1;
    public static final int POSITION_LOW_MASK = (1 << POSITION_SPLIT_BITS) - 1;

    /** 位置归一化：section 局部坐标范围。 */
    public static final float MODEL_ORIGIN = 8.0f;
    public static final float MODEL_RANGE = 32.0f;
    /** 着色器反量化尺度：32 / 2^20。 */
    public static final float VERTEX_SCALE = MODEL_RANGE / (1 << POSITION_BITS);
    /** 着色器反量化偏移：-8。 */
    public static final float VERTEX_OFFSET = -MODEL_ORIGIN;

    /** 纹理量化位宽与尺度。 */
    public static final int TEXCOORD_BITS = 15;
    public static final int TEXCOORD_MASK = (1 << TEXCOORD_BITS) - 1;
    public static final float TEXCOORD_SCALE = (float) (1 << TEXCOORD_BITS);

    /** 光照钳制范围。 */
    public static final int LIGHT_MIN = 8;
    public static final int LIGHT_MAX = 248;

    private LuminQuantizedVertexFormat() {
    }

    /** 量化单轴位置（输入 section 局部坐标，输出 20 位无符号）。 */
    public static int quantizePosition(float value) {
        float normalized = (MODEL_ORIGIN + value) / MODEL_RANGE;
        if (Float.isNaN(normalized)) {
            return 0;
        }
        int quantized = (int) (normalized * (1 << POSITION_BITS));
        if (quantized < 0) {
            return 0;
        }
        return Math.min(quantized, POSITION_MASK);
    }

    /** 反量化单轴位置（20 位 → section 局部坐标）。 */
    public static float dequantizePosition(int quantized) {
        return (quantized & POSITION_MASK) * VERTEX_SCALE + VERTEX_OFFSET;
    }

    /** 量化纹理坐标（经质检定，输出 15 位无符号）。 */
    public static int quantizeTexCoord(float value) {
        int quantized = Math.round(value * TEXCOORD_SCALE);
        if (quantized < 0) {
            return 0;
        }
        return Math.min(quantized, TEXCOORD_MASK);
    }

    /** 反量化纹理坐标（15 位 → [0,1]）。 */
    public static float dequantizeTexCoord(int quantized) {
        return (quantized & TEXCOORD_MASK) / TEXCOORD_SCALE;
    }

    /** 光照钳制到 [{@value #LIGHT_MIN}, {@value #LIGHT_MAX}]。 */
    public static int clampLight(int value) {
        return Math.max(LIGHT_MIN, Math.min(LIGHT_MAX, value));
    }

    /** 计算 quad 的 UV 质心（用于渗色偏置方向）。 */
    public static float[] texCoordCentroid(float[] u, float[] v) {
        if (u.length != 4 || v.length != 4) {
            throw new IllegalArgumentException("quad must have exactly 4 texcoords");
        }
        float sumU = u[0] + u[1] + u[2] + u[3];
        float sumV = v[0] + v[1] + v[2] + v[3];
        return new float[]{sumU * 0.25f, sumV * 0.25f};
    }

    /**
     * 渗色偏置符号：UV 小于质心取 +1（向质心收缩），否则取 -1。
     *
     * @return 1 或 0（位值语义：1 = 正方向偏置）
     */
    public static int texCoordBiasBit(float value, float centroid) {
        return value < centroid ? 1 : 0;
    }
}
