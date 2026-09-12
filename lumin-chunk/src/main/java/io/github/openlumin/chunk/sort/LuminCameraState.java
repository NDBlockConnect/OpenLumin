package io.github.openlumin.chunk.sort;

/**
 * 相机状态（相对 section 坐标系的视点位置），用于 BSP 遍历序判定。
 */
public record LuminCameraState(
        /** Section 坐标（1 步 = 16 方块），确定相机所在 section。 */
        int sectionX, int sectionY, int sectionZ,
        /** 相机相对 section 内部的世界坐标（0..15），用于平面侧的符号判定。 */
        float localX, float localY, float localZ) {
}