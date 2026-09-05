package io.github.openlumin.chunk.cull;

import io.github.openlumin.chunk.CancellationToken;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遮挡剔除遍历器（M3b）：从相机 section 出发，沿可见性图连通边广度优先扩展，
 * 产出「连通可达 ∩ 视锥可见」的 section 集。
 * <p>语义参照 Sodium OcclusionCuller（三级可见性、方向对连通、取消令牌），零拷码：</p>
 * <ul>
 *   <li>视锥测试失败不阻断扩展——相机身后的 section 仍是通往可见区域的通路；</li>
 *   <li>{@code occlusionEnabled=false} 时退化为距离受限的纯视锥遍历（对照模式）；</li>
 *   <li>取消令牌每 {@value #CANCEL_CHECK_INTERVAL} 次出队检查一次，
 *       取消后立即返回不完整结果（{@link LuminCullResult#isCancelled()}）。</li>
 * </ul>
 * <p>单线程遍历（Sodium 同款；多线程分区遍历与其 TODO 一样列为后续研究项）。
 * 非线程安全：同一实例禁止并发 cull；图实现须线程安全。</p>
 */
public final class LuminOcclusionCuller {

    private static final int CANCEL_CHECK_INTERVAL = 256;

    private final LuminSectionVisibilityGraph graph;

    public LuminOcclusionCuller(LuminSectionVisibilityGraph graph) {
        if (graph == null) {
            throw new NullPointerException("graph");
        }
        this.graph = graph;
    }

    public LuminCullResult cull(LuminCullRequest request, CancellationToken cancel) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (cancel == null) {
            throw new NullPointerException("cancel");
        }
        LuminSectionPos origin = request.cameraSection();
        if (!graph.exists(origin)) {
            throw new IllegalArgumentException("camera section does not exist in graph: " + origin);
        }

        List<LuminSectionPos> visible = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<LuminSectionPos> queue = new ArrayDeque<>();
        int maxDistanceSquared = request.maxDistanceSections() * request.maxDistanceSections();

        visited.add(origin.pack());
        queue.add(origin);
        int visitedCount = 0;
        boolean cancelled = false;
        int sinceCancelCheck = 0;

        while (!queue.isEmpty()) {
            if (cancel.isCancelled()) {
                cancelled = true;
                break;
            }
            LuminSectionPos pos = queue.poll();
            visitedCount++;
            if (pos.distanceSquaredTo(origin) > maxDistanceSquared) {
                continue;
            }
            if (request.frustum().isSectionVisible(pos)) {
                visible.add(pos);
            }
            for (LuminGraphDirection direction : LuminGraphDirection.values()) {
                if (request.occlusionEnabled()
                        && !LuminVisibilityEncoding.isTraversalOpen(graph.visibilityBits(pos), direction)) {
                    continue;
                }
                LuminSectionPos neighbor = pos.offset(direction);
                if (visited.add(neighbor.pack()) && graph.exists(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return new LuminCullResult(visible, visitedCount, cancelled);
    }
}
