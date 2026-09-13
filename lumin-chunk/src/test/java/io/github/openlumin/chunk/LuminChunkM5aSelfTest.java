package io.github.openlumin.chunk;

import io.github.openlumin.chunk.cull.LuminCullTier;
import io.github.openlumin.chunk.cull.LuminMortonTree;
import io.github.openlumin.chunk.cull.LuminTraversableTree;
import io.github.openlumin.chunk.cull.LuminVisibilityRayTester;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * WP-1 M5a 自测：Morton 位树（交错/摘要/基数）、可遍历树（近到远子序、inside 标志、
 * 树复用、剪枝）、LOCAL 档射线测试。聚合 M4c（含 M1..M4b）全量回归。
 */
public final class LuminChunkM5aSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M5a 全部测试节（先聚合 M4c 及以下回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM4cSelfTest.runAll();
        section("morton interleave round trip", LuminChunkM5aSelfTest::testMortonInterleave);
        section("morton tree membership", LuminChunkM5aSelfTest::testMortonTree);
        section("child order near to far", LuminChunkM5aSelfTest::testChildOrder);
        section("tree reuse validity", LuminChunkM5aSelfTest::testTreeReuse);
        section("traversal visits all", LuminChunkM5aSelfTest::testTraversalCompleteness);
        section("traversal inside flags", LuminChunkM5aSelfTest::testInsideFlags);
        section("distance pruning", LuminChunkM5aSelfTest::testDistancePruning);
        section("ray tester", LuminChunkM5aSelfTest::testRayTester);
        if (failures > 0) {
            System.err.println("[lumin-chunk M5a] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-chunk M5a] ALL SELF TESTS PASSED (M1..M5a)");
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

    private static void testMortonInterleave() {
        // 交错/解交错往返（6 位/轴 全域采样）
        for (int x = 0; x < 64; x += 7) {
            for (int y = 0; y < 64; y += 11) {
                for (int z = 0; z < 64; z += 13) {
                    int code = LuminMortonTree.interleave(x, y, z);
                    check(LuminMortonTree.deinterleave(code, 0) == x, "x decode at " + x + "," + y + "," + z);
                    check(LuminMortonTree.deinterleave(code, 1) == y, "y decode");
                    check(LuminMortonTree.deinterleave(code, 2) == z, "z decode");
                    check(code >= 0 && code < (1 << LuminMortonTree.MORTON_BITS), "code within range");
                }
            }
        }
        // 交错码唯一性（不同坐标不碰撞）
        int a = LuminMortonTree.interleave(1, 2, 3);
        int b = LuminMortonTree.interleave(3, 2, 1);
        check(a != b, "distinct coordinates produce distinct Morton codes");
        // 相对坐标/范围判定
        check(LuminMortonTree.encodeRelative(100, 100) == 32, "relative encoding centres on origin");
        check(LuminMortonTree.encodeRelative(100 + 31, 100) == 63, "upper bound inclusive");
        check(LuminMortonTree.encodeRelative(100 + 32, 100) == -1, "out of range above");
        check(!LuminMortonTree.inRange(100 + 32, 100), "inRange false above");
    }

    private static void testMortonTree() {
        LuminMortonTree tree = new LuminMortonTree();
        check(tree.isEmpty(), "fresh tree empty");
        check(tree.add(1, 2, 3), "first add reports change");
        check(!tree.add(1, 2, 3), "duplicate add reports no change");
        check(tree.contains(1, 2, 3), "contains after add");
        check(!tree.contains(3, 2, 1), "no false positive");
        check(tree.cardinality() == 1, "cardinality counts unique slots");
        check(!tree.isEmpty(), "non-empty after add");
        // 子树摘要：整树根（边长 64）必非空
        check(tree.hasAnyInSubtree(0, 0, 0, 64), "root subtree non-empty");
        // 包含 (1,2,3) 的 2 边长子树非空；远处子树为空
        check(tree.hasAnyInSubtree(0, 2, 2, 2), "subtree containing (1,2,3) is non-empty");
        check(!tree.hasAnyInSubtree(62, 62, 62, 2), "far subtree empty");
        tree.clear();
        check(tree.isEmpty() && tree.cardinality() == 0, "clear resets state");
    }

    private static void testChildOrder() {
        int[] order = LuminTraversableTree.childOrder(0x0); // 相机位于全负象限 → 子 0 最近
        check(order[0] == 0, "nearest child first, got " + order[0]);
        check(order[7] == 7, "farthest child last, got " + order[7]);
        // 相机位于全正象限 → 子 7 最近
        int[] order2 = LuminTraversableTree.childOrder(0x7);
        check(order2[0] == 7, "positive octant makes child 7 nearest, got " + order2[0]);
        check(order2[7] == 0, "and child 0 farthest");
        // 顺序为 0..7 的排列
        java.util.Arrays.sort(order);
        check(java.util.Arrays.equals(order, new int[]{0, 1, 2, 3, 4, 5, 6, 7}), "order is a permutation");
        // 象限编码
        check(LuminTraversableTree.octantOf(10, 10, 10, 0, 0, 0) == 0x7, "all-positive octant");
        check(LuminTraversableTree.octantOf(-1, -1, -1, 0, 0, 0) == 0x0, "all-negative octant");
    }

    private static void testTreeReuse() {
        LuminMortonTree tree = new LuminMortonTree();
        tree.add(10, 10, 10);
        LuminTraversableTree traversal = new LuminTraversableTree(
                tree, LuminCullTier.REGULAR, 100, 100, 100, 16, 0);
        check(traversal.isValidFor(100, 100, 100, 8), "same camera and smaller distance reuses");
        check(traversal.isValidFor(100, 100, 100, 16), "equal distance reuses");
        check(!traversal.isValidFor(100, 100, 100, 17), "larger distance must rebuild");
        check(!traversal.isValidFor(101, 100, 100, 8), "REGULAR has zero width, so movement rebuilds");
        // WIDE 档允许 ±1 位移
        LuminTraversableTree wide = new LuminTraversableTree(
                tree, LuminCullTier.WIDE, 100, 100, 100, 16, 0);
        check(wide.isValidFor(101, 99, 100, 16), "WIDE tolerates 1-section movement");
        check(!wide.isValidFor(102, 100, 100, 16), "WIDE rejects 2-section movement");
    }

    private static void testTraversalCompleteness() {
        // 在 64³ 树里散布若干 section，遍历应恰好访问全部（distance 无限制、frustum 全通过）
        LuminMortonTree tree = new LuminMortonTree();
        List<int[]> expected = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int x = (i * 7) % 64;
            int y = (i * 13) % 64;
            int z = (i * 29) % 64;
            if (tree.add(x, y, z)) {
                expected.add(new int[]{x, y, z});
            }
        }
        LuminTraversableTree traversal = new LuminTraversableTree(
                tree, LuminCullTier.REGULAR, 32, 32, 32, 64, 0x7);
        List<int[]> visited = new ArrayList<>();
        traversal.traverse(
                (x, y, z, size) -> true,
                (x, y, z, size) -> true,
                (x, y, z, flags) -> {
                    visited.add(new int[]{x, y, z});
                    return true;
                });
        Comparator<int[]> cmp = (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
                : (a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[2], b[2]));
        expected.sort(cmp);
        visited.sort(cmp);
        check(visited.size() == expected.size(),
                "visited " + visited.size() + " of " + expected.size());
        for (int i = 0; i < expected.size(); i++) {
            check(cmp.compare(visited.get(i), expected.get(i)) == 0,
                    "mismatch at " + i + ": got " + java.util.Arrays.toString(visited.get(i))
                            + " expected " + java.util.Arrays.toString(expected.get(i)));
        }
    }

    private static void testInsideFlags() {
        LuminMortonTree tree = new LuminMortonTree();
        tree.add(32, 32, 32);
        // 视锥批测：只有小尺寸（≤2）视为完全在内 → 访问叶时应带 INSIDE_FRUSTUM
        LuminTraversableTree traversal = new LuminTraversableTree(
                tree, LuminCullTier.REGULAR, 32, 32, 32, 64, 0);
        boolean[] sawInsideFrustum = {false};
        boolean[] sawInsideDistance = {false};
        traversal.traverse(
                (x, y, z, size) -> size <= 2,
                (x, y, z, size) -> size <= 8,
                (x, y, z, flags) -> {
                    if ((flags & LuminTraversableTree.FLAG_INSIDE_FRUSTUM) != 0) {
                        sawInsideFrustum[0] = true;
                    }
                    if ((flags & LuminTraversableTree.FLAG_INSIDE_DISTANCE) != 0) {
                        sawInsideDistance[0] = true;
                    }
                    return true;
                });
        check(sawInsideFrustum[0], "leaf inherits INSIDE_FRUSTUM from ancestors");
        check(sawInsideDistance[0], "leaf inherits INSIDE_DISTANCE from ancestors");
        // WIDE 档不做视锥测试：叶应天然带 INSIDE_FRUSTUM
        LuminTraversableTree wide = new LuminTraversableTree(
                tree, LuminCullTier.WIDE, 32, 32, 32, 64, 0);
        boolean[] wideInside = {false};
        wide.traverse(null, (x, y, z, size) -> true,
                (x, y, z, flags) -> {
                    if ((flags & LuminTraversableTree.FLAG_INSIDE_FRUSTUM) != 0) {
                        wideInside[0] = true;
                    }
                    return true;
                });
        check(wideInside[0], "WIDE tier marks frustum inside implicitly");
    }

    private static void testDistancePruning() {
        LuminMortonTree tree = new LuminMortonTree();
        tree.add(0, 0, 0);   // 远角
        tree.add(32, 32, 32); // 原点附近
        LuminTraversableTree traversal = new LuminTraversableTree(
                tree, LuminCullTier.REGULAR, 32, 32, 32, 64, 0);
        List<int[]> visited = new ArrayList<>();
        // 距离批测：只有中心 8³ 块在距离内 → (0,0,0) 应被剪掉
        traversal.traverse(
                (x, y, z, size) -> true,
                (x, y, z, size) -> x >= 28 && x < 36 && y >= 28 && y < 36 && z >= 28 && z < 36,
                (x, y, z, flags) -> {
                    visited.add(new int[]{x, y, z});
                    return true;
                });
        check(visited.stream().anyMatch(p -> p[0] == 32 && p[1] == 32 && p[2] == 32),
                "in-range section visited");
        check(visited.stream().noneMatch(p -> p[0] == 0 && p[1] == 0 && p[2] == 0),
                "out-of-range section pruned by distance test");
    }

    private static void testRayTester() {
        // 常量以"方块"为单位：近距（<48 方块）直接放行
        check(!LuminVisibilityRayTester.isRayBlocked(0, 0, 0, 16, 0, 0, (x, y, z) -> false),
                "near sections (16 blocks) bypass the ray test");
        // 远距（80 方块 = 5 section）且路径上无门户 → 遮挡
        check(LuminVisibilityRayTester.isRayBlocked(0, 0, 0, 80, 0, 0, (x, y, z) -> false),
                "no portal on path -> blocked");
        // 远距且路径中段有门户 → 可见
        boolean visible = !LuminVisibilityRayTester.isRayBlocked(0, 0, 0, 80, 0, 0,
                (x, y, z) -> x == 40);
        check(visible, "portal on path -> not blocked");
        // 门户在端点（section 自身/相机所在）不算数
        check(LuminVisibilityRayTester.isRayBlocked(0, 0, 0, 80, 0, 0,
                        (x, y, z) -> (x == 0 && y == 0 && z == 0) || (x == 80 && y == 0 && z == 0)),
                "endpoints do not count as portals");
    }

    private LuminChunkM5aSelfTest() {
    }
}
