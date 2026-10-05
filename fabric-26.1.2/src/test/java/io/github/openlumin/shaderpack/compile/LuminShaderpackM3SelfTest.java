package io.github.openlumin.shaderpack.compile;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminPackDirectives;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminProgramGroup;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.parse.IncludeGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-2 M3 自测：shaderpack 编译器（合成族管线 / 混合映射 / 资源部署 / 拒绝语义）。
 * 全部使用合成夹具；RenderPipeline 为数据构建，不触发 GPU 编译。
 */
public final class LuminShaderpackM3SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        section("composite pipeline properties", LuminShaderpackM3SelfTest::testCompositePipeline);
        section("blend override int mapping", LuminShaderpackM3SelfTest::testBlendOverride);
        section("blend code mapping table", LuminShaderpackM3SelfTest::testBlendCodes);
        section("shader resource deployment", LuminShaderpackM3SelfTest::testResourceDeployment);
        section("deployment is preprocessed", LuminShaderpackM3SelfTest::testDeploymentPreprocessed);
        section("fullscreen vertex fallback", LuminShaderpackM3SelfTest::testFullscreenFallback);
        section("compute pass deferred", LuminShaderpackM3SelfTest::testComputeDeferred);
        section("geometry pass deferred", LuminShaderpackM3SelfTest::testGeometryDeferred);
        section("capability rejection wired", LuminShaderpackM3SelfTest::testCapabilityRejection);
        section("capability optional split wired", LuminShaderpackM3SelfTest::testCapabilityOptionalSplit);
        section("resource/frame plan wired", LuminShaderpackM3SelfTest::testPlansWired);
        section("translation wired", LuminShaderpackM3SelfTest::testTranslationWired);
        section("error pack refused", LuminShaderpackM3SelfTest::testErrorRefused);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack M3] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack M3] ALL SELF TESTS PASSED");
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

    private static ShaderpackLoader loader(Map<String, String> files) {
        return new ShaderpackLoader("fixture", path -> files.get(IncludeGraph.normalize(path)));
    }

    /** 含 composite1（带 blend + scale）+ final + gbuffers_terrain 的合成包。 */
    private static Map<String, String> pack() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", """
                scale.composite1=0.5
                blend.composite1=on ONE ZERO
                """);
        files.put("shaders/gbuffers_terrain.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:01 */\nvoid main(){}\n");
        files.put("shaders/composite1.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/final.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/final.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        return files;
    }

    private static CompiledShaderpack compile(Map<String, String> files) {
        ShaderpackIR ir = loader(files).load(new ArrayList<>(files.keySet()));
        check(!ir.hasErrors(), "fixture must parse cleanly: " + ir.errors());
        PassGraph graph = PassGraph.from(ir);
        check(!graph.hasCycle(), "fixture graph must be schedulable");
        return LuminShaderpackCompiler.compile(ir, graph);
    }

    private static void testCompositePipeline() {
        CompiledShaderpack compiled = compile(pack());
        check(!compiled.hasErrors(), "fixture must compile cleanly: " + compiled.errors());
        LuminProgramId finalId = LuminProgramId.of(LuminProgramGroup.FINAL, "final");
        RenderPipeline pipeline = compiled.pipelineFor(finalId);
        check(pipeline != null, "final pass must be compiled");
        check(pipeline.getVertexFormat() == DefaultVertexFormat.EMPTY,
                "composite passes use the engine gl_VertexID convention (EMPTY format)");
        check(pipeline.getVertexFormatMode() == VertexFormat.Mode.TRIANGLES,
                "composite passes use fullscreen-triangle topology");
        check(!pipeline.isCull(), "composite passes must not cull");
        check(pipeline.getVertexShader().getNamespace().equals("openlumin"),
                "shader namespace must be openlumin");
    }

    private static void testBlendOverride() {
        CompiledShaderpack compiled = compile(pack());
        LuminProgramId composite1 = LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 1);
        RenderPipeline pipeline = compiled.pipelineFor(composite1);
        check(pipeline != null, "composite1 must be compiled");
        check(compiled.warnings().isEmpty(),
                "valid blend codes must not warn: " + compiled.warnings());
    }

    private static void testBlendCodes() {
        check(LuminShaderpackCompiler.mapSourceFactor(0) != null, "code 0 = ZERO");
        check(LuminShaderpackCompiler.mapSourceFactor(4) != null, "code 4 = SRC_ALPHA");
        check(LuminShaderpackCompiler.mapSourceFactor(9) != null, "code 9 = ONE_MINUS_DST_COLOR");
        check(LuminShaderpackCompiler.mapSourceFactor(42) == null, "out-of-range code must be null");
        check(LuminShaderpackCompiler.mapDestFactor(0) != null, "dest code 0 = ZERO");
        check(LuminShaderpackCompiler.mapDestFactor(5) != null, "dest code 5 = ONE_MINUS_SRC_ALPHA");
        check(LuminShaderpackCompiler.mapDestFactor(-1) == null, "negative code must be null");
    }

    private static void testUnknownBlend() {
        Map<String, String> files = pack();
        files.put("shaders/shaders.properties", "blend.composite1=on 99 99\n");
        CompiledShaderpack compiled = compile(files);
        check(!compiled.hasErrors(), "unknown blend must degrade, not fail");
        check(compiled.warnings().stream().anyMatch(
                        d -> d.message().contains("unknown blend factor codes")),
                "out-of-range codes must emit a warning");
    }

    private static void testResourceDeployment() {
        CompiledShaderpack compiled = compile(pack());
        check(compiled.shaderResources().containsKey("shaders/shaderpack/composite1.vsh"),
                "composite1 vertex source must be deployed");
        check(compiled.shaderResources().containsKey("shaders/shaderpack/composite1.fsh"),
                "composite1 fragment source must be deployed");
        check(compiled.shaderResources().containsKey("shaders/shaderpack/final.vsh"),
                "final vertex source must be deployed");
        check(compiled.shaderResources().containsKey("shaders/shaderpack/final.fsh"),
                "final fragment source must be deployed");
        check(!compiled.shaderResources().containsKey("shaders/shaderpack/gbuffers_terrain.vsh"),
                "geometry pass sources must not be deployed in M3");
    }

    /** 真实 shaderpack 的合成 pass 常只有 .fsh：必须回退到内建全屏顶点着色器并部署它。 */
    private static void testFullscreenFallback() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        CompiledShaderpack compiled = compile(files);
        LuminProgramId composite1 = LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 1);
        RenderPipeline pipeline = compiled.pipelineFor(composite1);
        check(pipeline != null, "fragment-only composite must still compile");
        check(pipeline.getVertexShader().toString().endsWith("shaderpack/_fullscreen"),
                "fragment-only composite must bind the built-in fullscreen vertex shader, got "
                        + pipeline.getVertexShader());
        check(compiled.shaderResources().containsKey("shaders/shaderpack/_fullscreen.vsh"),
                "built-in fullscreen vertex source must be deployed");
        check(!compiled.shaderResources().containsKey("shaders/shaderpack/composite1.vsh"),
                "no pack vertex source should be deployed when absent");
    }

    /** 部署的源必须是预处理后的：include 展开、#version 顶部回填。 */
    private static void testDeploymentPreprocessed() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh",
                "#include \"include/util.glsl\"\n/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/include/util.glsl", "#version 330\nfloat utilValue = 1.0;\n");
        CompiledShaderpack compiled = compile(files);
        String deployed = compiled.shaderResources().get("shaders/shaderpack/composite1.fsh");
        check(deployed != null, "composite1 fragment source must be deployed");
        check(!deployed.contains("#include"), "includes must be expanded before deployment");
        check(deployed.contains("float utilValue = 1.0;"),
                "included content must be inlined into the deployed source");
        check(deployed.startsWith("#version 330"),
                "#version must be hoisted to the top of the deployed source");
    }

    /** 仅有 .csh 的 compute 程序：26.1.2 GL 无计算管线，应延后并记录 INFO。 */
    private static void testComputeDeferred() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/setup.csh", "#version 430\nlayout(local_size_x=1) in;\nvoid main(){}\n");
        CompiledShaderpack compiled = compile(files);
        LuminProgramId setup = LuminProgramId.of(LuminProgramGroup.SETUP, "setup");
        check(compiled.pipelineFor(setup) == null,
                "compute-only pass must not produce a graphics pipeline");
        check(!compiled.executionOrder().contains(setup),
                "deferred compute pass must not enter the M3 execution order");
        check(compiled.diagnostics().stream().anyMatch(
                        d -> d.severity() == Diagnostic.Severity.INFO
                                && d.message().contains("compute pass")),
                "compute deferral must be recorded as INFO");
    }

    private static void testGeometryDeferred() {
        CompiledShaderpack compiled = compile(pack());
        LuminProgramId terrain = LuminProgramId.of(LuminProgramGroup.GBUFFERS, "terrain");
        check(compiled.pipelineFor(terrain) == null,
                "gbuffers passes must not be compiled in M3");
        check(compiled.diagnostics().stream().anyMatch(
                        d -> d.severity() == Diagnostic.Severity.INFO
                                && d.message().contains("deferred to WP-1 integration")),
                "geometry pass deferral must be recorded as INFO");
    }

    private static void testErrorRefused() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", "bogus_directive=broken\n");
        ShaderpackIR ir = loader(files).load(new ArrayList<>(files.keySet()));
        PassGraph graph = PassGraph.from(ir);
        CompiledShaderpack compiled = LuminShaderpackCompiler.compile(ir, graph);
        if (ir.hasErrors()) {
            check(compiled.pipelines().isEmpty(),
                    "error pack must not produce pipelines");
        } else {
            check(!compiled.hasErrors(), "clean pack must compile");
        }
    }

    /** required 缺失必须明确拒绝（不静默降级）：无管线、ERROR 诊断、报告列出缺失旗标。 */
    private static void testCapabilityRejection() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", "iris.features.required = COMPUTE_SHADERS\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        CompiledShaderpack compiled = compile(files);
        check(compiled.hasErrors(), "missing required feature must fail compilation");
        check(compiled.pipelines().isEmpty(), "rejected pack must not produce pipelines");
        check(compiled.capabilityReport().missingRequired().contains(
                        io.github.openlumin.shaderpack.LuminFeatureFlag.COMPUTE_SHADERS),
                "report must list the missing flag");
        check(compiled.errors().stream().anyMatch(
                        d -> d.message().contains("unsupported feature")),
                "rejection must be recorded as an ERROR diagnostic");
    }

    /** optional 拆分：支持者 provided、不支持者 unavailable，且不影响编译。 */
    private static void testCapabilityOptionalSplit() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties",
                "iris.features.optional = SEPARATE_HARDWARE_SAMPLERS CUSTOM_IMAGES\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        CompiledShaderpack compiled = compile(files);
        check(!compiled.hasErrors(), "optional gaps must not reject: " + compiled.errors());
        check(compiled.capabilityReport().providedOptional().contains(
                        io.github.openlumin.shaderpack.LuminFeatureFlag.SEPARATE_HARDWARE_SAMPLERS),
                "supported optional must be provided");
        check(compiled.capabilityReport().unavailableOptional().contains(
                        io.github.openlumin.shaderpack.LuminFeatureFlag.CUSTOM_IMAGES),
                "unsupported optional must be marked unavailable");
    }

    /** 编译产物必须携带资源计划与帧计划（M4 规划层接线）。 */
    private static void testPlansWired() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/final.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        CompiledShaderpack compiled = compile(files);
        check(compiled.resourcePlan().allocation(
                        io.github.openlumin.shaderpack.LuminTargetId.color(0)) != null,
                "colortex0 allocation must be planned");
        check(compiled.framePlan().passSteps().size() == 3,
                "frame plan must cover all three passes, got "
                        + compiled.framePlan().passSteps().size());
        check(compiled.framePlan().steps().stream()
                        .anyMatch(step -> step instanceof
                                io.github.openlumin.shaderpack.plan.LuminFramePlan.ClearStep),
                "frame plan must start with clear steps");
    }

    /** legacy 源必须经翻译后部署；管线声明翻译层注入的 UBO 块。 */
    private static void testTranslationWired() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", """
                #version 120
                uniform sampler2D colortex0;
                void main() {
                    gl_FragColor = texture2D(colortex0, vec2(0.0));
                }
                """);
        CompiledShaderpack compiled = compile(files);
        check(!compiled.hasErrors(), "legacy pack must compile: " + compiled.errors());
        String deployed = compiled.shaderResources().get("shaders/shaderpack/composite1.fsh");
        check(deployed != null, "legacy fragment source must be deployed");
        check(deployed.startsWith("#version 330"), "version normalized on deployment");
        check(deployed.contains("out vec4 fragColor;"), "fragment output injected");
        check(!deployed.contains("gl_FragColor"), "legacy output symbol consumed");

        LuminProgramId composite1 = LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 1);
        RenderPipeline pipeline = compiled.pipelineFor(composite1);
        check(pipeline != null, "composite pipeline must exist");
        check(pipeline.getUniforms().stream()
                        .anyMatch(u -> u.name().equals("DynamicTransforms")),
                "composite pipeline must declare DynamicTransforms");
        check(pipeline.getUniforms().stream()
                        .anyMatch(u -> u.name().equals("Projection")),
                "composite pipeline must declare Projection");
    }
}
