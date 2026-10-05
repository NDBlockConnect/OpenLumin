package io.github.openlumin.shaderpack.exec;

import com.mojang.blaze3d.textures.TextureFormat;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminBufferFormat;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.compile.LuminShaderpackM3SelfTest;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.plan.LuminResourceAllocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP-2 M4 执行层自测（聚合入口，无 GPU 依赖的部分）：
 * 格式降级映射、清屏色解析、ARGB 打包、MRT 主目标定序。GPU 路径由游戏内验证。
 */
public final class ShaderpackExecSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = LuminShaderpackM3SelfTest.runAll();
        section("exec: format mapping", ShaderpackExecSelfTest::testFormatMapping);
        section("exec: clear color resolution", ShaderpackExecSelfTest::testClearColors);
        section("exec: argb packing", ShaderpackExecSelfTest::testArgbPacking);
        section("exec: primary write ordering", ShaderpackExecSelfTest::testPrimaryWrite);
        section("exec: final color copy resolution", ShaderpackExecSelfTest::testFinalColorCopy);
        section("exec: target usage flags", ShaderpackExecSelfTest::testTargetUsage);
        section("exec: source registry fallback", ShaderpackExecSelfTest::testSourceRegistry);
        section("exec: fullscreen triangle args", ShaderpackExecSelfTest::testFullscreenTriangle);
        if (failures > 0) {
            System.err.println("[lumin-shaderpack exec] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-shaderpack exec] ALL SELF TESTS PASSED (M3 + exec)");
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

    private static void testFormatMapping() {
        List<Diagnostic> diagnostics = new ArrayList<>();
        check(ShaderpackTargets.mapTextureFormat(LuminTargetId.color(0),
                        LuminBufferFormat.RGBA8, diagnostics) == TextureFormat.RGBA8,
                "RGBA8 stays RGBA8");
        check(ShaderpackTargets.mapTextureFormat(LuminTargetId.color(1),
                        LuminBufferFormat.R8, diagnostics) == TextureFormat.RED8,
                "R8 maps to RED8");
        check(ShaderpackTargets.mapTextureFormat(LuminTargetId.color(2),
                        LuminBufferFormat.RGBA16F, diagnostics) == TextureFormat.RGBA8,
                "float format degrades to RGBA8");
        check(diagnostics.stream().anyMatch(d ->
                        d.severity() == Diagnostic.Severity.WARNING
                                && d.message().contains("RGBA16F")),
                "degradation must warn");
        check(ShaderpackTargets.mapTextureFormat(LuminTargetId.depth(0),
                        LuminBufferFormat.R32F, diagnostics) == TextureFormat.DEPTH32,
                "depth target maps to DEPTH32");
    }

    private static void testClearColors() {
        int fog = 0x11223344;
        check(ShaderpackFrameExecutor.resolveClearColor(
                        LuminResourceAllocation.ClearColor.fog(), fog) == fog,
                "fog color passthrough");
        check(ShaderpackFrameExecutor.resolveClearColor(
                        LuminResourceAllocation.ClearColor.white(), fog) == 0xFFFFFFFF,
                "white constant");
        check(ShaderpackFrameExecutor.resolveClearColor(
                        LuminResourceAllocation.ClearColor.transparentBlack(), fog) == 0,
                "transparent black constant");
        int literal = ShaderpackFrameExecutor.resolveClearColor(
                LuminResourceAllocation.ClearColor.literal(new float[]{1f, 0.5f, 0f, 1f}), fog);
        check(literal == 0xFFFF8000, "literal packs to ARGB, got " + Integer.toHexString(literal));
    }

    private static void testArgbPacking() {
        check(ShaderpackFrameExecutor.packArgb(new float[]{0f, 0f, 0f, 0f}) == 0,
                "black transparent");
        check(ShaderpackFrameExecutor.packArgb(new float[]{1f, 1f, 1f, 1f}) == 0xFFFFFFFF,
                "white opaque");
        // r=2 → 255(0xFF0000)，g=-1 → 0，a=1 → 0xFF000000
        check(ShaderpackFrameExecutor.packArgb(new float[]{2f, -1f, 0f, 1f}) == 0xFFFF0000,
                "channels clamp to [0,255]");
        check(ShaderpackFrameExecutor.packArgb(new float[]{0f, 0f, 1f, 0.5f}) == 0x800000FF,
                "alpha and blue placement");
    }

    private static void testPrimaryWrite() {
        Map<LuminTargetId, LuminResourceId.Copy> writes = new LinkedHashMap<>();
        writes.put(LuminTargetId.color(3), LuminResourceId.Copy.MAIN);
        writes.put(LuminTargetId.color(1), LuminResourceId.Copy.ALT);
        check(ShaderpackFrameExecutor.primaryWriteTarget(writes).equals(LuminTargetId.color(1)),
                "lowest color index wins");
        writes.put(LuminTargetId.depth(0), LuminResourceId.Copy.MAIN);
        check(ShaderpackFrameExecutor.primaryWriteTarget(writes).equals(LuminTargetId.color(1)),
                "color kind outranks depth kind");
        check(ShaderpackFrameExecutor.primaryWriteTarget(Map.of()) == null,
                "empty write set yields null");
    }

    /** 呈现拷贝的源副本 = colortex0 最后一次写入的副本；从未写入则 MAIN。 */
    private static void testFinalColorCopy() {
        check(ShaderpackFrameExecutor.finalColorCopy(
                        new io.github.openlumin.shaderpack.plan.LuminFramePlan(List.of()),
                        LuminTargetId.color(0)) == LuminResourceId.Copy.MAIN,
                "never-written colortex0 resolves to MAIN");

        // 三 pass 依次写 colortex0：ALT → MAIN → ALT，最终应为 ALT
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders/gbuffers_terrain.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/composite1.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        files.put("shaders/final.fsh", "/* DRAWBUFFERS:0 */\nvoid main(){}\n");
        io.github.openlumin.shaderpack.ShaderpackIR ir =
                new io.github.openlumin.shaderpack.parse.ShaderpackLoader("fixture",
                        path -> files.get(io.github.openlumin.shaderpack.parse.IncludeGraph
                                .normalize(path)))
                        .load(new ArrayList<>(files.keySet()));
        check(!ir.hasErrors(), "fixture must parse: " + ir.errors());
        var graph = io.github.openlumin.shaderpack.graph.PassGraph.from(ir);
        var resources = io.github.openlumin.shaderpack.plan.LuminResourcePlanner.plan(ir, graph);
        var frame = io.github.openlumin.shaderpack.plan.LuminFramePlanner.plan(graph, resources);
        check(ShaderpackFrameExecutor.finalColorCopy(frame, LuminTargetId.color(0))
                        == LuminResourceId.Copy.ALT,
                "last writer's copy wins (ALT after two flips)");
    }

    /** 目标纹理 usage：与 vanilla RenderTarget 相同（15），缺 COPY_DST 会使清屏/拷贝失败。 */
    private static void testTargetUsage() {
        int usage = ShaderpackTargets.TARGET_USAGE;
        check((usage & com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST) != 0,
                "COPY_DST required (clearColorTexture enforces it at runtime)");
        check((usage & com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_SRC) != 0,
                "COPY_SRC required for present copies");
        check((usage & com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING) != 0,
                "TEXTURE_BINDING required for sampling");
        check((usage & com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT) != 0,
                "RENDER_ATTACHMENT required for pass attachments");
        check(usage == 15, "usage set must match vanilla RenderTarget (15), got " + usage);
    }

    /** 源注册表：内置着色器经类加载器兜底解析；注册表优先；未知类型返回 null。 */
    private static void testSourceRegistry() {
        check(io.github.openlumin.shaderpack.host.ShaderpackSourceRegistry
                        .extensionOf(com.mojang.blaze3d.shaders.ShaderType.VERTEX)
                        .equals(".vsh"),
                "vertex extension mapping");
        check(io.github.openlumin.shaderpack.host.ShaderpackSourceRegistry
                        .extensionOf(com.mojang.blaze3d.shaders.ShaderType.FRAGMENT)
                        .equals(".fsh"),
                "fragment extension mapping");
        String source = io.github.openlumin.shaderpack.host.ShaderpackSourceRegistry.resolve(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("openlumin", "rectangle"),
                com.mojang.blaze3d.shaders.ShaderType.VERTEX);
        check(source != null && source.contains("#version"),
                "built-in shader must resolve via classloader fallback");
        check(source != null && !source.contains("#moj_import"),
                "moj_import directives must be expanded by the registry");
        check(source != null && source.contains("DynamicTransforms"),
                "expanded include content must be inlined");
        check(source != null && source.lines()
                        .filter(line -> line.trim().startsWith("#version")).count() == 1,
                "nested include #version lines must be stripped (exactly one remains)");
        check(io.github.openlumin.shaderpack.host.ShaderpackSourceRegistry.resolve(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("openlumin", "does_not_exist"),
                        com.mojang.blaze3d.shaders.ShaderType.VERTEX) == null,
                "unknown shader resolves to null (falls through to vanilla)");
    }

    /** 绘制参数锁定：26.1.2 draw(firstVertex, vertexCount)，vanilla PostPass 为 draw(0, 3)。 */
    private static void testFullscreenTriangle() {
        check(ShaderpackFrameExecutor.FULLSCREEN_FIRST_VERTEX == 0,
                "first vertex must be 0");
        check(ShaderpackFrameExecutor.FULLSCREEN_VERTEX_COUNT == 3,
                "and vertex count 3 (gl_VertexID fullscreen triangle)");
    }

    private ShaderpackExecSelfTest() {
    }
}
