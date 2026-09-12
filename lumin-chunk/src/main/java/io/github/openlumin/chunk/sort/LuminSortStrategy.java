package io.github.openlumin.chunk.sort;

/**
 * @see LuminTranslucentSorter
 */
public enum LuminSortStrategy {
    /** 无需排序（无半透明几何体或已预排序）。 */
    NONE,
    /** 静态拓扑序：按预定义排列（场景无动态遮挡关系时稳定且零帧开销）。 */
    STATIC_TOPO,
    /** 每相机变化重建 BSP 树（动态遮挡关系，如玩家移动或方块变更触发重排）。 */
    DYNAMIC_BSP
}