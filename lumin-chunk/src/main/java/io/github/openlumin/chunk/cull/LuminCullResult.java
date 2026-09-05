package io.github.openlumin.chunk.cull;

import java.util.List;

/**
 * 剔除结果（不可变）：视锥可见且连通可达的 section 集 + 遍历统计。
 */
public final class LuminCullResult {

    private final List<LuminSectionPos> visibleSections;
    private final int visitedSections;
    private final boolean cancelled;

    LuminCullResult(List<LuminSectionPos> visibleSections, int visitedSections, boolean cancelled) {
        this.visibleSections = List.copyOf(visibleSections);
        this.visitedSections = visitedSections;
        this.cancelled = cancelled;
    }

    /** 可渲染 section（连通可达 + 视锥可见），无特定顺序。 */
    public List<LuminSectionPos> visibleSections() {
        return visibleSections;
    }

    /** 遍历过的 section 总数（含视锥外但作为通路被扩展的）。 */
    public int visitedSections() {
        return visitedSections;
    }

    /** true = 因取消令牌提前终止（结果不完整）。 */
    public boolean isCancelled() {
        return cancelled;
    }
}
