package io.github.openlumin.chunk;

import io.github.openlumin.chunk.batch.LuminBatchCapabilities;
import io.github.openlumin.chunk.batch.LuminBatchPath;
import io.github.openlumin.chunk.batch.LuminBatchPlanner;
import io.github.openlumin.chunk.batch.LuminBatchTraits;

/**
 * WP-1 M5b 自测：批量路径选择的回退链（能力探测 → 路径决策 → 语义等价不变量）。
 * 聚合 M5a（含 M1..M4c）全量回归。
 */
public final class LuminChunkM5bSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M5b 全部测试节（先聚合 M5a 及以下回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM5aSelfTest.runAll();
        section("single draw never batches", LuminChunkM5bSelfTest::testSingleDraw);
        section("baseline capability chain", LuminChunkM5bSelfTest::testBaselineChain);
        section("full capability prefers strongest", LuminChunkM5bSelfTest::testFullCapability);
        section("mixed traits exclude direct shapes", LuminChunkM5bSelfTest::testMixedTraits);
        section("indirect requires readiness", LuminChunkM5bSelfTest::testIndirectReadiness);
        section("no capability falls back to per-draw", LuminChunkM5bSelfTest::testNoCapability);
        section("planner invariants", LuminChunkM5bSelfTest::testInvariants);
        if (failures > 0) {
            System.err.println("[lumin-chunk M5b] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-chunk M5b] ALL SELF TESTS PASSED (M1..M5b)");
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

    private static void testSingleDraw() {
        // 无论能力多强，单 draw 都走逐 draw（无批量收益）
        check(LuminBatchPlanner.plan(LuminBatchCapabilities.full(), LuminBatchTraits.single())
                        == LuminBatchPath.PER_DRAW,
                "single draw always per-draw");
        check(LuminBatchPlanner.plan(LuminBatchCapabilities.full(),
                        new LuminBatchTraits(1, false, false, false, false, true, true))
                        == LuminBatchPath.PER_DRAW,
                "single draw per-draw even with indirect ready and mixed traits");
    }

    private static void testBaselineChain() {
        // 仅有聚合通路的保守能力：共享 IBO 批次 → 聚合；混杂批次 → 聚合（聚合通路本就支持混杂）
        LuminBatchCapabilities baseline = LuminBatchCapabilities.baseline();
        check(LuminBatchPlanner.plan(baseline, LuminBatchTraits.uniformRegionBatch(64))
                        == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "baseline picks aggregated path");
        check(LuminBatchPlanner.plan(baseline, LuminBatchTraits.mixed(64))
                        == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "baseline aggregated path also handles mixed batches");
    }

    private static void testFullCapability() {
        LuminBatchCapabilities full = LuminBatchCapabilities.full();
        // 无 indirect 命令就绪 → 直接形态取最强（交错优先于分离）
        check(LuminBatchPlanner.plan(full, LuminBatchTraits.uniformRegionBatch(64))
                        == LuminBatchPath.MULTI_DRAW_INTERLEAVED,
                "interleaved preferred over separate when both supported");
        // 仅分离可用（GL 典型）→ 分离
        LuminBatchCapabilities separateOnly = new LuminBatchCapabilities(true, false, true, false, false, false);
        check(LuminBatchPlanner.plan(separateOnly, LuminBatchTraits.uniformRegionBatch(64))
                        == LuminBatchPath.MULTI_DRAW_SEPARATE,
                "separate chosen when interleaved unavailable");
        // 仅聚合可用 → 聚合
        LuminBatchCapabilities aggOnly = new LuminBatchCapabilities(true, false, false, false, false, false);
        check(LuminBatchPlanner.plan(aggOnly, LuminBatchTraits.uniformRegionBatch(64))
                        == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "aggregated chosen when no direct multi-draw");
    }

    private static void testMixedTraits() {
        // 混杂批次（不共享 IBO、有 per-draw uniform）不能走直接形态，即使设备支持
        LuminBatchCapabilities full = LuminBatchCapabilities.full();
        LuminBatchPath path = LuminBatchPlanner.plan(full, LuminBatchTraits.mixed(32));
        check(path == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "mixed batch falls back to aggregated even with multi-draw support, got " + path);
        // per-draw uniform 单独即可排除直接形态
        LuminBatchTraits perDraw = new LuminBatchTraits(32, true, true, true, true, true, false);
        check(LuminBatchPlanner.plan(full, perDraw) == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "per-draw uniforms exclude direct multi-draw shapes");
        // 索引类型不一致同样排除
        LuminBatchTraits mixedTypes = new LuminBatchTraits(32, true, false, true, true, false, false);
        check(LuminBatchPlanner.plan(full, mixedTypes) == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "mixed index types exclude direct multi-draw shapes");
    }

    private static void testIndirectReadiness() {
        LuminBatchCapabilities indirectCapable = new LuminBatchCapabilities(true, true, true, true, true, false);
        // 命令未就绪 → 退到直接形态
        check(LuminBatchPlanner.plan(indirectCapable, LuminBatchTraits.uniformRegionBatch(16))
                        == LuminBatchPath.MULTI_DRAW_INTERLEAVED,
                "indirect not ready -> strongest direct shape");
        // 命令就绪 → 间接优先（即使批次为混杂：间接形态自携 per-draw 命令）
        LuminBatchTraits ready = new LuminBatchTraits(16, true, true, true, true, false, true);
        check(LuminBatchPlanner.plan(indirectCapable, ready) == LuminBatchPath.MULTI_DRAW_INDIRECT,
                "indirect preferred when commands are ready");
        LuminBatchTraits readyMixed = new LuminBatchTraits(16, false, false, false, false, true, true);
        check(LuminBatchPlanner.plan(indirectCapable, readyMixed) == LuminBatchPath.MULTI_DRAW_INDIRECT,
                "indirect path covers mixed batches too");
        // 设备不支持 indirect → 即使命令就绪也不选
        LuminBatchCapabilities noIndirect = new LuminBatchCapabilities(true, true, true, false, true, false);
        check(LuminBatchPlanner.plan(noIndirect, readyMixed) == LuminBatchPath.DRAW_MULTIPLE_INDEXED,
                "no indirect capability -> aggregated fallback");
    }

    private static void testNoCapability() {
        // 极端：设备什么都不支持（连聚合都没有）→ 逐 draw
        LuminBatchCapabilities none = new LuminBatchCapabilities(false, false, false, false, false, false);
        check(LuminBatchPlanner.plan(none, LuminBatchTraits.uniformRegionBatch(8)) == LuminBatchPath.PER_DRAW,
                "no capability falls back to per-draw");
        check(LuminBatchPlanner.plan(none, LuminBatchTraits.mixed(8)) == LuminBatchPath.PER_DRAW,
                "no capability falls back to per-draw for mixed batches");
    }

    private static void testInvariants() {
        // 不变量：共享 IBO/类型 + 统一实例数 + 无 per-draw uniform 时，选中的直接形态必须
        // 在该设备能力下可行；否则必须退化到聚合或逐 draw。
        LuminBatchCapabilities[] capabilities = {
                LuminBatchCapabilities.baseline(),
                LuminBatchCapabilities.full(),
                new LuminBatchCapabilities(true, false, true, false, false, false),
                new LuminBatchCapabilities(true, true, false, true, false, false),
                new LuminBatchCapabilities(false, false, false, false, false, false),
        };
        LuminBatchTraits[] traits = {
                LuminBatchTraits.single(),
                LuminBatchTraits.uniformRegionBatch(2),
                LuminBatchTraits.uniformRegionBatch(4096),
                LuminBatchTraits.mixed(128),
                new LuminBatchTraits(128, true, true, true, false, false, false),
        };
        for (LuminBatchCapabilities capability : capabilities) {
            for (LuminBatchTraits trait : traits) {
                LuminBatchPath path = LuminBatchPlanner.plan(capability, trait);
                check(path != null, "planner never returns null");
                switch (path) {
                    case MULTI_DRAW_INDIRECT -> check(
                            capability.multiDrawIndirect() && trait.indirectCommandsReady() && trait.drawCount() > 1,
                            "indirect path requires capability, readiness and >1 draw");
                    case MULTI_DRAW_INTERLEAVED -> check(
                            capability.multiDrawDirectInterleaved() && trait.drawCount() > 1
                                    && trait.sharedIndexBuffer() && trait.sharedIndexType()
                                    && trait.uniformInstanceCount() && !trait.perDrawUniforms(),
                            "interleaved path requires its capability and matching traits");
                    case MULTI_DRAW_SEPARATE -> check(
                            capability.multiDrawDirectSeparate() && trait.drawCount() > 1
                                    && trait.sharedIndexBuffer() && trait.sharedIndexType()
                                    && trait.uniformInstanceCount() && !trait.perDrawUniforms()
                                    && !capability.multiDrawDirectInterleaved(),
                            "separate path requires its capability, matching traits and no better direct shape");
                    case DRAW_MULTIPLE_INDEXED -> check(capability.drawMultipleIndexed() && trait.drawCount() > 1,
                            "aggregated path requires capability and >1 draw");
                    case PER_DRAW -> {
                    }
                    default -> throw new AssertionError("unexpected path " + path);
                }
                String description = LuminBatchPlanner.describe(path, capability, trait);
                check(description != null && !description.isEmpty(), "describe returns text");
            }
        }
        // 单调性：能力增强不应降到更弱的路径
        LuminBatchPath weak = LuminBatchPlanner.plan(new LuminBatchCapabilities(true, false, false, false, false, false),
                LuminBatchTraits.uniformRegionBatch(32));
        LuminBatchPath strong = LuminBatchPlanner.plan(LuminBatchCapabilities.full(),
                LuminBatchTraits.uniformRegionBatch(32));
        check(strong.ordinal() <= weak.ordinal(), "more capability never selects a weaker path");
    }

    private LuminChunkM5bSelfTest() {
    }
}
