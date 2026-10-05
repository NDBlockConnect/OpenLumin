package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.frame.LuminCelestial;
import io.github.openlumin.shaderpack.frame.LuminFrameUniformInputs;
import io.github.openlumin.shaderpack.frame.LuminFrameUniforms;
import org.joml.Matrix4f;

/**
 * WP-2 帧 uniform 自测（聚合入口）：天象角/位置约定、阴影光选择、计数器语义、透传字段。
 * 全部纯 CPU；矩阵约定与 Iris 取证对齐。
 */
public final class LuminFrameUniformsSelfTest {

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
        failures = LuminTranslatorSelfTest.runAll();
        section("frame: angle semantics", LuminFrameUniformsSelfTest::testAngles);
        section("frame: position convention", LuminFrameUniformsSelfTest::testPositionConvention);
        section("frame: modelview applied", LuminFrameUniformsSelfTest::testModelViewApplied);
        section("frame: shadow light selection", LuminFrameUniformsSelfTest::testShadowLight);
        section("frame: counters", LuminFrameUniformsSelfTest::testCounters);
        section("frame: passthrough fields", LuminFrameUniformsSelfTest::testPassthrough);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack frame] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack frame] ALL SELF TESTS PASSED "
                + "(M1+M2+M6+preprocess+plan+translate+frame)");
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

    private static void checkVec(float[] actual, float[] expected, String message) {
        for (int i = 0; i < expected.length; i++) {
            if (Math.abs(actual[i] - expected[i]) > EPS) {
                throw new AssertionError(message + ": expected [" + expected[0] + ", "
                        + expected[1] + ", " + expected[2] + "] got [" + actual[0] + ", "
                        + actual[1] + ", " + actual[2] + "]");
            }
        }
    }

    private static LuminFrameUniformInputs inputs(Matrix4f modelView,
                                                  float rawSun, float rawMoon) {
        return new LuminFrameUniformInputs(rawSun, rawMoon, 0f, modelView,
                6000, 3, 5, 0.016f, 100f, 0f, 0f,
                1.0, 2.0, 3.0, 0f, 0f, 0f, 0f);
    }

    private static void testAngles() {
        checkClose(LuminCelestial.angleDegrees(0f), 90f, "raw 0 -> 90");
        checkClose(LuminCelestial.angleDegrees(-90f), 0f, "raw -90 wraps to 0");
        checkClose(LuminCelestial.angleDegrees(270f), 0f, "raw 270 wraps to 0");
        checkClose(LuminCelestial.celestialAngle(0f), 0.25f, "normalized angle");
        check(LuminCelestial.isDay(0f), "raw 0 (angle 90) is day");
        check(!LuminCelestial.isDay(180f), "raw 180 (angle 270) is night");
        checkClose(LuminCelestial.shadowAngle(0f, 0f), 0.25f, "day shadow angle uses sun");
        checkClose(LuminCelestial.shadowAngle(180f, 0f), 0.25f,
                "night shadow angle uses moon");
    }

    /**
     * 身份 MV、无路径旋转时的解析解：
     * rawAngle=90 → Rx(90)·(0,100,0,1)=(0,0,100)，再经 Ry(-90) → (-100,0,0)。
     */
    private static void testPositionConvention() {
        Matrix4f identity = new Matrix4f();
        checkVec(LuminCelestial.bodyPosition(identity, 0f, 90f, 100f),
                new float[]{-100f, 0f, 0f}, "sun at raw 90 maps +Z to -X under Ry(-90)");
        checkVec(LuminCelestial.bodyPosition(identity, 0f, 0f, 100f),
                new float[]{0f, 100f, 0f}, "sun at raw 0 points up");
        checkVec(LuminCelestial.bodyPosition(identity, 0f, 0f, -100f),
                new float[]{0f, -100f, 0f}, "moon uses negative base height");
        checkVec(LuminCelestial.upPosition(identity),
                new float[]{0f, 100f, 0f}, "up stays up under Ry(-90)");
    }

    /** MV 旋转必须作用到结果上：Ry(+90) 把基准 (-100,0,0) 转到 (0,0,100)。 */
    private static void testModelViewApplied() {
        Matrix4f modelView = new Matrix4f().rotateY((float) Math.toRadians(90.0));
        checkVec(LuminCelestial.bodyPosition(modelView, 0f, 90f, 100f),
                new float[]{0f, 0f, 100f}, "model view rotation applies");
    }

    private static void testShadowLight() {
        Matrix4f identity = new Matrix4f();
        LuminFrameUniforms day = LuminFrameUniforms.compute(inputs(identity, 0f, 180f));
        checkVec(day.shadowLightPosition(), day.sunPosition(),
                "day shadow light equals sun position");
        LuminFrameUniforms night = LuminFrameUniforms.compute(inputs(identity, 180f, 0f));
        checkVec(night.shadowLightPosition(), night.moonPosition(),
                "night shadow light equals moon position");
        checkVec(night.shadowLightPosition(), new float[]{0f, -100f, 0f},
                "moon at raw 0 points down");
    }

    private static void testCounters() {
        Matrix4f identity = new Matrix4f();
        LuminFrameUniforms norm = LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                0f, 0f, 0f, identity, 6000, 3, 5, 0.016f, 100f,
                0f, 0f, 0.0, 64.0, 0.0, 0f, 0f, 0f, 0f));
        check(norm.frameCounter() == 6, "frame counter increments");
        checkClose(norm.frameTimeCounter(), 100.016f, "frame time counter accumulates");
        checkClose(norm.frameTime(), 0.016f, "frame time passthrough");

        LuminFrameUniforms wrapped = LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                0f, 0f, 0f, identity, 0, 0,
                LuminFrameUniforms.FRAME_COUNTER_WRAP - 1, 0.016f, 10f,
                0f, 0f, 0.0, 0.0, 0.0, 0f, 0f, 0f, 0f));
        check(wrapped.frameCounter() == 0, "frame counter wraps at 720720");

        LuminFrameUniforms timeWrapped = LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                0f, 0f, 0f, identity, 0, 0, 0, 0.6f, 3599.5f,
                0f, 0f, 0.0, 0.0, 0.0, 0f, 0f, 0f, 0f));
        checkClose(timeWrapped.frameTimeCounter(), 0f,
                "frame time counter resets at 3600s");
    }

    private static void testPassthrough() {
        Matrix4f identity = new Matrix4f();
        LuminFrameUniforms u = LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                0f, 0f, 0f, identity, 12345, 7, 0, 0.02f, 0f,
                0.4f, 0.6f, 12.5, 64.25, -3.5, 1f, 0.5f, 0.25f, 0.1f));
        check(u.worldTime() == 12345 && u.worldDay() == 7, "world time passthrough");
        checkClose(u.rainStrength(), 0.4f, "rain passthrough");
        checkClose(u.wetness(), 0.6f, "wetness passthrough");
        check(u.cameraPosition()[0] == 12.5 && u.cameraPosition()[1] == 64.25
                        && u.cameraPosition()[2] == -3.5,
                "camera position passthrough (double)");
        checkClose(u.eyeAltitude(), 64.25f, "eye altitude is camera Y");
        checkClose(u.isEyeInWater(), 1f, "isEyeInWater passthrough");
        checkClose(u.nightVision(), 0.5f, "night vision passthrough");
        checkClose(u.blindness(), 0.25f, "blindness passthrough");
        checkClose(u.darknessFactor(), 0.1f, "darkness passthrough");
    }

    private LuminFrameUniformsSelfTest() {
    }
}
