package io.github.openlumin.chunk.sort;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态拓扑序排序：按构造时确定的排列（由消费方在几何体不变时预定义），
 * 每帧零开销（不重建树、不评估相机距离）。适用场景：法线轴对齐的
 * 静态半透明面（如水面连续层），拓扑序与相机方向兼容。
 */
public final class StaticTopoSorter implements LuminTranslucentSorter {

    private final int[] order;

    /**
     * @param order 排列：order[i] = 输入列表中的原始位置（重排成新列表后，第 i 位取自 quads.get(order[i])）
     */
    public StaticTopoSorter(int[] order) {
        this.order = order.clone();
        for (int index : this.order) {
            if (index < 0) {
                throw new IllegalArgumentException("order contains negative index");
            }
        }
    }

    @Override
    public LuminSortStrategy strategy() {
        return LuminSortStrategy.STATIC_TOPO;
    }

    @Override
    public List<LuminTranslucentQuad> sort(LuminCameraState camera, List<LuminTranslucentQuad> quads) {
        List<LuminTranslucentQuad> result = new ArrayList<>(quads.size());
        for (int index : order) {
            result.add(quads.get(index));
        }
        return result;
    }

    @Override
    public void close() {
    }
}