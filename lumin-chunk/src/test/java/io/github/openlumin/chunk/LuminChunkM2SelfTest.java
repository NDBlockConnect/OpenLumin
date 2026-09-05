package io.github.openlumin.chunk;

import io.github.openlumin.chunk.store.LuminChunkStoreLedger;
import io.github.openlumin.chunk.store.LuminRegionAllocator;
import io.github.openlumin.chunk.store.LuminSectionAllocation;

/**
 * WP-1 M2 自测：Store 账本层（first-fit 分配/对齐/合并/统计、region 生命周期幂等），
 * 并聚合 M1 全量回归。
 */
public final class LuminChunkM2SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M2 全部测试节（先聚合 M1 回归），返回失败节数（可被 M3 聚合运行器复用）。 */
    public static int runAll() {
        failures = LuminChunkM1SelfTest.runAll();
        section("allocator first-fit and alignment", LuminChunkM2SelfTest::testFirstFitAndAlignment);
        section("allocator free coalescing", LuminChunkM2SelfTest::testFreeCoalescing);
        section("allocator exhaustion and stats", LuminChunkM2SelfTest::testExhaustionAndStats);
        section("ledger lifecycle idempotence", LuminChunkM2SelfTest::testLedgerLifecycle);
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-30s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-30s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void testFirstFitAndAlignment() {
        LuminRegionAllocator allocator = new LuminRegionAllocator(1024);
        long a = allocator.allocate(100, 16);
        check(a == 0, "first allocation starts at 0, got " + a);
        check(allocator.usedBytes() == 100, "used=100 expected");
        long b = allocator.allocate(64, 16);
        check(b == 112, "second allocation must be 16-aligned (112), got " + b);
        long c = allocator.allocate(1, 256);
        check(c == 256, "256-aligned hole found at 256, got " + c);
        // 中间打洞后 first-fit 应复用最小可用洞
        allocator.free(b, 64);
        long d = allocator.allocate(32, 16);
        check(d == 112, "first-fit must reuse freed hole at 112, got " + d);
    }

    private static void testFreeCoalescing() {
        LuminRegionAllocator allocator = new LuminRegionAllocator(512);
        long a = allocator.allocate(100, 1);
        long b = allocator.allocate(100, 1);
        long c = allocator.allocate(100, 1);
        check(a == 0 && b == 100 && c == 200, "sequential bump allocations");
        // 释放中间块 → 独立洞
        allocator.free(b, 100);
        check(allocator.freeBytes() == 512 - 200, "one hole freed");
        // 释放后继块 → 与中间洞合并
        allocator.free(c, 100);
        // 释放前驱块 → 三块合并为一
        allocator.free(a, 100);
        check(allocator.usedBytes() == 0, "all freed");
        check(allocator.freeBytes() == 512, "full capacity must be coalesced back");
        // 合并后的整块应可一次性分配
        long d = allocator.allocate(512, 1);
        check(d == 0, "coalesced block must satisfy full-capacity allocation, got " + d);
        // 跨对齐边界的合并：分配-释放-分配序列
        allocator.free(d, 512);
        long e1 = allocator.allocate(10, 1);
        long e2 = allocator.allocate(10, 1);
        allocator.free(e1, 10);
        allocator.free(e2, 10);
        check(allocator.freeBytes() == 512, "paired free must fully coalesce");
        check(allocator.allocate(512, 64) == 0, "realigned full allocation after coalesce");
    }

    private static void testExhaustionAndStats() {
        LuminRegionAllocator allocator = new LuminRegionAllocator(256);
        check(allocator.allocate(512, 1) == -1, "oversized request must fail");
        long a = allocator.allocate(128, 1);
        long b = allocator.allocate(128, 1);
        check(a == 0 && b == 128, "exact capacity fill");
        check(allocator.allocate(1, 1) == -1, "exhausted allocator must fail");
        check(allocator.usedBytes() == 256 && allocator.freeBytes() == 0, "stats at full");
        allocator.free(a, 128);
        // 对齐 128 时剩余洞 [0,128) 无法给出 128 对齐起点（0 满足！）→ 应成功
        long c = allocator.allocate(64, 128);
        check(c == 0, "aligned hole at 0 must be found, got " + c);
        check(allocator.allocate(65, 1) == -1, "65 bytes cannot fit remaining 64");
    }

    private static void testLedgerLifecycle() {
        try (LuminChunkStoreLedger ledger = new LuminChunkStoreLedger()) {
            LuminChunkStoreLedger.RegionHandle h1 = ledger.acquire(0xABC, 4096, 1024);
            check(ledger.acquire(0xABC, 1, 1) == h1, "acquire must be idempotent for live handle");
            check(ledger.activeCount() == 1, "one active region");
            LuminSectionAllocation allocation = new LuminSectionAllocation(
                    h1.vertexAllocator().allocate(256, 4), 256,
                    h1.indexAllocator().allocate(64, 4), 64);
            check(allocation.vertexOffset() == 0 && allocation.indexOffset() == 0, "fresh region allocates at 0");

            ledger.retire(0xABC);
            ledger.retire(0xABC);
            check(h1.isRetired(), "handle must be retired");
            check(ledger.activeCount() == 0, "retired region must not count as active");
            try {
                h1.vertexAllocator().allocate(1, 1);
                check(false, "allocation on retired handle must throw");
            } catch (IllegalStateException expected) {
            }

            LuminChunkStoreLedger.RegionHandle h2 = ledger.acquire(0xABC, 8192, 2048);
            check(h2 != h1, "acquire after retire must rebuild the handle");
            check(!h2.isRetired() && ledger.activeCount() == 1, "rebuilt handle must be live");
            check(h2.vertexAllocator().capacity() == 8192, "rebuilt handle must use new capacity");
            check(h2.vertexAllocator().allocate(256, 4) == 0, "rebuilt allocator must be fresh");

            ledger.retire(0xDEF);
            check(ledger.activeCount() == 1, "retiring unknown key must be a no-op");
        }
    }

    private LuminChunkM2SelfTest() {
    }
}
