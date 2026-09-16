package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.parse.DirectiveParser;
import io.github.openlumin.shaderpack.parse.IncludeGraph;
import io.github.openlumin.shaderpack.parse.PropertiesParser;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP-2 M1 自测：Shaderpack 解析层（发现 / include 图 / 属性 / 指令 / IR）。
 * 全部使用**合成内存夹具**——仓库内不存任何第三方 shaderpack（许可与体积纪律）。
 */
public final class LuminShaderpackM1SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = 0;
        section("include graph and flatten", LuminShaderpackM1SelfTest::testIncludeGraph);
        section("include cycle detection", LuminShaderpackM1SelfTest::testCycleDetection);
        section("path resolution and normalisation", LuminShaderpackM1SelfTest::testPathRules);
        section("properties first-wins", LuminShaderpackM1SelfTest::testProperties);
        section("directive parsing", LuminShaderpackM1SelfTest::testDirectives);
        section("program identification", LuminShaderpackM1SelfTest::testProgramIdentification);
        section("target id parsing", LuminShaderpackM1SelfTest::testTargetIds);
        section("loader end to end", LuminShaderpackM1SelfTest::testLoaderEndToEnd);
        section("loader diagnostics", LuminShaderpackM1SelfTest::testLoaderDiagnostics);
        section("custom uniforms and flags", LuminShaderpackM1SelfTest::testUniformsAndFlags);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack M1] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack M1] ALL SELF TESTS PASSED");
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static IncludeGraph.SourceProvider provider(Map<String, String> files) {
        return path -> files.get(IncludeGraph.normalize(path));
    }

    private static void testIncludeGraph() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.vsh", "#include \"include/common.glsl\"\nvoid main(){}\n");
        files.put("shaders/include/common.glsl", "#include \"shared.glsl\"\nfloat a;\n");
        files.put("shaders/include/shared.glsl", "float b;\n");

        IncludeGraph graph = new IncludeGraph(provider(files));
        IncludeGraph.Node root = graph.addRoot("shaders/gbuffers_terrain.vsh");
        check(root != null, "root present");
        checkEquals(3, graph.paths().size(), "all three files reachable");
        check(!graph.hasCycles(), "no cycles");
        check(graph.node("shaders/gbuffers_terrain.vsh").includes()
                .contains("shaders/include/common.glsl"), "relative include resolved");
        // 相对上一层目录的 include 解析
        check(graph.node("shaders/include/common.glsl").includes()
                .contains("shaders/include/shared.glsl"), "nested relative include resolved");

        String flat = graph.flatten("shaders/gbuffers_terrain.vsh");
        check(flat.contains("float a;"), "nested include content inlined");
        check(flat.contains("float b;"), "deep include content inlined");
        check(flat.contains("void main(){}"), "root body preserved");
        check(!flat.contains("#include"), "include directives consumed");
        // 顺序：common 的 include(shared) 先于 common 自身内容
        check(flat.indexOf("float b;") < flat.indexOf("float a;"),
                "depth-first inlining order");

        // 重复 include 会重复文本（OptiFine 语义：include guard 无效）
        Map<String, String> dup = new LinkedHashMap<>();
        dup.put("shaders/a.fsh", "#include \"inc.glsl\"\n#include \"inc.glsl\"\n");
        dup.put("shaders/inc.glsl", "const int X = 1;\n");
        String duplicated = new IncludeGraph(provider(dup)).flatten("shaders/a.fsh");
        check(countOccurrences(duplicated, "const int X = 1;") == 2,
                "repeated include duplicates text (guards do not apply)");

        // 行注释中的伪指令不应被计入
        check(IncludeGraph.scanIncludes("// #include \"x.glsl\"\n").isEmpty(),
                "commented-out include ignored");
        checkEquals(1, IncludeGraph.scanIncludes("#include <foo/bar.glsl>\n").size(),
                "angle-bracket include recognised");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void testCycleDetection() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/a.glsl", "#include \"b.glsl\"\n");
        files.put("shaders/b.glsl", "#include \"a.glsl\"\n");
        IncludeGraph graph = new IncludeGraph(provider(files));
        graph.addRoot("shaders/a.glsl");
        check(graph.hasCycles(), "two-file cycle detected");
        String described = graph.cycles().get(0).describe();
        check(described.contains("a.glsl") && described.contains("b.glsl"),
                "cycle path names both files: " + described);

        // 自环
        Map<String, String> self = new LinkedHashMap<>();
        self.put("shaders/s.glsl", "#include \"s.glsl\"\n");
        IncludeGraph selfGraph = new IncludeGraph(provider(self));
        selfGraph.addRoot("shaders/s.glsl");
        check(selfGraph.hasCycles(), "self-include detected");

        // 展平时环路不得无限展开
        String flat = graph.flatten("shaders/a.glsl");
        check(flat != null, "flatten terminates on cycles");
    }

    private static void testPathRules() {
        checkEquals("shaders/include/x.glsl", IncludeGraph.resolve("shaders/a.fsh", "include/x.glsl"),
                "relative to includer directory");
        checkEquals("shaders/inc/x.glsl", IncludeGraph.resolve("shaders/inc/a.fsh", "x.glsl"),
                "sibling relative path");
        checkEquals("shaders/x.glsl", IncludeGraph.resolve("shaders/sub/a.fsh", "/shaders/x.glsl"),
                "absolute path is pack-rooted");
        checkEquals("shaders/x.glsl", IncludeGraph.resolve("shaders/a.fsh", "./x.glsl"), "dot segment");
        checkEquals("x.glsl", IncludeGraph.resolve("shaders/a.fsh", "../x.glsl"), "parent segment");
        checkEquals("shaders/a.glsl", IncludeGraph.normalize("\\shaders\\a.glsl"), "backslash unified");
    }

    private static void testProperties() {
        String text = """
                # leading comment
                ! alternative comment
                clouds=true
                clouds=false
                shadow.culling = true
                scale.composite = 0.5 1 2
                size.buffer.colortex2= 960 540
                alphaTest.gbuffers_terrain=0.1
                multi = line \\
                        continued
                quoted = "value with # hash"
                """;
        PropertiesParser.ParsedProperties parsed = PropertiesParser.parse(text);
        checkEquals("true", parsed.get("clouds"), "first declaration wins");
        checkEquals("true", parsed.get("shadow.culling"), "colon-less key with spaces around =");
        checkEquals("0.5 1 2", parsed.get("scale.composite"), "scale value kept verbatim");
        checkEquals("960 540", parsed.get("size.buffer.colortex2"), "size value kept verbatim");
        checkEquals("0.1", parsed.get("alphaTest.gbuffers_terrain"), "alphaTest value");
        checkEquals("line continued", parsed.get("multi"), "backslash continuation joined");
        checkEquals("value with # hash", parsed.get("quoted"), "hash inside quotes preserved");
        check(parsed.lineOf("clouds") == 3, "line number of first declaration, got " + parsed.lineOf("clouds"));
        check(!parsed.contains("missing"), "absent key");
    }

    private static void testDirectives() {
        String source = """
                #version 330
                const int colortex0Format = RGBA16F;
                const bool colortex1Clear = false;
                const vec4 colortex2ClearColor = vec4(0.1, 0.2, 0.3, 1.0);
                const int shadowMapResolution = 2048;
                const int flags = 0x10 | 0x01;
                /* DRAWBUFFERS:01 */
                void main() { }
                /* DRAWBUFFERS:0123 */
                """;
        DirectiveParser.ParsedDirectives parsed = DirectiveParser.parse(source);
        checkEquals(5, parsed.constants().size() + 0, "five const declarations, got " + parsed.constants().size());
        checkEquals("RGBA16F", parsed.constants().get(0).rawValue(), "const raw value retained");
        checkEquals(2048, DirectiveParser.parseIntExpression(
                DirectiveParser.findConst(parsed, "shadowMapResolution").rawValue(), -1),
                "integer const parsed");
        checkEquals(0x11, DirectiveParser.parseIntExpression(
                DirectiveParser.findConst(parsed, "flags").rawValue(), -1), "hex-or expression evaluated");
        // DRAWBUFFERS 后出现者胜
        check(parsed.drawTargets().contains(LuminTargetId.color(0)), "colortex0 present");
        check(parsed.drawTargets().contains(LuminTargetId.color(3)), "last DRAWBUFFERS wins (colortex3)");
        checkEquals(4, parsed.drawTargets().size(), "four draw targets, got " + parsed.drawTargets().size());
        // RENDERTARGETS 备选语法
        DirectiveParser.ParsedDirectives alt = DirectiveParser.parse("/* RENDERTARGETS:0,2,colortex5 */");
        check(alt.drawTargets().contains(LuminTargetId.color(2)), "RENDERTARGETS by index");
        check(alt.drawTargets().contains(LuminTargetId.color(5)), "RENDERTARGETS by name");
        // 清屏色解析
        float[] cleared = ShaderpackLoader.parseClearColor("vec4(0.5, 0.25, 0, 1)");
        check(cleared != null && Math.abs(cleared[0] - 0.5f) < 1e-6, "clear colour parsed");
    }

    private static void testProgramIdentification() {
        checkEquals(LuminProgramId.of(LuminProgramGroup.GBUFFERS, "terrain"),
                ShaderpackLoader.identifyProgram("gbuffers_terrain"), "gbuffers program");
        checkEquals(LuminProgramId.of(LuminProgramGroup.SHADOW, "shadow"),
                ShaderpackLoader.identifyProgram("shadow"), "shadow main program");
        checkEquals(LuminProgramId.of(LuminProgramGroup.SHADOW, "cutout"),
                ShaderpackLoader.identifyProgram("shadow_cutout"), "shadow variant");
        checkEquals(LuminProgramId.of(LuminProgramGroup.COMPOSITE, "composite"),
                ShaderpackLoader.identifyProgram("composite"), "composite base");
        checkEquals(LuminProgramId.numbered(LuminProgramGroup.COMPOSITE, 7),
                ShaderpackLoader.identifyProgram("composite7"), "numbered composite");
        checkEquals(LuminProgramId.of(LuminProgramGroup.FINAL, "final"),
                ShaderpackLoader.identifyProgram("final"), "final program");
        check(ShaderpackLoader.identifyProgram("gbuffers_unknown") == null,
                "unknown gbuffer name rejected");
        // 源文件基名（决定编译期查找键）
        checkEquals("gbuffers_terrain",
                LuminProgramId.of(LuminProgramGroup.GBUFFERS, "terrain").sourceBaseName(),
                "gbuffers source base name");
        checkEquals("deferred3", LuminProgramId.numbered(LuminProgramGroup.DEFERRED, 3).sourceBaseName(),
                "numbered deferred base name");
        checkEquals("shadow_cutout",
                LuminProgramId.of(LuminProgramGroup.SHADOW, "cutout").sourceBaseName(),
                "shadow variant base name");
    }

    private static void testTargetIds() {
        checkEquals(LuminTargetId.color(3), LuminTargetId.parse("colortex3"), "colortex by name");
        checkEquals(LuminTargetId.color(1), LuminTargetId.parse("gdepth"), "legacy alias gdepth → colortex1");
        checkEquals(LuminTargetId.color(6), LuminTargetId.parse("gaux3"), "legacy alias gaux3 → colortex6");
        checkEquals(LuminTargetId.color(0), LuminTargetId.parse("gcolor"), "legacy alias gcolor → colortex0");
        checkEquals(LuminTargetId.depth(1), LuminTargetId.parse("depthtex1"), "depthtex1");
        checkEquals(LuminTargetId.shadowDepth(1), LuminTargetId.parse("shadowtex1"), "shadowtex1");
        checkEquals(LuminTargetId.shadowColor(2), LuminTargetId.parse("shadowcolor2"), "shadowcolor2");
        check(LuminTargetId.parse("colortex99") == null, "out-of-range colour buffer rejected");
        check(LuminTargetId.parse("bogus") == null, "unknown name rejected");
        checkEquals("colortex5", LuminTargetId.color(5).canonicalName(), "canonical name");
        // 指令名 → 目标
        checkEquals(LuminTargetId.color(3), ShaderpackLoader.targetOfDirective("colortex3Format"),
                "directive name to target");
        checkEquals(LuminTargetId.shadowDepth(0),
                ShaderpackLoader.targetOfDirective("shadowtex0MipmapEnabled"), "shadowtex directive");
    }

    private static Map<String, String> fullPack() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", """
                clouds=true
                scale.composite=0.5
                alphaTest.gbuffers_terrain=0.1
                uniform.float.sunHeight = sin(worldTime) * 0.5
                variable.int.mode = 2
                iris.features.required = COMPUTE_SHADERS SSBO
                iris.features.optional = CUSTOM_IMAGES
                """);
        files.put("shaders/gbuffers_basic.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/gbuffers_basic.fsh", "#include \"include/color.glsl\"\n/* DRAWBUFFERS:0 */\n");
        files.put("shaders/gbuffers_terrain.fsh", """
                const int colortex0Format = RGBA16F;
                const vec4 colortex1ClearColor = vec4(0.2, 0.3, 0.4, 1.0);
                /* DRAWBUFFERS:01 */
                """);
        files.put("shaders/gbuffers_terrain.vsh", "#version 330\nvoid main(){}\n");
        files.put("shaders/shadow.fsh", "#version 330\n");
        files.put("shaders/composite1.fsh", "#version 330\n");
        files.put("shaders/final.fsh", "#version 330\n");
        files.put("shaders/include/color.glsl", "vec4 tint = vec4(1.0);\n");
        files.put("shaders/world1/gbuffers_basic.fsh", "#version 330\n");
        return files;
    }

    private static void testLoaderEndToEnd() {
        Map<String, String> files = fullPack();
        ShaderpackLoader loader = new ShaderpackLoader("fixture", provider(files));
        ShaderpackIR ir = loader.load(new ArrayList<>(files.keySet()));

        check(!ir.hasErrors(), "clean pack has no errors: " + ir.errors());
        check(ir.metadata().hasShaderSources(), "shader sources discovered");
        check(ir.metadata().dimensions().contains("world1"), "dimension folder discovered");
        // gbuffers_basic（基础 + world1 维度变体共享同一程序标识）、gbuffers_terrain、shadow、
        // composite1、final —— 共 5 个不同标识（维度变体按维度在编译期选择，不产生新标识）
        checkEquals(5, ir.programs().size(), "five distinct program ids, got " + ir.programs().size());

        // gbuffers_basic 两阶段齐全
        LuminProgramSource basic = ir.programs().get(
                LuminProgramId.of(LuminProgramGroup.GBUFFERS, "basic"));
        check(basic != null && basic.isCompleteGraphicsProgram(), "basic has both stages");
        check(basic.has(LuminShaderKind.VERTEX) && basic.has(LuminShaderKind.FRAGMENT),
                "basic stage keys");

        // DRAWBUFFERS 生效
        LuminProgramSource terrain = ir.programs().get(
                LuminProgramId.of(LuminProgramGroup.GBUFFERS, "terrain"));
        checkEquals(2, terrain.drawTargets().size(), "terrain writes two targets");
        check(terrain.drawTargets().contains(LuminTargetId.color(1)), "terrain writes colortex1");

        // 源内 const 指令 → 目标规格
        LuminTargetSpec color0 = ir.targetSpec(LuminTargetId.color(0));
        checkEquals(LuminBufferFormat.RGBA16F, color0.format(), "colortex0 format from const");
        float[] clear1 = ir.targetSpec(LuminTargetId.color(1)).clearColor();
        check(clear1 != null && Math.abs(clear1[1] - 0.3f) < 1e-6, "colortex1 clear colour from const");

        // 属性 → 包级指令
        check(ir.directives().switchEnabled("clouds"), "clouds switch parsed");
        LuminPackDirectives.ViewportScale scale = ir.directives().viewportScale(
                LuminProgramId.of(LuminProgramGroup.COMPOSITE, "composite"));
        check(scale != null && Math.abs(scale.scale() - 0.5f) < 1e-6, "composite scale parsed");
        check(ir.directives().alphaTest(LuminProgramId.of(LuminProgramGroup.GBUFFERS, "terrain")) != null,
                "alphaTest parsed for terrain");
    }

    private static void testLoaderDiagnostics() {
        // 缺失 include → ERROR 且定位到路径
        Map<String, String> missing = new LinkedHashMap<>();
        missing.put("shaders/gbuffers_basic.fsh", "#include \"include/nope.glsl\"\n");
        ShaderpackIR ir = new ShaderpackLoader("missing", provider(missing))
                .load(new ArrayList<>(missing.keySet()));
        check(ir.hasErrors(), "missing include produces an error");
        check(ir.errors().get(0).message().contains("nope.glsl"), "error names the missing file");

        // include 环 → ERROR 且给出闭环（用真实存在的文件名构造环）
        Map<String, String> cyclic = new LinkedHashMap<>();
        cyclic.put("shaders/gbuffers_basic.fsh", "#include \"include/a.glsl\"\n");
        cyclic.put("shaders/include/a.glsl", "#include \"b.glsl\"\n");
        cyclic.put("shaders/include/b.glsl", "#include \"a.glsl\"\n");
        ShaderpackIR cyclicIr = new ShaderpackLoader("cyclic", provider(cyclic))
                .load(new ArrayList<>(cyclic.keySet()));
        check(cyclicIr.hasErrors(), "cycle produces an error");
        check(cyclicIr.errors().stream().anyMatch(d -> d.message().contains("cycle")),
                "some error mentions the cycle: " + cyclicIr.errors());
        check(cyclicIr.errors().stream().noneMatch(d -> d.message().contains("not found")),
                "no spurious missing-include error: " + cyclicIr.errors());

        // 无程序源 → ERROR
        Map<String, String> empty = new LinkedHashMap<>();
        empty.put("shaders/shaders.properties", "clouds=true\n");
        ShaderpackIR emptyIr = new ShaderpackLoader("empty", provider(empty))
                .load(new ArrayList<>(empty.keySet()));
        check(emptyIr.hasErrors(), "empty pack reports an error");

        // 未知程序基名 → WARNING（不致命）
        Map<String, String> unknown = new LinkedHashMap<>();
        unknown.put("shaders/gbuffers_basic.vsh", "#version 330\n");
        unknown.put("shaders/gbuffers_basic.fsh", "#version 330\n");
        unknown.put("shaders/gbuffers_mystery.fsh", "#version 330\n");
        ShaderpackIR unknownIr = new ShaderpackLoader("unknown", provider(unknown))
                .load(new ArrayList<>(unknown.keySet()));
        check(!unknownIr.hasErrors(), "unknown program is only a warning");
        check(unknownIr.warnings().stream().anyMatch(d -> d.message().contains("gbuffers_mystery")),
                "warning names the unrecognised program");
    }

    private static void testUniformsAndFlags() {
        Map<String, String> files = fullPack();
        ShaderpackIR ir = new ShaderpackLoader("fixture", provider(files))
                .load(new ArrayList<>(files.keySet()));

        checkEquals(2, ir.customUniforms().size(), "two custom uniforms");
        LuminCustomUniform sunHeight = ir.customUniforms().stream()
                .filter(u -> u.name().equals("sunHeight")).findFirst().orElse(null);
        check(sunHeight != null, "uniform.float.sunHeight parsed");
        checkEquals(LuminCustomUniform.ValueType.FLOAT, sunHeight.type(), "uniform type");
        check(sunHeight.exposed(), "uniform.* is exposed");
        check(sunHeight.references().contains("worldTime"), "expression references scanned");

        LuminCustomUniform mode = ir.customUniforms().stream()
                .filter(u -> u.name().equals("mode")).findFirst().orElse(null);
        check(mode != null && !mode.exposed(), "variable.* is not exposed");

        checkEquals(2, ir.requiredFeatures().size(), "two required flags");
        check(ir.requiredFeatures().contains(LuminFeatureFlag.COMPUTE_SHADERS), "required compute");
        check(ir.requiredFeatures().contains(LuminFeatureFlag.SSBO), "required ssbo");
        check(ir.optionalFeatures().contains(LuminFeatureFlag.CUSTOM_IMAGES), "optional images");
    }

    private LuminShaderpackM1SelfTest() {
    }
}
