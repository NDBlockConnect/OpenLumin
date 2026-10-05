package io.github.openlumin.shaderpack.frame;

/**
 * 单帧 uniform 计算快照（WP-2 帧数据层，纯 CPU）：OptiFine/Iris 内建 uniform 的运行时值。
 *
 * <p>平台层消费本结构把值写入管线 uniform（后续以 ShaderpackUniforms UBO 承载）。</p>
 */
public record LuminFrameUniforms(
        float sunAngle,
        float shadowAngle,
        float[] sunPosition,
        float[] moonPosition,
        float[] shadowLightPosition,
        float[] upPosition,
        int worldTime,
        int worldDay,
        int frameCounter,
        float frameTime,
        float frameTimeCounter,
        float rainStrength,
        float wetness,
        double[] cameraPosition,
        float eyeAltitude,
        float isEyeInWater,
        float nightVision,
        float blindness,
        float darknessFactor) {

    /** frameCounter 回绕周期（Iris 取证）。 */
    public static final int FRAME_COUNTER_WRAP = 720720;
    /** frameTimeCounter 归零阈值（秒，Iris 取证）。 */
    public static final float FRAME_TIME_COUNTER_WRAP_SECONDS = 3600f;

    public LuminFrameUniforms {
        sunPosition = sunPosition.clone();
        moonPosition = moonPosition.clone();
        shadowLightPosition = shadowLightPosition.clone();
        upPosition = upPosition.clone();
        cameraPosition = cameraPosition.clone();
    }

    @Override
    public float[] sunPosition() {
        return sunPosition.clone();
    }

    @Override
    public float[] moonPosition() {
        return moonPosition.clone();
    }

    @Override
    public float[] shadowLightPosition() {
        return shadowLightPosition.clone();
    }

    @Override
    public float[] upPosition() {
        return upPosition.clone();
    }

    @Override
    public double[] cameraPosition() {
        return cameraPosition.clone();
    }

    /**
     * 由帧输入计算全部 uniform 值（纯函数）。
     */
    public static LuminFrameUniforms compute(LuminFrameUniformInputs in) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        var modelView = in.gbufferModelView();
        float sunAngle = LuminCelestial.celestialAngle(in.rawSunAngle());
        float shadowAngle = LuminCelestial.shadowAngle(in.rawSunAngle(), in.rawMoonAngle());
        float[] sunPosition = LuminCelestial.bodyPosition(
                modelView, in.sunPathRotation(), in.rawSunAngle(), 100f);
        float[] moonPosition = LuminCelestial.bodyPosition(
                modelView, in.sunPathRotation(), in.rawMoonAngle(), -100f);
        float[] shadowLightPosition = LuminCelestial.isDay(in.rawSunAngle())
                ? sunPosition.clone() : moonPosition.clone();
        float[] upPosition = LuminCelestial.upPosition(modelView);

        int frameCounter = (in.previousFrameCounter() + 1) % FRAME_COUNTER_WRAP;
        float frameTimeCounter = in.previousFrameTimeCounter() + in.lastFrameTimeSeconds();
        if (frameTimeCounter >= FRAME_TIME_COUNTER_WRAP_SECONDS) {
            frameTimeCounter = 0f;
        }

        return new LuminFrameUniforms(
                sunAngle,
                shadowAngle,
                sunPosition,
                moonPosition,
                shadowLightPosition,
                upPosition,
                in.worldTime(),
                in.worldDay(),
                frameCounter,
                in.lastFrameTimeSeconds(),
                frameTimeCounter,
                in.rainStrength(),
                in.wetness(),
                new double[]{in.eyeX(), in.eyeY(), in.eyeZ()},
                (float) in.eyeY(),
                in.isEyeInWater(),
                in.nightVision(),
                in.blindness(),
                in.darknessFactor());
    }
}
