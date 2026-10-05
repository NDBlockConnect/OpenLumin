package io.github.openlumin.shaderpack.plan;

import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.graph.LuminHazard;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 帧规划器（WP-2 M4，纯 CPU）：{@link PassGraph} × {@link LuminResourcePlan}
 * → {@link LuminFramePlan}。
 *
 * <p>乒乓解析算法：</p>
 * <ol>
 *   <li>帧首：清屏批次先行（两副本状态一致，故当前副本可直接取 MAIN）；</li>
 *   <li>按拓扑序遍历 pass：<b>读</b> = 当前副本；<b>写</b> = 另一副本，写后翻转为新当前；</li>
 *   <li>非乒乓目标（depthtex/shadowtex/shadowcolor）恒绑定 MAIN；</li>
 *   <li>每个 pass 携带其消费侧竞争（{@code hazardsBefore}，消费者 = 本 pass），
 *       供执行层插入屏障（GL 内存屏障 / Vulkan image barrier）。</li>
 * </ol>
 */
public final class LuminFramePlanner {

    private LuminFramePlanner() {
    }

    public static LuminFramePlan plan(PassGraph graph, LuminResourcePlan resources) {
        if (graph == null) {
            throw new NullPointerException("graph");
        }
        if (resources == null) {
            throw new NullPointerException("resources");
        }
        List<LuminFramePlan.Step> steps = new ArrayList<>();
        for (LuminResourcePlan.ClearBatch batch : resources.clearBatches()) {
            steps.add(new LuminFramePlan.ClearStep(batch));
        }

        Map<LuminTargetId, LuminResourceId.Copy> current = new HashMap<>();
        for (LuminPassNode node : graph.orderedNodes()) {
            Map<LuminTargetId, LuminResourceId.Copy> reads = new LinkedHashMap<>();
            for (LuminResourceId resource : node.reads()) {
                LuminTargetId target = resource.target();
                reads.put(target, isPingPong(resources, target)
                        ? current.getOrDefault(target, LuminResourceId.Copy.MAIN)
                        : LuminResourceId.Copy.MAIN);
            }
            Map<LuminTargetId, LuminResourceId.Copy> writes = new LinkedHashMap<>();
            for (LuminResourceId resource : node.writes()) {
                LuminTargetId target = resource.target();
                if (isPingPong(resources, target)) {
                    LuminResourceId.Copy writeCopy =
                            flip(current.getOrDefault(target, LuminResourceId.Copy.MAIN));
                    writes.put(target, writeCopy);
                    current.put(target, writeCopy);
                } else {
                    writes.put(target, LuminResourceId.Copy.MAIN);
                }
            }
            List<LuminHazard> hazardsBefore = graph.hazards().stream()
                    .filter(hazard -> hazard.consumer() == node.sequence())
                    .toList();
            steps.add(new LuminFramePlan.PassStep(node.program(), node.sequence(),
                    reads, writes, node.viewport(), node.compute(), hazardsBefore));
        }
        return new LuminFramePlan(steps);
    }

    private static boolean isPingPong(LuminResourcePlan resources, LuminTargetId target) {
        LuminResourceAllocation allocation = resources.allocation(target);
        return allocation != null && allocation.pingPong();
    }

    private static LuminResourceId.Copy flip(LuminResourceId.Copy copy) {
        return copy == LuminResourceId.Copy.MAIN
                ? LuminResourceId.Copy.ALT
                : LuminResourceId.Copy.MAIN;
    }
}
