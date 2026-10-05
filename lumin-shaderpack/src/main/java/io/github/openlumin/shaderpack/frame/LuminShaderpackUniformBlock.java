package io.github.openlumin.shaderpack.frame;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 引擎侧 ShaderpackUniforms UBO（WP-2 帧数据层）：
 * 承载 OptiFine/Iris 内建 uniform 的运行时值，供翻译层把包内引用映射到本块成员。
 *
 * <p>布局为 std140（{@link LuminUniformLayout} 推导，GLSL 文本同源生成）；
 * 位置类成员用 vec4 承载 vec3 语义（w=0），规避 std140 vec3 打包歧义。</p>
 *
 * <p>序列化约定：调用方提供**本机字节序**（{@code ByteOrder.nativeOrder()}）的缓冲区；
 * 偏移与 {@link #layout()} 一致。</p>
 */
public final class LuminShaderpackUniformBlock {

    /** GLSL 块名（翻译层映射与管线声明共用）。 */
    public static final String BLOCK_NAME = "ShaderpackUniforms";

    /** 成员表（顺序即 GLSL 声明顺序，偏移由 std140 推导）。 */
    public static final List<LuminUniformField> FIELDS = List.of(
            new LuminUniformField("SunPosition", LuminUniformField.Type.VEC4),
            new LuminUniformField("MoonPosition", LuminUniformField.Type.VEC4),
            new LuminUniformField("ShadowLightPosition", LuminUniformField.Type.VEC4),
            new LuminUniformField("UpPosition", LuminUniformField.Type.VEC4),
            new LuminUniformField("EyePosition", LuminUniformField.Type.VEC4),
            new LuminUniformField("SunAngle", LuminUniformField.Type.FLOAT),
            new LuminUniformField("ShadowAngle", LuminUniformField.Type.FLOAT),
            new LuminUniformField("FrameTime", LuminUniformField.Type.FLOAT),
            new LuminUniformField("FrameTimeCounter", LuminUniformField.Type.FLOAT),
            new LuminUniformField("RainStrength", LuminUniformField.Type.FLOAT),
            new LuminUniformField("Wetness", LuminUniformField.Type.FLOAT),
            new LuminUniformField("EyeAltitude", LuminUniformField.Type.FLOAT),
            new LuminUniformField("IsEyeInWater", LuminUniformField.Type.FLOAT),
            new LuminUniformField("NightVision", LuminUniformField.Type.FLOAT),
            new LuminUniformField("Blindness", LuminUniformField.Type.FLOAT),
            new LuminUniformField("DarknessFactor", LuminUniformField.Type.FLOAT),
            new LuminUniformField("WorldTime", LuminUniformField.Type.INT),
            new LuminUniformField("WorldDay", LuminUniformField.Type.INT),
            new LuminUniformField("FrameCounter", LuminUniformField.Type.INT));

    private static final LuminUniformLayout LAYOUT = LuminUniformLayout.of(FIELDS);

    private LuminShaderpackUniformBlock() {
    }

    public static LuminUniformLayout layout() {
        return LAYOUT;
    }

    /** 块字节尺寸（std140，16 对齐）。 */
    public static int byteSize() {
        return LAYOUT.size();
    }

    /** GLSL 块声明文本（供翻译层注入）。 */
    public static String glslDeclaration() {
        return LAYOUT.glslDeclaration(BLOCK_NAME);
    }

    /**
     * 把一帧的 uniform 值写入缓冲区（绝对写入，不改变 position）。
     *
     * @param uniforms 帧 uniform 快照
     * @param buffer   本机字节序、容量 ≥ {@link #byteSize()} 的缓冲区
     */
    public static void write(LuminFrameUniforms uniforms, ByteBuffer buffer) {
        if (uniforms == null) {
            throw new NullPointerException("uniforms");
        }
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (buffer.capacity() < byteSize()) {
            throw new IllegalArgumentException(
                    "buffer capacity " + buffer.capacity() + " < " + byteSize());
        }
        writeVec3(buffer, "SunPosition", uniforms.sunPosition());
        writeVec3(buffer, "MoonPosition", uniforms.moonPosition());
        writeVec3(buffer, "ShadowLightPosition", uniforms.shadowLightPosition());
        writeVec3(buffer, "UpPosition", uniforms.upPosition());
        writeVec3(buffer, "EyePosition", toFloats(uniforms.playerEyePosition()));
        putFloat(buffer, "SunAngle", uniforms.sunAngle());
        putFloat(buffer, "ShadowAngle", uniforms.shadowAngle());
        putFloat(buffer, "FrameTime", uniforms.frameTime());
        putFloat(buffer, "FrameTimeCounter", uniforms.frameTimeCounter());
        putFloat(buffer, "RainStrength", uniforms.rainStrength());
        putFloat(buffer, "Wetness", uniforms.wetness());
        putFloat(buffer, "EyeAltitude", uniforms.eyeAltitude());
        putFloat(buffer, "IsEyeInWater", uniforms.isEyeInWater());
        putFloat(buffer, "NightVision", uniforms.nightVision());
        putFloat(buffer, "Blindness", uniforms.blindness());
        putFloat(buffer, "DarknessFactor", uniforms.darknessFactor());
        putInt(buffer, "WorldTime", uniforms.worldTime());
        putInt(buffer, "WorldDay", uniforms.worldDay());
        putInt(buffer, "FrameCounter", uniforms.frameCounter());
    }

    private static void writeVec3(ByteBuffer buffer, String field, float[] value) {
        int offset = LAYOUT.offset(field);
        buffer.putFloat(offset, value[0]);
        buffer.putFloat(offset + 4, value[1]);
        buffer.putFloat(offset + 8, value[2]);
        buffer.putFloat(offset + 12, 0f);
    }

    private static void putFloat(ByteBuffer buffer, String field, float value) {
        buffer.putFloat(LAYOUT.offset(field), value);
    }

    private static void putInt(ByteBuffer buffer, String field, int value) {
        buffer.putInt(LAYOUT.offset(field), value);
    }

    private static float[] toFloats(double[] value) {
        return new float[]{(float) value[0], (float) value[1], (float) value[2]};
    }
}
