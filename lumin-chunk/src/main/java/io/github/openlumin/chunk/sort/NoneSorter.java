package io.github.openlumin.chunk.sort;

import java.util.ArrayList;
import java.util.List;

/**
 * 不排序（原序直出）。适用无半透明几何体的 section（索引缓冲无需重建）。
 */
public final class NoneSorter implements LuminTranslucentSorter {

    @Override
    public LuminSortStrategy strategy() {
        return LuminSortStrategy.NONE;
    }

    @Override
    public List<LuminTranslucentQuad> sort(LuminCameraState camera, List<LuminTranslucentQuad> quads) {
        return new ArrayList<>(quads);
    }

    @Override
    public void close() {
    }
}