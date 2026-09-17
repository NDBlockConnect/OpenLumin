package io.github.openlumin.shaderpack.graph;

import io.github.openlumin.shaderpack.LuminProgramGroup;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.ShaderpackIR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渲染图（WP-2 第 4 层产物，本设计的核心）：把 pass 集合表达为
 * <b>资源依赖图</b>，并给出拓扑序、竞争（hazard）与资源生命周期。
 *
 * <p>设计要点（见 docs/design/WP2_shaderpack_host.md §3.2）：</p>
 * <ul>
 *   <li><b>Iris 的固定时序是本结构的一个特例</b>：默认依赖集按组顺序铺设
 *       （见 {@link #defaultGroupOrder()}），因此同一 shaderpack 在默认依赖下必然得到
 *       与 Iris 等价的执行序（由 {@link #isCompatibleWithDefaultOrder()} 断言）；</li>
 *   <li>扩展包可声明额外依赖（超集能力），拓扑序随之变化；</li>
 *   <li>hazard 与 lifetime 同时服务 WP-3（资源别名与自动屏障），避免两套编排逻辑。</li>
 * </ul>
 *
 * <p>纯 CPU、不可变。环依赖（不可调度）由 {@link #hasCycle()} 报告。</p>
 */
public final class PassGraph {

    private final List<LuminPassNode> nodes;
    private final Map<Integer, Set<Integer>> successors;
    private final List<LuminHazard> hazards;
    private final List<Integer> topologicalOrder;
    private final boolean cyclic;
    private final Map<LuminResourceId, ResourceLifetime> lifetimes;

    /** 资源生命周期：首次写/读与末次读/写所在的 pass 序号。 */
    public record ResourceLifetime(int firstUse, int lastUse, int firstWrite, int lastWrite,
                                   boolean everWritten, boolean everRead) {
        /** 是否为"只读且从未写入"（可能是外部输入或声明遗漏）。 */
        public boolean isNeverWritten() {
            return !everWritten;
        }
    }

    private PassGraph(List<LuminPassNode> nodes, Map<Integer, Set<Integer>> successors,
                      List<LuminHazard> hazards, List<Integer> topologicalOrder,
                      boolean cyclic, Map<LuminResourceId, ResourceLifetime> lifetimes) {
        this.nodes = List.copyOf(nodes);
        this.successors = Map.copyOf(successors);
        this.hazards = List.copyOf(hazards);
        this.topologicalOrder = List.copyOf(topologicalOrder);
        this.cyclic = cyclic;
        this.lifetimes = Map.copyOf(lifetimes);
    }

    public List<LuminPassNode> nodes() {
        return nodes;
    }

    public List<LuminHazard> hazards() {
        return hazards;
    }

    /** 拓扑序（pass 序号序列）；存在环时仅含可调度部分。 */
    public List<Integer> topologicalOrder() {
        return topologicalOrder;
    }

    public boolean hasCycle() {
        return cyclic;
    }

    public Map<LuminResourceId, ResourceLifetime> lifetimes() {
        return lifetimes;
    }

    /** 按拓扑序取节点。 */
    public List<LuminPassNode> orderedNodes() {
        List<LuminPassNode> ordered = new ArrayList<>(topologicalOrder.size());
        for (int sequence : topologicalOrder) {
            ordered.add(node(sequence));
        }
        return ordered;
    }

    public LuminPassNode node(int sequence) {
        for (LuminPassNode candidate : nodes) {
            if (candidate.sequence() == sequence) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("no pass with sequence " + sequence);
    }

    /** 某节点的后继（依赖它的 pass）。 */
    public Set<Integer> successorsOf(int sequence) {
        return successors.getOrDefault(sequence, Set.of());
    }

    /**
     * 拓扑序是否与"默认组序（解析期 sequence 升序）"一致。
     * <p>用于回归门槛：装配默认依赖的 Iris 兼容包必须为 true。</p>
     */
    public boolean isCompatibleWithDefaultOrder() {
        if (cyclic) {
            return false;
        }
        for (int index = 0; index < topologicalOrder.size(); index++) {
            if (topologicalOrder.get(index) != index) {
                return false;
            }
        }
        return topologicalOrder.size() == nodes.size();
    }

    // ── 构建 ──

    /**
     * Iris 默认组序：帧内 pass 的固定顺序（见设计 §4 的默认依赖集）。
     * <p>同组内按 sequence 升序（合成组按编号）。</p>
     */
    public static List<LuminProgramGroup> defaultGroupOrder() {
        return List.of(
                LuminProgramGroup.SETUP,
                LuminProgramGroup.BEGIN,
                LuminProgramGroup.SHADOW,
                LuminProgramGroup.SHADOWCOMP,
                LuminProgramGroup.PREPARE,
                LuminProgramGroup.GBUFFERS,
                LuminProgramGroup.DEFERRED,
                LuminProgramGroup.COMPOSITE,
                LuminProgramGroup.FINAL);
    }

    /** 组的默认序位（越小越先）；未知组排在最后。 */
    static int groupRank(LuminProgramGroup group) {
        int index = defaultGroupOrder().indexOf(group);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * 由 IR 构建默认图：节点按 (组序, sequence) 排序，依赖取"最近的先前写者"。
     */
    public static PassGraph from(ShaderpackIR ir) {
        if (ir == null) {
            throw new NullPointerException("ir");
        }
        List<LuminPassNode> nodes = new ArrayList<>();
        List<LuminProgramId> programIds = new ArrayList<>(ir.programs().keySet());
        // 默认序：组序优先，其次编号（组内保持稳定）
        programIds.sort((a, b) -> {
            int byGroup = Integer.compare(groupRank(a.group()), groupRank(b.group()));
            if (byGroup != 0) {
                return byGroup;
            }
            int byIndex = Integer.compare(a.index(), b.index());
            if (byIndex != 0) {
                return byIndex;
            }
            return a.sourceBaseName().compareTo(b.sourceBaseName());
        });

        int sequence = 0;
        for (LuminProgramId id : programIds) {
            var source = ir.programs().get(id);
            if (source == null || !source.enabled()) {
                continue;
            }
            Set<LuminResourceId> writes = new LinkedHashSet<>();
            for (var target : source.drawTargets()) {
                writes.add(LuminResourceId.main(target));
            }
            Set<LuminResourceId> reads = inferReads(ir, id);
            LuminPassNode.ViewportSpec viewport = viewportOf(ir, id);
            boolean compute = id.group() == LuminProgramGroup.SETUP
                    || id.group() == LuminProgramGroup.SHADOWCOMP
                    || source.source(io.github.openlumin.shaderpack.LuminShaderKind.COMPUTE) != null;
            nodes.add(new LuminPassNode(id, sequence++, reads, writes, viewport, compute));
        }
        return build(nodes, true);
    }

    /** 读取集推断：深度拷贝目标 + 采样器引用（M2 简化：由指令与程序名推断常见读取）。 */
    private static Set<LuminResourceId> inferReads(ShaderpackIR ir, LuminProgramId id) {
        Set<LuminResourceId> reads = new LinkedHashSet<>();
        switch (id.group()) {
            case COMPOSITE, FINAL, DEFERRED, PREPARE, BEGIN -> {
                // 后处理链默认读取 colortex0 与深度
                reads.add(LuminResourceId.main(io.github.openlumin.shaderpack.LuminTargetId.color(0)));
                reads.add(LuminResourceId.main(io.github.openlumin.shaderpack.LuminTargetId.depth(0)));
            }
            case SHADOWCOMP -> reads.add(LuminResourceId.main(
                    io.github.openlumin.shaderpack.LuminTargetId.shadowDepth(0)));
            case GBUFFERS -> {
                // 地形/实体程序读取深度与阴影
                reads.add(LuminResourceId.main(io.github.openlumin.shaderpack.LuminTargetId.depth(0)));
                reads.add(LuminResourceId.main(io.github.openlumin.shaderpack.LuminTargetId.shadowDepth(0)));
            }
            default -> {
                // setup/shadow 无默认读取
            }
        }
        return reads;
    }

    private static LuminPassNode.ViewportSpec viewportOf(ShaderpackIR ir, LuminProgramId id) {
        var scale = ir.directives().viewportScale(id);
        if (scale == null) {
            return LuminPassNode.ViewportSpec.full();
        }
        return new LuminPassNode.ViewportSpec(scale.scale(), scale.offsetX(), scale.offsetY());
    }

    /**
     * 由节点序列构建图。
     *
     * @param implicitOrder true = 装配"默认时序依赖"（Iris 兼容）；false = 仅资源依赖（自由调度）
     */
    public static PassGraph build(List<LuminPassNode> nodes, boolean implicitOrder) {
        return build(nodes, implicitOrder, Map.of());
    }

    /**
     * 由节点序列构建图，并叠加**显式声明的额外依赖**（超集能力：扩展包可声明跨组依赖）。
     *
     * <p>注意：一个帧内的资源流依赖按构造必然前向（读自先前写者），因此环只能来自
     * 显式依赖与资源流相矛盾的情形——本重载正是用于表达并可检出这类冲突。</p>
     *
     * @param explicitDependencies 显式依赖：key 为生产者序号，value 为消费者序号集合
     */
    public static PassGraph build(List<LuminPassNode> nodes, boolean implicitOrder,
                                  Map<Integer, Set<Integer>> explicitDependencies) {
        Map<Integer, Set<Integer>> successors = new LinkedHashMap<>();
        for (LuminPassNode node : nodes) {
            successors.put(node.sequence(), new LinkedHashSet<>());
        }

        // 依赖边 1：资源流——真依赖（写→读/写）与反依赖（读→写）都必须成边，
        // 否则调度器可能把"覆盖仍被需要的资源"的 pass 提前（正确性错误）。
        Map<LuminResourceId, Integer> lastWriter = new HashMap<>();
        Map<LuminResourceId, Integer> lastReader = new HashMap<>();
        for (LuminPassNode node : nodes) {
            for (LuminResourceId resource : node.reads()) {
                Integer writer = lastWriter.get(resource);
                if (writer != null && writer != node.sequence()) {
                    successors.get(writer).add(node.sequence());
                }
            }
            for (LuminResourceId resource : node.writes()) {
                Integer writer = lastWriter.get(resource);
                if (writer != null && writer != node.sequence()) {
                    successors.get(writer).add(node.sequence());
                }
                // 反依赖：先前的读者必须先完成，写者才能覆盖
                Integer reader = lastReader.get(resource);
                if (reader != null && reader != node.sequence()) {
                    successors.get(reader).add(node.sequence());
                }
            }
            for (LuminResourceId resource : node.writes()) {
                lastWriter.put(resource, node.sequence());
            }
            for (LuminResourceId resource : node.reads()) {
                lastReader.put(resource, node.sequence());
            }
        }

        // 依赖边 2：隐式时序（Iris 兼容模式）——前一个 pass 先于后一个
        if (implicitOrder) {
            for (int index = 1; index < nodes.size(); index++) {
                successors.get(nodes.get(index - 1).sequence()).add(nodes.get(index).sequence());
            }
        }

        // 依赖边 3：显式声明的额外依赖（超集）
        for (Map.Entry<Integer, Set<Integer>> entry : explicitDependencies.entrySet()) {
            Set<Integer> out = successors.get(entry.getKey());
            if (out == null) {
                continue;
            }
            for (int consumer : entry.getValue()) {
                if (successors.containsKey(consumer) && consumer != entry.getKey()) {
                    out.add(consumer);
                }
            }
        }

        List<LuminHazard> hazards = computeHazards(nodes, successors);
        List<Integer> order = topologicalSort(nodes, successors);
        boolean cyclic = order.size() != nodes.size();
        Map<LuminResourceId, ResourceLifetime> lifetimes = computeLifetimes(nodes);
        return new PassGraph(nodes, successors, hazards, order, cyclic, lifetimes);
    }

    private static List<LuminHazard> computeHazards(List<LuminPassNode> nodes,
                                                    Map<Integer, Set<Integer>> successors) {
        List<LuminHazard> hazards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<LuminResourceId, Integer> lastWriter = new HashMap<>();
        Map<LuminResourceId, Integer> lastReader = new HashMap<>();
        for (LuminPassNode node : nodes) {
            for (LuminResourceId resource : node.writes()) {
                Integer reader = lastReader.get(resource);
                if (reader != null && reader != node.sequence()) {
                    addHazard(hazards, seen, new LuminHazard(resource,
                            LuminHazard.Kind.WRITE_AFTER_READ, reader, node.sequence()));
                }
                Integer writer = lastWriter.get(resource);
                if (writer != null && writer != node.sequence()) {
                    addHazard(hazards, seen, new LuminHazard(resource,
                            LuminHazard.Kind.WRITE_AFTER_WRITE, writer, node.sequence()));
                }
            }
            for (LuminResourceId resource : node.reads()) {
                Integer writer = lastWriter.get(resource);
                if (writer != null && writer != node.sequence()) {
                    addHazard(hazards, seen, new LuminHazard(resource,
                            LuminHazard.Kind.READ_AFTER_WRITE, writer, node.sequence()));
                }
            }
            for (LuminResourceId resource : node.writes()) {
                lastWriter.put(resource, node.sequence());
            }
            for (LuminResourceId resource : node.reads()) {
                lastReader.put(resource, node.sequence());
            }
        }
        return hazards;
    }

    private static void addHazard(List<LuminHazard> hazards, Set<String> seen, LuminHazard hazard) {
        if (seen.add(hazard.toString())) {
            hazards.add(hazard);
        }
    }

    private static List<Integer> topologicalSort(List<LuminPassNode> nodes,
                                                 Map<Integer, Set<Integer>> successors) {
        Map<Integer, Integer> inDegree = new LinkedHashMap<>();
        for (LuminPassNode node : nodes) {
            inDegree.putIfAbsent(node.sequence(), 0);
        }
        for (Map.Entry<Integer, Set<Integer>> entry : successors.entrySet()) {
            for (int successor : entry.getValue()) {
                inDegree.merge(successor, 1, Integer::sum);
            }
        }
        Deque<Integer> ready = new ArrayDeque<>();
        for (Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.addLast(entry.getKey());
            }
        }
        List<Integer> order = new ArrayList<>(nodes.size());
        // 稳定性：同层按序号升序，保证与默认序一致
        List<Integer> readyList = new ArrayList<>(ready);
        readyList.sort(Integer::compareTo);
        ready = new ArrayDeque<>(readyList);
        while (!ready.isEmpty()) {
            int current = ready.pollFirst();
            order.add(current);
            List<Integer> newlyReady = new ArrayList<>();
            for (int successor : successors.getOrDefault(current, Set.of())) {
                int remaining = inDegree.merge(successor, -1, Integer::sum);
                if (remaining == 0) {
                    newlyReady.add(successor);
                }
            }
            newlyReady.sort(Integer::compareTo);
            for (int sequence : newlyReady) {
                ready.addLast(sequence);
            }
        }
        return order;
    }

    private static Map<LuminResourceId, ResourceLifetime> computeLifetimes(List<LuminPassNode> nodes) {
        // bounds: [firstUse, lastUse, firstWrite, lastWrite]
        Map<LuminResourceId, int[]> bounds = new LinkedHashMap<>();
        for (LuminPassNode node : nodes) {
            for (LuminResourceId resource : node.reads()) {
                int[] bound = bounds.computeIfAbsent(resource, r -> new int[]{-1, -1, -1, -1});
                bound[0] = bound[0] < 0 ? node.sequence() : Math.min(bound[0], node.sequence());
                bound[1] = Math.max(bound[1], node.sequence());
            }
            for (LuminResourceId resource : node.writes()) {
                int[] bound = bounds.computeIfAbsent(resource, r -> new int[]{-1, -1, -1, -1});
                bound[0] = bound[0] < 0 ? node.sequence() : Math.min(bound[0], node.sequence());
                bound[1] = Math.max(bound[1], node.sequence());
                bound[2] = bound[2] < 0 ? node.sequence() : Math.min(bound[2], node.sequence());
                bound[3] = Math.max(bound[3], node.sequence());
            }
        }
        Map<LuminResourceId, ResourceLifetime> lifetimes = new LinkedHashMap<>();
        for (Map.Entry<LuminResourceId, int[]> entry : bounds.entrySet()) {
            int[] bound = entry.getValue();
            lifetimes.put(entry.getKey(), new ResourceLifetime(
                    bound[0], bound[1], bound[2], bound[3], bound[2] >= 0, bound[0] >= 0));
        }
        return lifetimes;
    }
}
