package io.github.openlumin.shaderpack.frame;

import org.joml.Matrix4f;

/**
 * 帧 uniform 计算输入（WP-2 帧数据层，纯 CPU）：由平台层在每帧采集。
 *
 * <p>时间类字段语义与 Iris 取证一致：{@code worldTime} 为 int（{@code dayTime % 24000}，
 * PER_TICK）；{@code frameCounter} 每帧 +1、720720 回绕；{@code frameTimeCounter} 为累计
 * 帧时（秒），≥3600 归零（Iris {@code SystemTimeUniforms.Timer}）。</p>
 *
 * @param rawSunAngle        太阳原始角（度，来自环境属性）
 * @param rawMoonAngle       月亮原始角（度）
 * @param sunPathRotation    sunPathRotation 指令（度）
 * @param gbufferModelView   帧的 gbuffer 模型视图矩阵（构造时深拷贝）
 * @param worldTime          世界时间 int（0..23999）
 * @param worldDay           世界天数 int
 * @param previousFrameCounter 上一帧的 frameCounter（本帧计算 +1 回绕）
 * @param lastFrameTimeSeconds 上一帧耗时（秒）
 * @param previousFrameTimeCounter 上一帧的 frameTimeCounter 累计值
 * @param rainStrength       雨强度 [0,1]
 * @param wetness            湿度 [0,1]
 * @param eyeX/eyeY/eyeZ     相机世界坐标（绝对，double 精度）
 * @param isEyeInWater       眼在水中（0=否，1=水，2=岩浆，3=粉雪——与透传输入一致）
 * @param nightVision        夜视强度 [0,1]
 * @param blindness         失明强度 [0,1]
 * @param darknessFactor    黑暗效果强度 [0,1]
 * @param eyePosX/eyePosY/eyePosZ 相机**实体**眼位世界坐标（double；与 cameraPosition
 *                          不同——第三人称时两者分离，Iris {@code eyePosition} 语义）
 */
public record LuminFrameUniformInputs(
        float rawSunAngle,
        float rawMoonAngle,
        float sunPathRotation,
        Matrix4f gbufferModelView,
        int worldTime,
        int worldDay,
        int previousFrameCounter,
        float lastFrameTimeSeconds,
        float previousFrameTimeCounter,
        float rainStrength,
        float wetness,
        double eyeX,
        double eyeY,
        double eyeZ,
        float isEyeInWater,
        float nightVision,
        float blindness,
        float darknessFactor,
        double eyePosX,
        double eyePosY,
        double eyePosZ) {

    public LuminFrameUniformInputs {
        if (gbufferModelView == null) {
            throw new NullPointerException("gbufferModelView");
        }
        gbufferModelView = new Matrix4f(gbufferModelView);
    }

    @Override
    public Matrix4f gbufferModelView() {
        return new Matrix4f(gbufferModelView);
    }
}
