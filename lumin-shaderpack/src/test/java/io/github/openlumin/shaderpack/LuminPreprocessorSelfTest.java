package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.parse.IncludeGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackPreprocessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP-2 预处理自测（聚合入口）：include 展开 + #version/#extension 回填 + 选项注入。
 * 全部使用合成夹具，纯 CPU。
 */
public final class LuminPreprocessorSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminCapabilitySelfTest.runAll();
        section("preprocess: include expansion", LuminPreprocessorSelfTest::testIncludeExpansion);
        section("preprocess: version/ext hoisting", LuminPreprocessorSelfTest::testHoisting);
        section("preprocess: conflicting versions", LuminPreprocessorSelfTest::testConflictingVersions);
        section("preprocess: option defines", LuminPreprocessorSelfTest::testOptionDefines);
        section("preprocess: missing include error", LuminPreprocessorSelfTest::testMissingInclude);
        section("preprocess: guards are inert", LuminPreprocessorSelfTest::testGuardsInert);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack preprocess] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack preprocess] ALL SELF TESTS PASSED (M1+M2+M6+preprocess)");
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

    private static IncludeGraph graphOf(Map<String, String> files) {
        IncludeGraph graph = new IncludeGraph(
                path -> files.get(IncludeGraph.normalize(path)));
        for (String path : files.keySet()) {
            graph.addRoot(path);
        }
        return graph;
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

    private static void testIncludeExpansion() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/main.fsh", "#include \"lib/common.glsl\"\nvoid main() { helper(); }\n");
        files.put("shaders/lib/common.glsl", "float helperValue = 1.0;\n");
        IncludeGraph graph = graphOf(files);
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/main.fsh", Map.of());
        check(!result.hasErrors(), "clean include must preprocess: " + result.diagnostics());
        check(result.source().contains("float helperValue = 1.0;"),
                "included content must be inlined");
        check(!result.source().contains("#include"),
                "include directives must be consumed");
    }

    private static void testHoisting() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/main.fsh",
                "#include \"lib/header.glsl\"\nvoid main() {}\n");
        files.put("shaders/lib/header.glsl",
                "#version 330\n#extension GL_ARB_shader_draw_parameters : enable\n");
        IncludeGraph graph = graphOf(files);
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/main.fsh", Map.of());
        String source = result.source();
        check(!result.hasErrors(), "hoisting must succeed: " + result.diagnostics());
        int versionIndex = source.indexOf("#version 330");
        int extensionIndex = source.indexOf("#extension");
        int bodyIndex = source.indexOf("void main()");
        check(versionIndex >= 0, "version must survive");
        check(extensionIndex > versionIndex, "extension must follow version");
        check(bodyIndex > extensionIndex, "body must follow directives");
        check(countOccurrences(source, "#version") == 1,
                "version must appear exactly once");
    }

    private static void testConflictingVersions() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/main.fsh", "#version 330\n#include \"lib/old.glsl\"\nvoid main() {}\n");
        files.put("shaders/lib/old.glsl", "#version 120\n");
        IncludeGraph graph = graphOf(files);
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/main.fsh", Map.of());
        check(result.hasErrors(), "conflicting versions must be an error");
        check(result.diagnostics().stream().anyMatch(
                        d -> d.message().contains("conflicting #version")),
                "error must name the conflict");
    }

    private static void testOptionDefines() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/composite1.fsh", "#version 330\nvoid main() {}\n");
        IncludeGraph graph = graphOf(files);
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("SHADOW_QUALITY", "2");
        defines.put("ALLOW_FADE", "");
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/composite1.fsh", defines);
        String source = result.source();
        check(source.contains("#define SHADOW_QUALITY 2"), "valued define must be injected");
        check(source.contains("#define ALLOW_FADE\n"), "bare define must be injected");
        check(source.indexOf("#define SHADOW_QUALITY") > source.indexOf("#version 330"),
                "defines must follow version");
        check(source.indexOf("#define SHADOW_QUALITY") < source.indexOf("void main()"),
                "defines must precede body");
    }

    private static void testMissingInclude() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/main.fsh", "#include \"lib/absent.glsl\"\nvoid main() {}\n");
        IncludeGraph graph = graphOf(files);
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/main.fsh", Map.of());
        check(result.hasErrors(), "missing include must be an error");
        check(result.diagnostics().stream().anyMatch(
                        d -> d.message().contains("included file not found")),
                "error must name the missing file");
    }

    /**
     * include guard 无效（与 Iris 语义一致）：同一文件被包含两次时文本出现两次。
     * 本测试固定该语义，防止后续误加"智能去重"。
     */
    private static void testGuardsInert() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/main.fsh",
                "#include \"lib/g.glsl\"\n#include \"lib/g.glsl\"\nvoid main() {}\n");
        files.put("shaders/lib/g.glsl",
                "#ifndef GUARD_G\n#define GUARD_G\nfloat gValue = 1.0;\n#endif\n");
        IncludeGraph graph = graphOf(files);
        ShaderpackPreprocessor.Result result = ShaderpackPreprocessor.preprocess(
                graph, "shaders/main.fsh", Map.of());
        check(countOccurrences(result.source(), "float gValue = 1.0;") == 2,
                "duplicate include must duplicate text (guards are inert)");
    }

    private LuminPreprocessorSelfTest() {
    }
}
