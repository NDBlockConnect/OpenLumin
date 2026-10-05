package io.github.openlumin.shaderpack.frame;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 天象数学（WP-2 帧数据层，纯 CPU）：与 Iris 语义对齐的太阳/月亮角度与位置计算。
 *
 * <p>取证依据（Iris {@code CelestialUniforms}，原理复现、零代码移植）：</p>
 * <ul>
 *   <li>{@code sunAngle}（uniform，归一 [0,1)）= {@code (rawAngle + 90°) mod 360° / 360}；</li>
 *   <li>{@code isDay} = 太阳角 &lt; 180°；{@code shadowAngle} 白天取太阳、夜里取月亮；</li>
 *   <li>天体位置（视图空间）= {@code gbufferModelView · Ry(-90°) · Rz(sunPathRotation) ·
 *       Rx(rawAngle) · (0, y, 0, 1)}；太阳 y=+100、月亮 y=−100；</li>
 *   <li>{@code upPosition} = {@code gbufferModelView · Ry(-90°) · (0, 100, 0, 0)}（无后两步旋转）。</li>
 * </ul>
 */
public final class LuminCelestial {

    private LuminCelestial() {
    }

    /**
     * 原始角度 → OptiFine 角度约定（+90° 并归一 [0,360)）。
     * <p>Iris 用单次加减归一（其对原始角范围 [0,360) 足够）；本实现用真模以避免
     * 超出范围的输入产生负角。</p>
     */
    public static float angleDegrees(float rawAngle) {
        float c = (rawAngle + 90f) % 360f;
        return c < 0f ? c + 360f : c;
    }

    /** 归一 [0,1) 的天象角（Iris {@code sunAngle} uniform 的值）。 */
    public static float celestialAngle(float rawAngle) {
        return angleDegrees(rawAngle) / 360f;
    }

    /** 白天判定：太阳角（归一前）< 180°。 */
    public static boolean isDay(float rawSunAngle) {
        return angleDegrees(rawSunAngle) < 180f;
    }

    /** 阴影光角（白天太阳/夜里月亮），归一 [0,1)。 */
    public static float shadowAngle(float rawSunAngle, float rawMoonAngle) {
        return angleDegrees(isDay(rawSunAngle) ? rawSunAngle : rawMoonAngle) / 360f;
    }

    /**
     * 天象体位置（视图空间向量）。
     *
     * @param gbufferModelView 帧的 gbuffer 模型视图矩阵（不会被修改）
     * @param sunPathRotation  太阳路径旋转（度；Negative 表示南半球路径，见 shaderpack 指令）
     * @param rawAngle         原始天体角（度）
     * @param y                天体在 celestial 空间的基准高度（太阳 +100 / 月亮 −100）
     * @return {x, y, z}（w=1 变换后的前三维）
     */
    public static float[] bodyPosition(Matrix4f gbufferModelView, float sunPathRotation,
                                       float rawAngle, float y) {
        Matrix4f m = new Matrix4f(gbufferModelView)
                .rotateY((float) Math.toRadians(-90.0))
                .rotateZ((float) Math.toRadians(sunPathRotation))
                .rotateX((float) Math.toRadians(rawAngle));
        Vector4f v = m.transform(new Vector4f(0f, y, 0f, 1f));
        return new float[]{v.x, v.y, v.z};
    }

    /**
     * 世界"上"方向在视图空间的表示。
     *
     * @return {x, y, z}（w=0 方向向量变换）
     */
    public static float[] upPosition(Matrix4f gbufferModelView) {
        Matrix4f m = new Matrix4f(gbufferModelView)
                .rotateY((float) Math.toRadians(-90.0));
        Vector4f v = m.transform(new Vector4f(0f, 100f, 0f, 0f));
        return new float[]{v.x, v.y, v.z};
    }
}
