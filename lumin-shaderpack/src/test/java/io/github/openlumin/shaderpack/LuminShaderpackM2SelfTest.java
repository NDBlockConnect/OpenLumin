package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.graph.LuminHazard;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.parse.IncludeGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP-2 M2 自测：PassGraph（依赖推导 / 竞争分析 / 拓扑序 / 生命周期 / Iris 默认序等价性）。
 * 全部使用合成夹具。
 */
public final class LuminShaderpackM2SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminShaderpackM1SelfTest.runAll();
        section("default order equivalence", LuminShaderpackM2SelfTest::testDefaultOrder);
        section("resource dependency edges", LuminShaderpackM2SelfTest::testDependencyEdges);
        section("hazard classification", LuminShaderpackM2SelfTest::testHazards);
        section("resource lifetimes", LuminShaderpackM2SelfTest::testLifetimes);
        section("cycle detection", LuminShaderpackM2SelfTest::testCycle);
        section("explicit order vs free order", LuminShaderpackM2SelfTest::testOrderingModes);
        section("viewport propagation", LuminShaderpackM2SelfTest::testViewport);
        section("self feedback detection", LuminShaderpackM2SelfTest::testSelfFeedback);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack M2] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack M2] ALL SELF TESTS PASSED (M1 + M2)");
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-32s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-32s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static IncludeGraph.SourceProvider provider(Map<String, String> files) {
        return path -> files.get(IncludeGraph.normalize(path));
    }

    /** 一份含 gbuffers → deferred → composite → final 的最小合成包。 */
    private static Map<String, String> typicalPack() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", """
                scale.composite1=0.5
                scale.deferred=1.0
                clouds=true
                """);
        files.put("shaders/gbuffers_terrain.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:01 */\nvoid main(){}\n");
        files.put("shaders/deferred.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/final.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        return files;
    }

    private static PassGraph graphOf(Map<String, String> files) {
        ShaderpackIR ir = new ShaderpackLoader("fixture", provider(files))
                .load(new ArrayList<>(files.keySet()));
        check(!ir.hasErrors(), "fixture pack must parse cleanly: " + ir.errors());
        return PassGraph.from(ir);
    }

    private static void testDefaultOrder() {
        PassGraph graph = graphOf(typicalPack());
        check(!graph.hasCycle(), "typical pack has no dependency cycle");
        // 回归门槛：装配默认依赖时，拓扑序必须等于解析期序号升序（= Iris 固定时序）
        check(graph.isCompatibleWithDefaultOrder(),
                "default dependency set must reproduce the fixed ordering, got " + graph.topologicalOrder());
        // 组序：gbuffers 先于 deferred 先于 composite 先于 final
        List<String> names = new ArrayList<>();
        for (LuminPassNode node : graph.orderedNodes()) {
            names.add(node.program().sourceBaseName());
        }
        int terrain = names.indexOf("gbuffers_terrain");
        int deferred = names.indexOf("deferred");
        int composite = names.indexOf("composite1");
        int fin = names.indexOf("final");
        check(terrain >= 0 && deferred >= 0 && composite >= 0 && fin >= 0,
                "all four programs present: " + names);
        check(terrain < deferred, "gbuffers precedes deferred");
        check(deferred < composite, "deferred precedes composite");
        check(composite < fin, "composite precedes final");
    }

    private static void testDependencyEdges() {
        PassGraph graph = graphOf(typicalPack());
        // deferred 写 colortex0，composite1 读 colortex0 → 存在写→读依赖边
        LuminPassNode deferred = null;
        LuminPassNode composite = null;
        for (LuminPassNode node : graph.nodes()) {
            if (node.program().sourceBaseName().equals("deferred")) {
                deferred = node;
            }
            if (node.program().sourceBaseName().equals("composite1")) {
                composite = node;
            }
        }
        check(deferred != null && composite != null, "deferred and composite1 found");
        check(deferred.writes().contains(LuminResourceId.main(LuminTargetId.color(0))),
                "deferred writes colortex0");
        check(composite.reads().contains(LuminResourceId.main(LuminTargetId.color(0))),
                "composite1 reads colortex0");
        check(graph.successorsOf(deferred.sequence()).contains(composite.sequence()),
                "write→read edge present from deferred to composite1");
        // gbuffers 写 colortex1 但不被后处理读取 → 无该资源的依赖边
        LuminPassNode gbuffers = graph.nodes().stream()
                .filter(n -> n.program().sourceBaseName().equals("gbuffers_terrain"))
                .findFirst().orElseThrow();
        check(gbuffers.writes().contains(LuminResourceId.main(LuminTargetId.color(1))),
                "terrain writes colortex1 (from DRAWBUFFERS:01)");
    }

    private static void testHazards() {
        PassGraph graph = graphOf(typicalPack());
        check(!graph.hazards().isEmpty(), "hazards detected in a multi-pass chain");
        // deferred 写 colortex0 后 composite1 读 → READ_AFTER_WRITE
        boolean sawRaw = graph.hazards().stream().anyMatch(h ->
                h.kind() == LuminHazard.Kind.READ_AFTER_WRITE
                        && h.resource().equals(LuminResourceId.main(LuminTargetId.color(0))));
        check(sawRaw, "read-after-write on colortex0: " + graph.hazards());
        // 连续两个写者（deferred 与 composite1 都写 colortex0）→ WRITE_AFTER_WRITE
        boolean sawWaw = graph.hazards().stream().anyMatch(h ->
                h.kind() == LuminHazard.Kind.WRITE_AFTER_WRITE
                        && h.resource().equals(LuminResourceId.main(LuminTargetId.color(0))));
        check(sawWaw, "write-after-write on colortex0: " + graph.hazards());
        for (LuminHazard hazard : graph.hazards()) {
            check(hazard.producer() != hazard.consumer(), "hazard spans two distinct passes");
        }
    }

    private static void testLifetimes() {
        PassGraph graph = graphOf(typicalPack());
        LuminResourceId color0 = LuminResourceId.main(LuminTargetId.color(0));
        PassGraph.ResourceLifetime lifetime = graph.lifetimes().get(color0);
        check(lifetime != null, "colortex0 has a lifetime record");
        check(lifetime.everWritten() && lifetime.everRead(), "colortex0 both written and read");
        check(lifetime.firstWrite() <= lifetime.lastWrite(), "write range ordered");
        check(lifetime.firstUse() <= lifetime.lastUse(), "use range ordered");
        // 只写不读的资源也是合法记录（如 terrain 的 colortex1）
        PassGraph.ResourceLifetime color1 = graph.lifetimes().get(LuminResourceId.main(LuminTargetId.color(1)));
        check(color1 != null && color1.everWritten(), "colortex1 written by terrain");
    }

    private static void testCycle() {
        // 帧内资源流依赖按构造必然前向（读自先前写者），因此环只能由显式依赖与资源流矛盾造成。
        // 这里用显式依赖声明一个真正的环：0 → 1 且 1 → 0。
        LuminProgramId first = LuminProgramId.of(LuminProgramGroup.COMPOSITE, "composite");
        LuminProgramId second = LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 1);
        LuminResourceId r = LuminResourceId.main(LuminTargetId.color(2));
        LuminResourceId s = LuminResourceId.main(LuminTargetId.color(3));
        List<LuminPassNode> nodes = List.of(
                new LuminPassNode(first, 0, Set.of(r), Set.of(s), LuminPassNode.ViewportSpec.full(), false),
                new LuminPassNode(second, 1, Set.of(s), Set.of(r), LuminPassNode.ViewportSpec.full(), false));
        Map<Integer, Set<Integer>> contradictory = Map.of(0, Set.of(1), 1, Set.of(0));
        PassGraph cyclic = PassGraph.build(nodes, false, contradictory);
        check(cyclic.hasCycle(), "contradictory explicit dependencies form a cycle");
        check(!cyclic.isCompatibleWithDefaultOrder(), "cyclic graph cannot match default order");
        check(cyclic.topologicalOrder().size() < nodes.size(), "cyclic graph yields a partial order");

        // 同一组节点在无显式依赖（纯资源流）下必须可调度
        PassGraph acyclic = PassGraph.build(nodes, false);
        check(!acyclic.hasCycle(), "resource-flow-only graph is always schedulable");
        check(acyclic.topologicalOrder().equals(List.of(0, 1)), "resource flow orders producer first");

        // 显式依赖也能用于"扩展包声明跨组依赖"（超集）：单向声明应可调度
        LuminProgramId extra = LuminProgramId.of(LuminProgramGroup.DEFERRED, "deferred");
        List<LuminPassNode> three = List.of(
                new LuminPassNode(first, 0, Set.of(), Set.of(r), LuminPassNode.ViewportSpec.full(), false),
                new LuminPassNode(second, 1, Set.of(), Set.of(s), LuminPassNode.ViewportSpec.full(), false),
                new LuminPassNode(extra, 2, Set.of(), Set.of(), LuminPassNode.ViewportSpec.full(), false));
        PassGraph withExtra = PassGraph.build(three, false, Map.of(0, Set.of(2)));
        check(!withExtra.hasCycle(), "one-way explicit dependency stays schedulable");
        check(withExtra.successorsOf(0).contains(2), "explicit edge recorded");
    }

    private static void testOrderingModes() {
        // 隐式时序：即使资源上无依赖，也保持声明顺序
        LuminProgramId a = LuminProgramId.of(LuminProgramGroup.COMPOSITE, "composite");
        LuminProgramId b = LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 1);
        LuminResourceId x = LuminResourceId.main(LuminTargetId.color(4));
        LuminResourceId y = LuminResourceId.main(LuminTargetId.color(5));
        List<LuminPassNode> independent = List.of(
                new LuminPassNode(a, 0, Set.of(), Set.of(x), LuminPassNode.ViewportSpec.full(), false),
                new LuminPassNode(b, 1, Set.of(), Set.of(y), LuminPassNode.ViewportSpec.full(), false));
        PassGraph withImplicit = PassGraph.build(independent, true);
        check(withImplicit.isCompatibleWithDefaultOrder(), "implicit order keeps declaration sequence");
        check(withImplicit.successorsOf(0).contains(1), "implicit edge inserted");
        // 自由模式：无资源依赖则无边（供 WP-3 重排）
        PassGraph free = PassGraph.build(independent, false);
        check(free.successorsOf(0).isEmpty(), "free mode adds no edges for independent passes");
        check(!free.hasCycle(), "free mode still acyclic");
        // 自由模式下的拓扑序允许重排（但仍稳定：序号升序）
        check(free.topologicalOrder().equals(List.of(0, 1)), "stable order for independent passes");
    }

    private static void testViewport() {
        PassGraph graph = graphOf(typicalPack());
        for (LuminPassNode node : graph.nodes()) {
            if (node.program().sourceBaseName().equals("composite1")) {
                check(Math.abs(node.viewport().scale() - 0.5f) < 1e-6,
                        "composite1 viewport scale propagated from scale.composite1, got " + node.viewport().scale());
            }
            if (node.program().sourceBaseName().equals("deferred")) {
                check(Math.abs(node.viewport().scale() - 1.0f) < 1e-6,
                        "deferred viewport scale propagated, got " + node.viewport().scale());
            }
        }
    }

    private static void testSelfFeedback() {
        // 读写同资源（如 colortex0 自反馈）应可被检出
        LuminProgramId program = LuminProgramId.of(LuminProgramGroup.COMPOSITE, "composite");
        LuminResourceId resource = LuminResourceId.main(LuminTargetId.color(0));
        LuminPassNode node = new LuminPassNode(program, 0, Set.of(resource), Set.of(resource),
                LuminPassNode.ViewportSpec.full(), false);
        check(node.isSelfFeedback(), "self feedback detected when a pass reads and writes the same resource");
        LuminResourceId other = LuminResourceId.main(LuminTargetId.color(1));
        LuminPassNode clean = new LuminPassNode(program, 1, Set.of(other), Set.of(resource),
                LuminPassNode.ViewportSpec.full(), false);
        check(!clean.isSelfFeedback(), "no self feedback for disjoint read/write sets");
        // 资源实例的乒乓语义
        LuminResourceId alt = resource.flipped();
        check(alt.copy() == LuminResourceId.Copy.ALT, "flipped resource targets the alt copy");
        check(alt.flipped().equals(resource), "double flip returns to main");
        check(!alt.equals(resource), "main and alt are distinct resources");
        Set<LuminResourceId> set = new LinkedHashSet<>();
        set.add(resource);
        set.add(alt);
        check(set.size() == 2, "ping-pong copies are distinct in a set");
    }

    private LuminShaderpackM2SelfTest() {
    }
}
