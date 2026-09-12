package io.github.openlumin.chunk.sort;

/**
 * 单个半透明四边形（M3c 排序的基本单元）：4 个顶点索引 + 3D 角点坐标 + 面平面方程。
 * <p>角点坐标用于 BSP 分区平面分类——消费方从 section 网格数据计算（顶点位置 + section 偏移）；
 * 面平面方程 (nx, ny, nz, d) 满足 {@code nx*x + ny*y + nz*z + d = 0}，
 * 由消费方预计算并归一化。</p>
 */
public record LuminTranslucentQuad(
        int vertexIndex0,
        int vertexIndex1,
        int vertexIndex2,
        int vertexIndex3,
        float corner0X, float corner0Y, float corner0Z,
        float corner1X, float corner1Y, float corner1Z,
        float corner2X, float corner2Y, float corner2Z,
        float corner3X, float corner3Y, float corner3Z,
        float planeNX, float planeNY, float planeNZ, float planeD) {
}