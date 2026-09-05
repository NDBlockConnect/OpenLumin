package io.github.openlumin.chunk.cull;

/**
 * 剔除请求（不可变）。
 *
 * @param cameraSection       相机所在 section（必须存在于图中，否则抛 IAE）
 * @param frustum             视锥测试
 * @param maxDistanceSections 最大遍历半径（section 坐标欧氏距离平方比较）
 * @param occlusionEnabled    false 时忽略可见性位，纯视锥遍历（调试/对照模式）
 */
public record LuminCullRequest(
        LuminSectionPos cameraSection,
        LuminFrustumTest frustum,
        int maxDistanceSections,
        boolean occlusionEnabled) {

    public LuminCullRequest {
        if (maxDistanceSections < 1) {
            throw new IllegalArgumentException("maxDistanceSections must be >= 1, got " + maxDistanceSections);
        }
        if (frustum == null) {
            throw new NullPointerException("frustum");
        }
    }
}
