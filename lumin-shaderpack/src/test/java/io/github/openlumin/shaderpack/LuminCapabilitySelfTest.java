package io.github.openlumin.shaderpack;

import io.github.openlumin.shaderpack.capability.LuminCapabilityNegotiator;
import io.github.openlumin.shaderpack.capability.LuminCapabilityReport;
import io.github.openlumin.shaderpack.capability.LuminCapabilitySet;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-2 M6 自测（聚合入口）：能力旗标协商 + M1/M2 回归。
 * 全部使用合成夹具，纯 CPU。
 */
public final class LuminCapabilitySelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminShaderpackM2SelfTest.runAll();
        section("capability: required satisfied", LuminCapabilitySelfTest::testRequiredSatisfied);
        section("capability: required missing", LuminCapabilitySelfTest::testRequiredMissing);
        section("capability: optional split", LuminCapabilitySelfTest::testOptionalSplit);
        section("capability: empty declarations", LuminCapabilitySelfTest::testEmptyDeclarations);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack M6] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack M6] ALL SELF TESTS PASSED (M1 + M2 + M6)");
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

    private static ShaderpackIR irWith(String features) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/shaders.properties", features);
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        return new ShaderpackLoader("fixture",
                path -> files.get(io.github.openlumin.shaderpack.parse.IncludeGraph.normalize(path)))
                .load(new ArrayList<>(files.keySet()));
    }

    private static void testRequiredSatisfied() {
        ShaderpackIR ir = irWith("iris.features.required = COMPUTE_SHADERS SSBO\n");
        check(ir.requiredFeatures().contains(LuminFeatureFlag.COMPUTE_SHADERS), "fixture required parsed");
        LuminCapabilityReport report = LuminCapabilityNegotiator.negotiate(ir,
                LuminCapabilitySet.of(LuminFeatureFlag.COMPUTE_SHADERS, LuminFeatureFlag.SSBO));
        check(report.accepted(), "all required satisfied must be accepted");
        check(report.missingRequired().isEmpty(), "no missing required");
    }

    private static void testRequiredMissing() {
        ShaderpackIR ir = irWith("iris.features.required = COMPUTE_SHADERS SSBO\n");
        LuminCapabilityReport report = LuminCapabilityNegotiator.negotiate(ir,
                LuminCapabilitySet.of(LuminFeatureFlag.COMPUTE_SHADERS));
        check(!report.accepted(), "missing required must reject");
        check(report.missingRequired().equals(java.util.Set.of(LuminFeatureFlag.SSBO)),
                "missing set must list SSBO, got " + report.missingRequired());
        check(report.describe().contains("SSBO"), "description must name the missing flag");
    }

    private static void testOptionalSplit() {
        ShaderpackIR ir = irWith("iris.features.optional = CUSTOM_IMAGES TEXTURE_FILTERING\n");
        LuminCapabilityReport report = LuminCapabilityNegotiator.negotiate(ir,
                LuminCapabilitySet.of(LuminFeatureFlag.TEXTURE_FILTERING));
        check(report.accepted(), "optional flags never reject");
        check(report.providedOptional().equals(java.util.Set.of(LuminFeatureFlag.TEXTURE_FILTERING)),
                "supported optional must be provided");
        check(report.unavailableOptional().equals(java.util.Set.of(LuminFeatureFlag.CUSTOM_IMAGES)),
                "unsupported optional must be flagged unavailable");
    }

    private static void testEmptyDeclarations() {
        ShaderpackIR ir = irWith("blur=true\n");
        LuminCapabilityReport report = LuminCapabilityNegotiator.negotiate(ir,
                LuminCapabilitySet.none());
        check(report.accepted(), "no declarations must be accepted by an empty engine");
        check(report.providedOptional().isEmpty() && report.unavailableOptional().isEmpty(),
                "no declarations must yield empty splits");
        check(report.describe().equals("all declared capabilities satisfied"),
                "clean report description");
    }

    private LuminCapabilitySelfTest() {
    }
}
