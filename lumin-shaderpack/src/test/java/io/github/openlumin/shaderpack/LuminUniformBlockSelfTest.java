package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.frame.LuminFrameUniformInputs;
import io.github.openlumin.shaderpack.frame.LuminFrameUniforms;
import io.github.openlumin.shaderpack.frame.LuminShaderpackUniformBlock;
import io.github.openlumin.shaderpack.frame.LuminUniformLayout;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WP-2 ShaderpackUniforms UBO 自测（聚合入口）：std140 偏移、序列化、GLSL 文本一致性。
 */
public final class LuminUniformBlockSelfTest {

    private static final float EPS = 1e-4f;
    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminFrameUniformsSelfTest.runAll();
        section("block: std140 offsets", LuminUniformBlockSelfTest::testOffsets);
        section("block: serialization round trip", LuminUniformBlockSelfTest::testSerialize);
        section("block: glsl declaration matches fields",
                LuminUniformBlockSelfTest::testGlslText);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack block] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack block] ALL SELF TESTS PASSED "
                + "(M1+M2+M6+preprocess+plan+translate+frame+block)");
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-36s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-36s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkClose(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > EPS) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    /** 手算 std140 偏移表（vec4×5 → 80，float×11 → 124，int×3 → 136，16 对齐 → 144）。 */
    private static void testOffsets() {
        LuminUniformLayout layout = LuminShaderpackUniformBlock.layout();
        check(layout.offset("SunPosition") == 0, "SunPosition at 0");
        check(layout.offset("MoonPosition") == 16, "MoonPosition at 16");
        check(layout.offset("ShadowLightPosition") == 32, "ShadowLightPosition at 32");
        check(layout.offset("UpPosition") == 48, "UpPosition at 48");
        check(layout.offset("EyePosition") == 64, "EyePosition at 64");
        check(layout.offset("SunAngle") == 80, "SunAngle at 80");
        check(layout.offset("FrameTimeCounter") == 92, "FrameTimeCounter at 92");
        check(layout.offset("DarknessFactor") == 120, "DarknessFactor at 120");
        check(layout.offset("WorldTime") == 124, "WorldTime at 124");
        check(layout.offset("WorldDay") == 128, "WorldDay at 128");
        check(layout.offset("FrameCounter") == 132, "FrameCounter at 132");
        check(LuminShaderpackUniformBlock.byteSize() == 144,
                "struct size 144 (16-aligned), got " + LuminShaderpackUniformBlock.byteSize());
    }

    private static LuminFrameUniforms sampleUniforms() {
        return LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                0f, 0f, 0f, new Matrix4f(), 6000, 4, 5, 0.016f, 100f,
                0.25f, 0.5f, 1.5, 65.5, -2.25, 2f, 0.75f, 0.125f, 0.0625f,
                11.5, 65.25, -2.75));
    }

    private static void testSerialize() {
        LuminFrameUniforms uniforms = sampleUniforms();
        ByteBuffer buffer = ByteBuffer.allocate(LuminShaderpackUniformBlock.byteSize())
                .order(ByteOrder.nativeOrder());
        LuminShaderpackUniformBlock.write(uniforms, buffer);

        LuminUniformLayout layout = LuminShaderpackUniformBlock.layout();
        int sunOffset = layout.offset("SunPosition");
        checkClose(buffer.getFloat(sunOffset), uniforms.sunPosition()[0], "sun x written");
        checkClose(buffer.getFloat(sunOffset + 4), uniforms.sunPosition()[1], "sun y written");
        checkClose(buffer.getFloat(sunOffset + 8), uniforms.sunPosition()[2], "sun z written");
        checkClose(buffer.getFloat(sunOffset + 12), 0f, "sun w reserved");

        int eyeOffset = layout.offset("EyePosition");
        checkClose(buffer.getFloat(eyeOffset), 11.5f, "player eye x written");
        checkClose(buffer.getFloat(eyeOffset + 4), 65.25f, "player eye y written");
        checkClose(buffer.getFloat(eyeOffset + 8), -2.75f, "player eye z written");

        checkClose(buffer.getFloat(layout.offset("SunAngle")), uniforms.sunAngle(),
                "sun angle written");
        checkClose(buffer.getFloat(layout.offset("RainStrength")), 0.25f,
                "rain strength written");
        checkClose(buffer.getFloat(layout.offset("IsEyeInWater")), 2f,
                "isEyeInWater written");
        check(buffer.getInt(layout.offset("WorldTime")) == 6000, "world time written");
        check(buffer.getInt(layout.offset("WorldDay")) == 4, "world day written");
        check(buffer.getInt(layout.offset("FrameCounter")) == 6, "frame counter written");
    }

    private static void testGlslText() {
        String glsl = LuminShaderpackUniformBlock.glslDeclaration();
        check(glsl.startsWith("layout(std140) uniform ShaderpackUniforms {"),
                "declaration header");
        check(glsl.contains("} shaderpackUniforms;"),
                "instance name present (members are accessed via instance)");
        check(glsl.contains("vec4 SunPosition;"), "vec4 member rendered");
        check(glsl.contains("float SunAngle;"), "float member rendered");
        check(glsl.contains("int FrameCounter;"), "int member rendered");
        for (var field : LuminShaderpackUniformBlock.FIELDS) {
            check(glsl.contains(field.type().glsl() + " " + field.name() + ";"),
                    "field rendered: " + field.name());
        }
    }

    private LuminUniformBlockSelfTest() {
    }
}
