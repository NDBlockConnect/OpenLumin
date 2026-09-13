package io.github.openlumin.chunk;

import io.github.openlumin.chunk.store.LuminArenaAllocator;
import io.github.openlumin.chunk.store.LuminStagingRing;

import java.util.List;

/**
 * WP-1 M4c 自测：共享 arena（best-fit + 增量碎片整理）与 staging 环（回绕拆段/提交回收）。
 * 聚合 M4b（含 M4a/M3/M2/M1）全量回归。
 */
public final class LuminChunkM4cSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M4c 全部测试节（先聚合 M4b+M4a+M3+M2+M1 回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM4bSelfTest.runAll();
        section("arena best-fit allocation", LuminChunkM4cSelfTest::testBestFit);
        section("arena free and reuse", LuminChunkM4cSelfTest::testFreeReuse);
        section("arena defragmentation", LuminChunkM4cSelfTest::testDefrag);
        section("staging ring wraparound", LuminChunkM4cSelfTest::testWraparound);
        section("staging ring reclaim", LuminChunkM4cSelfTest::testReclaim);
        if (failures > 0) {
            System.err.println("[lumin-chunk M4c] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-chunk M4c] ALL SELF TESTS PASSED (M1..M4c)");
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

    private static void testBestFit() {
        LuminArenaAllocator arena = new LuminArenaAllocator(1024);
        // 先造成两个不同尺寸的空闲洞：A=64，B=256
        LuminArenaAllocator.Segment a = arena.allocate(64);
        LuminArenaAllocator.Segment b = arena.allocate(256);
        LuminArenaAllocator.Segment c = arena.allocate(64);
        arena.free(b);
        // 现在空闲 = [64..320)（256），[384..1024)（640）
        // best-fit 应选择最小可容纳洞：请求 100 应落在 256 的洞里而不是 640
        LuminArenaAllocator.Segment fit = arena.allocate(100);
        check(fit != null && fit.offset() == 64, "best-fit picks smallest sufficient hole, got offset " + (fit == null ? -1 : fit.offset()));
        check(a.offset() == 0 && c.offset() == 320, "adjacent segments intact");
        // 请求 700（大于最大洞）应失败
        check(arena.allocate(700) == null, "oversized request fails");
        arena.free(a);
        arena.free(c);
        arena.free(fit);
        check(arena.usedBytes() == 0 && arena.usedSegmentCount() == 0, "full drain");
    }

    private static void testFreeReuse() {
        LuminArenaAllocator arena = new LuminArenaAllocator(512);
        LuminArenaAllocator.Segment s0 = arena.allocate(128);
        LuminArenaAllocator.Segment s1 = arena.allocate(128);
        LuminArenaAllocator.Segment s2 = arena.allocate(128);
        check(s0.offset() == 0 && s1.offset() == 128 && s2.offset() == 256, "sequential layout");
        arena.free(s1);
        check(arena.freeBytes() == 256, "hole plus tail free");
        // 复用洞
        LuminArenaAllocator.Segment reuse = arena.allocate(64);
        check(reuse.offset() == 128, "hole reused at 128");
        // 释放相邻段触发合并：s0 + reuse(64) + [192..256) 尾 + s2 后空间
        arena.free(s0);
        arena.free(reuse);
        check(arena.freeBytes() == 512 - 128, "coalescing restores free bytes");
        // 合并后低区 [0,256) 连续：256 可分配，384 不可（s2 仍占 [256,384)）
        LuminArenaAllocator.Segment big = arena.allocate(256);
        check(big != null && big.offset() == 0, "coalesced 256 region satisfied from 0");
        check(arena.allocate(384) == null, "384 rejected while s2 holds [256,384)");
    }

    private static void testDefrag() {
        LuminArenaAllocator arena = new LuminArenaAllocator(1024);
        // 布局：A(100) B(100) C(100) D(100)，然后释放 A、C → 两个 100 洞 + 尾部 624
        LuminArenaAllocator.Segment a = arena.allocate(100);
        LuminArenaAllocator.Segment b = arena.allocate(100);
        LuminArenaAllocator.Segment c = arena.allocate(100);
        LuminArenaAllocator.Segment d = arena.allocate(100);
        check(a.offset() == 0 && b.offset() == 100 && c.offset() == 200 && d.offset() == 300, "layout sanity");
        arena.free(a);
        arena.free(c);
        // 碎片：free = [0,100)+[200,100)+[400,624)；used = b(100@100) + d(100@300)
        List<LuminArenaAllocator.Move> moves = arena.defragment(8, 4096);
        check(!moves.isEmpty(), "defrag should produce moves");
        // 搬移后：所有已用段应比原来更靠前（向低地址合并），且最终可分配 824 连续
        check(arena.usedBytes() == 200, "used bytes unchanged by defrag");
        LuminArenaAllocator.Segment e = arena.allocate(824);
        check(e != null, "defrag must consolidate enough for an 824-byte allocation, got null");
        if (e != null) {
            arena.free(e);
        }
        // 预算约束：maxCopies=1 时只搬一段
        LuminArenaAllocator arena2 = new LuminArenaAllocator(1024);
        LuminArenaAllocator.Segment p0 = arena2.allocate(100);
        LuminArenaAllocator.Segment p1 = arena2.allocate(100);
        LuminArenaAllocator.Segment p2 = arena2.allocate(100);
        arena2.free(p0);
        arena2.free(p2);
        List<LuminArenaAllocator.Move> budgeted = arena2.defragment(1, 4096);
        check(budgeted.size() <= 1, "maxCopies=1 bounds the move count, got " + budgeted.size());
        arena2.free(p1);
    }

    private static void testWraparound() {
        LuminStagingRing ring = new LuminStagingRing(1024);
        // 分配 960 → 游标推进到 960
        LuminStagingRing.Allocation first = ring.allocate(960);
        check(first != null && first.segmentCount() == 1 && first.offset(0) == 0, "initial head allocation");
        ring.reclaim(first.submitId());
        // 游标位于 960：请求 100 → 尾部仅剩 64，跨尾拆为 64 + 36
        LuminStagingRing.Allocation wrapped = ring.allocate(100);
        check(wrapped != null && wrapped.segmentCount() == 2, "cross-tail split into two segments");
        check(wrapped.offset(0) == 960 && wrapped.length(0) == 64, "tail part 64 bytes at 960");
        check(wrapped.offset(1) == 0 && wrapped.length(1) == 36, "head part 36 bytes at 0");
        check(wrapped.totalBytes() == 100, "total preserved");
        ring.reclaim(wrapped.submitId());
        check(ring.inFlightBytes() == 0, "wrapped allocation reclaimable");
    }

    private static void testReclaim() {
        LuminStagingRing ring = new LuminStagingRing(1024);
        LuminStagingRing.Allocation a1 = ring.allocate(200);
        a1.submitId();
        LuminStagingRing.Allocation a2 = ring.allocate(200);
        check(ring.inFlightBytes() == 400, "two allocations in flight");
        // 容量约束：已用 400，再分配 700 会超过物理容量 1024 → null
        check(ring.allocate(700) == null, "capacity bound enforced");
        check(ring.allocate(624) != null, "up to capacity succeeds");
        // 回收 a1 之前的全部（含 a1/a2）
        long reclaimed = ring.reclaim(a2.submitId());
        check(reclaimed == 400, "reclaim through a2");
        check(ring.inFlightBytes() == 624, "in flight reduced to the last allocation");
        // 上限建议值（供帧预算用）
        check(ring.uploadByteLimit() == 819, "80% advisory limit, got " + ring.uploadByteLimit());
        // 全部回收
        ring.reclaim(Long.MAX_VALUE);
        check(ring.inFlightBytes() == 0, "full reclaim");
    }

    private LuminChunkM4cSelfTest() {
    }
}
