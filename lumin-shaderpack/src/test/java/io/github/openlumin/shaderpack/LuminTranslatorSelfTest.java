package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.translate.ShaderpackTranslator;

/**
 * WP-2 翻译层自测（聚合入口）：legacy GLSL → 330 的结构化翻译。
 * 全部合成夹具，纯 CPU。
 */
public final class LuminTranslatorSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminResourcePlanSelfTest.runAll();
        section("translate: legacy vertex", LuminTranslatorSelfTest::testLegacyVertex);
        section("translate: legacy fragment", LuminTranslatorSelfTest::testLegacyFragment);
        section("translate: multi draw outputs", LuminTranslatorSelfTest::testMultiOutputs);
        section("translate: modern passthrough", LuminTranslatorSelfTest::testModernPassthrough);
        section("translate: unmappable warning", LuminTranslatorSelfTest::testUnmappable);
        section("translate: comments protected", LuminTranslatorSelfTest::testComments);
        section("translate: no duplicate declarations", LuminTranslatorSelfTest::testNoDuplicates);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack translate] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack translate] ALL SELF TESTS PASSED "
                + "(M1+M2+M6+preprocess+plan+translate)");
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void testLegacyVertex() {
        String source = """
                #version 120
                varying vec2 texcoord;
                void main() {
                    gl_Position = ftransform();
                    texcoord = gl_MultiTexCoord0.xy;
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.VERTEX, 1);
        String out = result.source();
        check(result.translated(), "legacy source must be translated");
        check(out.startsWith("#version 330"), "version must be normalized, got: "
                + out.substring(0, Math.min(30, out.length())));
        check(countOccurrences(out, "#version") == 1, "single version directive");
        check(out.contains("uniform DynamicTransforms"), "UBO block injected for ftransform");
        check(out.contains("uniform Projection"), "Projection block injected");
        check(out.contains("in vec3 Position;"), "Position attribute declared");
        check(out.contains("in vec2 UV0;"), "UV0 attribute declared");
        check(out.contains("out vec2 texcoord;"), "varying becomes out in vertex stage");
        check(out.contains("(ProjMat * ModelViewMat) * vec4(Position, 1.0)"),
                "ftransform mapped to matrix multiply");
        check(!out.contains("ftransform"), "ftransform call consumed");
    }

    private static void testLegacyFragment() {
        String source = """
                #version 120
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                void main() {
                    vec4 c = texture2D(colortex0, texcoord);
                    gl_FragColor = c;
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.FRAGMENT, 1);
        String out = result.source();
        check(result.translated(), "legacy fragment must be translated");
        check(out.contains("in vec2 texcoord;"), "varying becomes in in fragment stage");
        check(out.contains("out vec4 fragColor;"), "fragment output declared");
        check(out.contains("texture(colortex0, texcoord)"), "texture2D mapped");
        check(out.contains("fragColor = c;"), "gl_FragColor mapped");
    }

    private static void testMultiOutputs() {
        String source = """
                #version 120
                void main() {
                    gl_FragData[0] = vec4(1.0);
                    gl_FragData[2] = vec4(0.0);
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.FRAGMENT, 3);
        String out = result.source();
        check(out.contains("out vec4 fragData0;"), "fragData0 declared");
        check(out.contains("out vec4 fragData2;"), "fragData2 declared");
        check(out.contains("fragData0 = vec4(1.0);"), "indexed write mapped");
        check(!out.contains("gl_FragData"), "gl_FragData consumed");
    }

    private static void testModernPassthrough() {
        String source = """
                #version 330
                in vec3 Position;
                void main() {
                    gl_Position = vec4(Position, 1.0);
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.VERTEX, 1);
        check(!result.translated(), "modern source must pass through unchanged");
        check(result.source().equals(source), "passthrough must be byte-identical");
    }

    private static void testUnmappable() {
        String source = """
                #version 120
                varying float fog;
                void main() {
                    fog = gl_FogFragCoord;
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.FRAGMENT, 1);
        check(result.translated(), "source with legacy markers is translated");
        check(result.diagnostics().stream().anyMatch(d ->
                        d.severity() == Diagnostic.Severity.WARNING
                                && d.message().contains("gl_FogFragCoord")),
                "unmappable builtin must produce a warning");
    }

    private static void testComments() {
        String commentOnly = """
                #version 330
                // legacy note: gl_FragColor was used here historically
                void main() { }
                """;
        ShaderpackTranslator.Result passthrough = ShaderpackTranslator.translate(
                commentOnly, LuminShaderKind.FRAGMENT, 1);
        check(!passthrough.translated(),
                "comment-only legacy mentions must not trigger translation");

        String legacyWithComment = """
                #version 120
                // keep gl_FragColor in this comment
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """;
        ShaderpackTranslator.Result translated = ShaderpackTranslator.translate(
                legacyWithComment, LuminShaderKind.FRAGMENT, 1);
        check(translated.source().contains("// keep gl_FragColor in this comment"),
                "comment content must be preserved verbatim");
        check(translated.source().contains("fragColor = vec4(1.0);"),
                "code outside comments must be rewritten");
    }

    private static void testNoDuplicates() {
        String source = """
                #version 120
                in vec3 Position;
                void main() {
                    gl_Position = vec4(gl_Vertex, 1.0);
                }
                """;
        ShaderpackTranslator.Result result = ShaderpackTranslator.translate(
                source, LuminShaderKind.VERTEX, 1);
        check(countOccurrences(result.source(), "in vec3 Position;") == 1,
                "already-declared attribute must not be declared twice");
    }

    private LuminTranslatorSelfTest() {
    }
}
