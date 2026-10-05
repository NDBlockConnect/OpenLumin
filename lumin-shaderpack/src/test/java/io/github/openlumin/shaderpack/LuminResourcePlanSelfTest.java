package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.graph.LuminHazard;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.parse.IncludeGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;
import io.github.openlumin.shaderpack.plan.LuminFramePlan;
import io.github.openlumin.shaderpack.plan.LuminFramePlanner;
import io.github.openlumin.shaderpack.plan.LuminResourceAllocation;
import io.github.openlumin.shaderpack.plan.LuminResourcePlan;
import io.github.openlumin.shaderpack.plan.LuminResourcePlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-2 M4 规划层自测（聚合入口）：资源分配默认/指令覆盖、尺寸语义、
 * 清屏批处理、帧计划的乒乓解析与竞争附着。全部合成夹具，纯 CPU。
 */
public final class LuminResourcePlanSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminPreprocessorSelfTest.runAll();
        section("plan: allocation defaults", LuminResourcePlanSelfTest::testAllocationDefaults);
        section("plan: directive overrides", LuminResourcePlanSelfTest::testDirectiveOverrides);
        section("plan: size semantics", LuminResourcePlanSelfTest::testSizeSemantics);
        section("plan: clear batching", LuminResourcePlanSelfTest::testClearBatching);
        section("plan: ping-pong resolution", LuminResourcePlanSelfTest::testPingPong);
        section("plan: self-feedback split", LuminResourcePlanSelfTest::testSelfFeedbackSplit);
        section("plan: hazards attached", LuminResourcePlanSelfTest::testHazardsAttached);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack plan] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack plan] ALL SELF TESTS PASSED (M1+M2+M6+preprocess+plan)");
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-36s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-36s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static ShaderpackIR irOf(Map<String, String> files) {
        ShaderpackIR ir = new ShaderpackLoader("fixture",
                path -> files.get(IncludeGraph.normalize(path)))
                .load(new ArrayList<>(files.keySet()));
        check(!ir.hasErrors(), "fixture must parse cleanly: " + ir.errors());
        return ir;
    }

    private static LuminResourcePlan planOf(ShaderpackIR ir) {
        return LuminResourcePlanner.plan(ir, PassGraph.from(ir));
    }

    private static void testAllocationDefaults() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        LuminResourcePlan plan = planOf(irOf(files));

        LuminResourceAllocation color0 = plan.allocation(LuminTargetId.color(0));
        check(color0 != null, "colortex0 must be allocated");
        check(color0.format() == LuminBufferFormat.RGBA8, "colortex default format RGBA8");
        check(color0.clearColor().source() == LuminResourceAllocation.ClearColor.Source.FOG,
                "colortex0 default clear is fog");
        check(color0.pingPong(), "colortex is a ping-pong pair");

        LuminResourceAllocation depth0 = plan.allocation(LuminTargetId.depth(0));
        check(depth0 != null, "depthtex0 must be allocated (composite reads depth)");
        check(depth0.format() == LuminBufferFormat.R32F, "depth default format R32F");
        check(depth0.clearColor().source() == LuminResourceAllocation.ClearColor.Source.DEPTH_FAR,
                "depth default clear is the far plane");
        check(!depth0.pingPong(), "depthtex is single-instance");
    }

    private static void testDirectiveOverrides() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", """
                #version 330
                const int colortex2Format = RGBA16F;
                const bool colortex2Clear = false;
                const bool colortex2MipmapEnabled = true;
                /* DRAWBUFFERS:02 */
                void main(){}
                """);
        LuminResourcePlan plan = planOf(irOf(files));
        LuminResourceAllocation color2 = plan.allocation(LuminTargetId.color(2));
        check(color2 != null, "colortex2 must be allocated");
        check(color2.format() == LuminBufferFormat.RGBA16F, "format directive honored");
        check(!color2.clear(), "clear=false honored");
        check(color2.mipmap(), "mipmap directive honored");
        check(plan.clearBatches().stream()
                        .noneMatch(batch -> batch.targets().contains(LuminTargetId.color(2))),
                "non-cleared target must not appear in any clear batch");
    }

    private static void testSizeSemantics() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", """
                size.buffer.colortex2 = 0.5 0.5
                size.buffer.colortex3 = 960 540
                size.buffer.colortex4 = 0.5 540
                """);
        files.put("shaders/composite1.fsh",
                "/* DRAWBUFFERS:0234 */\nvoid main(){}\n");
        LuminResourcePlan plan = planOf(irOf(files));

        LuminResourceAllocation color2 = plan.allocation(LuminTargetId.color(2));
        check(color2.size().widthRelative() && color2.size().heightRelative(),
                "decimal values are relative (Iris semantics)");
        check(color2.size().resolveWidth(1920) == 960, "0.5 * 1920 = 960");

        LuminResourceAllocation color3 = plan.allocation(LuminTargetId.color(3));
        check(!color3.size().widthRelative() && !color3.size().heightRelative(),
                "integer values are absolute (Iris semantics)");
        check(color3.size().resolveWidth(1920) == 960, "absolute 960 stays 960");

        LuminResourceAllocation color4 = plan.allocation(LuminTargetId.color(4));
        check(color4.size().widthRelative() && !color4.size().heightRelative(),
                "per-axis relative/absolute split");
        check(color4.size().resolveWidth(1920) == 960, "relative width resolves");
        check(color4.size().resolveHeight(1080) == 540, "absolute height stays 540");
    }

    private static void testClearBatching() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", """
                #version 330
                const vec4 colortex3ClearColor = vec4(0.1, 0.2, 0.3, 1.0);
                const vec4 colortex4ClearColor = vec4(0.1, 0.2, 0.3, 1.0);
                /* DRAWBUFFERS:01234 */
                void main(){}
                """);
        LuminResourcePlan plan = planOf(irOf(files));

        check(plan.clearBatches().stream().anyMatch(batch ->
                        batch.targets().equals(java.util.List.of(LuminTargetId.color(0)))
                                && batch.color().source()
                                == LuminResourceAllocation.ClearColor.Source.FOG
                                && batch.bothCopies()),
                "colortex0 fog batch present with both copies");
        check(plan.clearBatches().stream().anyMatch(batch ->
                        batch.targets().equals(java.util.List.of(LuminTargetId.color(1)))
                                && batch.color().source()
                                == LuminResourceAllocation.ClearColor.Source.WHITE),
                "colortex1 white batch present");
        check(plan.clearBatches().stream().anyMatch(batch ->
                        batch.targets().size() == 2
                                && batch.targets().contains(LuminTargetId.color(3))
                                && batch.targets().contains(LuminTargetId.color(4))),
                "identical literal clear colors group into one batch");
    }

    /** terrain 写 → ALT；composite 读 ALT、写 MAIN；final 读 MAIN。 */
    private static void testPingPong() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/final.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        ShaderpackIR ir = irOf(files);
        PassGraph graph = PassGraph.from(ir);
        LuminFramePlan frame = LuminFramePlanner.plan(graph, LuminResourcePlanner.plan(ir, graph));
        var passes = frame.passSteps();
        check(passes.size() == 3, "three passes planned, got " + passes.size());

        var terrain = passes.get(0);
        check(terrain.writes().get(LuminTargetId.color(0)) == LuminResourceId.Copy.ALT,
                "first write goes to ALT");
        var composite = passes.get(1);
        check(composite.reads().get(LuminTargetId.color(0)) == LuminResourceId.Copy.ALT,
                "next read sees the ALT write");
        check(composite.writes().get(LuminTargetId.color(0)) == LuminResourceId.Copy.MAIN,
                "second write flips back to MAIN");
        var finalPass = passes.get(2);
        check(finalPass.reads().get(LuminTargetId.color(0)) == LuminResourceId.Copy.MAIN,
                "final read sees the MAIN write");
    }

    /** 自反馈 pass：读旧副本、写新副本——两副本必须不同。 */
    private static void testSelfFeedbackSplit() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        ShaderpackIR ir = irOf(files);
        PassGraph graph = PassGraph.from(ir);
        LuminFramePlan frame = LuminFramePlanner.plan(graph, LuminResourcePlanner.plan(ir, graph));
        var composite = frame.passSteps().get(1);
        check(composite.reads().get(LuminTargetId.color(0)) != null,
                "self-feedback pass must read colortex0");
        check(composite.reads().get(LuminTargetId.color(0))
                        != composite.writes().get(LuminTargetId.color(0)),
                "self-feedback must read and write different copies");
    }

    /** 竞争附着：terrain→composite 的 colortex0 RAW 竞争出现在 composite 的 hazardsBefore。 */
    private static void testHazardsAttached() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        ShaderpackIR ir = irOf(files);
        PassGraph graph = PassGraph.from(ir);
        LuminFramePlan frame = LuminFramePlanner.plan(graph, LuminResourcePlanner.plan(ir, graph));
        var composite = frame.passSteps().get(1);
        check(composite.hazardsBefore().stream().anyMatch(h ->
                        h.kind() == LuminHazard.Kind.READ_AFTER_WRITE
                                && h.resource().target().equals(LuminTargetId.color(0))
                                && h.producer() == 0),
                "RAW hazard must be attached to the consumer pass, got "
                        + composite.hazardsBefore());
    }

    private LuminResourcePlanSelfTest() {
    }
}
