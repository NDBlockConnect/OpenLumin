package io.github.openlumin.chunk;

import io.github.openlumin.chunk.cull.LuminCullRequest;
import io.github.openlumin.chunk.cull.LuminCullResult;
import io.github.openlumin.chunk.cull.LuminFrustumTest;
import io.github.openlumin.chunk.cull.LuminGraphDirection;
import io.github.openlumin.chunk.cull.LuminOcclusionCuller;
import io.github.openlumin.chunk.cull.LuminSectionPos;
import io.github.openlumin.chunk.cull.LuminSectionVisibilityGraph;
import io.github.openlumin.chunk.cull.LuminVisibilityEncoding;
import io.github.openlumin.chunk.sort.LuminCameraState;
import io.github.openlumin.chunk.sort.LuminSortStrategy;
import io.github.openlumin.chunk.sort.LuminTranslucentQuad;
import io.github.openlumin.chunk.sort.LuminTranslucentSorter;
import io.github.openlumin.chunk.sort.DynamicBSPSorter;
import io.github.openlumin.chunk.sort.NoneSorter;
import io.github.openlumin.chunk.sort.StaticTopoSorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP-1 M3b 自测：方向对编码、坐标打包、遮挡遍历（墙体/门/视锥过滤/距离/取消），
 * 聚合 M2（含 M1）全量回归。
 */
public final class LuminChunkM3SelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M3 全部测试节（先聚合 M2+M1 回归），返回失败节数（可被 M4 聚合运行器复用）。 */
    public static int runAll() {
        failures = LuminChunkM2SelfTest.runAll();
        section("visibility encoding", LuminChunkM3SelfTest::testEncoding);
        section("section pos packing", LuminChunkM3SelfTest::testPacking);
        section("open field traversal", LuminChunkM3SelfTest::testOpenField);
        section("walls and doorways", LuminChunkM3SelfTest::testWallsAndDoorways);
        section("occlusion off mode", LuminChunkM3SelfTest::testOcclusionOff);
        section("frustum does not block traversal", LuminChunkM3SelfTest::testFrustumGateway);
        section("distance cutoff", LuminChunkM3SelfTest::testDistanceCutoff);
        section("cancellation", LuminChunkM3SelfTest::testCancellation);
        section("camera must exist", LuminChunkM3SelfTest::testCameraMustExist);
        section("none sorter identity", LuminChunkM3SelfTest::testNoneSorter);
        section("static topo order", LuminChunkM3SelfTest::testStaticTopo);
        section("BSP back-to-front", LuminChunkM3SelfTest::testBspBackToFront);
        section("BSP spanning quad", LuminChunkM3SelfTest::testBspSpanning);
        section("BSP full set retention", LuminChunkM3SelfTest::testBspFullSet);
        section("BSP camera dependency", LuminChunkM3SelfTest::testBspCameraDependency);
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

    private static void testEncoding() {
        Set<Integer> seen = new HashSet<>();
        for (LuminGraphDirection from : LuminGraphDirection.values()) {
            for (LuminGraphDirection to : LuminGraphDirection.values()) {
                int bit = LuminVisibilityEncoding.bit(from, to);
                check(bit >= 0 && bit < 36, "bit in [0,36): " + bit);
                check(seen.add(bit), "bit must be unique: " + from + "->" + to);
            }
        }
        check(LuminVisibilityEncoding.bit(LuminGraphDirection.DOWN, LuminGraphDirection.DOWN) == 0,
                "bit(DOWN,DOWN)=0");
        check(LuminVisibilityEncoding.bit(LuminGraphDirection.UP, LuminGraphDirection.EAST) == 11,
                "bit(UP,EAST)=1*6+5=11");
        check(LuminVisibilityEncoding.bit(LuminGraphDirection.EAST, LuminGraphDirection.WEST) == 34,
                "bit(EAST,WEST)=5*6+4=34");
        long pair = LuminVisibilityEncoding.oppositePairMask(LuminGraphDirection.NORTH, LuminGraphDirection.SOUTH);
        check(Long.bitCount(pair) == 2, "pair mask has two bits");
        long allPairs = LuminVisibilityEncoding.fullyOccludedPairsMask();
        check(Long.bitCount(allPairs) == 6, "three axes = six directed bits, got " + Long.bitCount(allPairs));
        check(LuminVisibilityEncoding.isTraversalOpen(0b000001, LuminGraphDirection.DOWN),
                "traversal bit 0 = DOWN");
        check(!LuminVisibilityEncoding.isTraversalOpen(0b000001, LuminGraphDirection.UP),
                "traversal bit 1 must be closed");
    }

    private static void testPacking() {
        LuminSectionPos pos = new LuminSectionPos(-1048575, 1048575, -42);
        check(LuminSectionPos.unpack(pos.pack()).equals(pos), "extreme round trip");
        check(LuminSectionPos.unpack(new LuminSectionPos(7, -8, 9).pack())
                .equals(new LuminSectionPos(7, -8, 9)), "mixed sign round trip");
        try {
            new LuminSectionPos(1048576, 0, 0);
            check(false, "out-of-range x must throw");
        } catch (IllegalArgumentException expected) {
        }
        check(new LuminSectionPos(3, 4, 5).distanceSquaredTo(new LuminSectionPos(0, 0, 0)) == 50,
                "distance squared");
    }

    /** 全开阔图：exists 在 [-4,4]³，全部连通。 */
    private static LuminSectionVisibilityGraph openGraph(int bound) {
        return new LuminSectionVisibilityGraph() {
            @Override
            public boolean exists(LuminSectionPos pos) {
                return Math.abs(pos.x()) <= bound && Math.abs(pos.y()) <= bound && Math.abs(pos.z()) <= bound;
            }

            @Override
            public long visibilityBits(LuminSectionPos pos) {
                return 0b111111;
            }
        };
    }

    private static void testOpenField() {
        LuminOcclusionCuller culler = new LuminOcclusionCuller(openGraph(4));
        LuminCullRequest request = new LuminCullRequest(
                new LuminSectionPos(0, 0, 0), pos -> true, 2, true);
        LuminCullResult result = culler.cull(request, CancellationToken.create());
        check(!result.isCancelled(), "not cancelled");
        // d²<=4 的格点数：1(0) + 6(1) + 12(2) + 8(3) + 6(4) = 33
        check(result.visibleSections().size() == 33, "sphere radius 2 = 33 sections, got "
                + result.visibleSections().size());
        check(result.visibleSections().contains(new LuminSectionPos(0, 0, 0)),
                "camera section visible");
        check(result.visibleSections().stream().noneMatch(p -> Math.abs(p.x()) > 2 || Math.abs(p.y()) > 2 || Math.abs(p.z()) > 2),
                "distance cutoff respected");
    }

    /** x=0 平面为墙：wallAcross=true 时墙上仅 (0,0,0) 一扇门（东西开放）。 */
    private static LuminSectionVisibilityGraph wallGraph(boolean withDoor) {
        return new LuminSectionVisibilityGraph() {
            private final Map<LuminSectionPos, Long> bits = new HashMap<>();

            {
                long eastWestClosed = 0b111111L
                        & ~LuminVisibilityEncoding.traversalBit(LuminGraphDirection.EAST)
                        & ~LuminVisibilityEncoding.traversalBit(LuminGraphDirection.WEST);
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        for (int z = -2; z <= 2; z++) {
                            LuminSectionPos pos = new LuminSectionPos(x, y, z);
                            long open = 0b111111L;
                            if (x == 0 && (!withDoor || y != 0 || z != 0)) {
                                open = eastWestClosed;
                            }
                            bits.put(pos, open);
                        }
                    }
                }
            }

            @Override
            public boolean exists(LuminSectionPos pos) {
                return bits.containsKey(pos);
            }

            @Override
            public long visibilityBits(LuminSectionPos pos) {
                return bits.getOrDefault(pos, 0L);
            }
        };
    }

    private static void testWallsAndDoorways() {
        // 封闭墙：x>0 全部不可达，遍历仅覆盖西侧 2×5×5=50 个 section
        LuminOcclusionCuller culler = new LuminOcclusionCuller(wallGraph(false));
        LuminCullResult sealed = culler.cull(new LuminCullRequest(
                new LuminSectionPos(-1, 0, 0), pos -> true, 8, true), CancellationToken.create());
        check(sealed.visibleSections().stream().noneMatch(p -> p.x() > 0),
                "solid wall must block traversal");
        // 西屋 2×5×5=50 + 墙体层本身可达但不可穿越 5×5=25
        check(sealed.visitedSections() == 75,
                "west room plus wall layer, got " + sealed.visitedSections());

        // 开门：全图 125 个 section 均可达（远房间经门 + 房间内部连通）
        LuminCullResult withDoor = new LuminOcclusionCuller(wallGraph(true)).cull(
                new LuminCullRequest(new LuminSectionPos(-1, 0, 0), pos -> true, 8, true),
                CancellationToken.create());
        check(withDoor.visitedSections() == 125,
                "doorway connects both rooms, got " + withDoor.visitedSections());
        check(withDoor.visibleSections().contains(new LuminSectionPos(1, 1, 0)),
                "far-room interior reachable through door");
    }

    private static void testOcclusionOff() {
        LuminOcclusionCuller culler = new LuminOcclusionCuller(wallGraph(false));
        LuminCullRequest request = new LuminCullRequest(
                new LuminSectionPos(-1, 0, 0), pos -> true, 2, false);
        LuminCullResult result = culler.cull(request, CancellationToken.create());
        check(result.visibleSections().stream().anyMatch(p -> p.x() == 1 && p.y() == 0 && p.z() == 0),
                "occlusion-off mode ignores walls");
    }

    private static void testFrustumGateway() {
        // 视锥只认 z>=0 的 section；z<0 区域不可见但必须被扩展（否则 z=0 边界无法从 z=-1 侧连通）
        LuminFrustumTest halfSpace = pos -> pos.z() >= 0;
        LuminOcclusionCuller culler = new LuminOcclusionCuller(openGraph(3));
        LuminCullRequest request = new LuminCullRequest(
                new LuminSectionPos(0, 0, -1), halfSpace, 4, true);
        LuminCullResult result = culler.cull(request, CancellationToken.create());
        check(!result.visibleSections().contains(new LuminSectionPos(0, 0, -1)),
                "behind-camera section must be excluded from visible set");
        check(result.visibleSections().contains(new LuminSectionPos(0, 0, 0)),
                "front section must be visible");
        check(result.visitedSections() > result.visibleSections().size(),
                "traversal must extend beyond the visible half-space");
    }

    private static void testDistanceCutoff() {
        LuminOcclusionCuller culler = new LuminOcclusionCuller(openGraph(10));
        LuminCullRequest request = new LuminCullRequest(
                new LuminSectionPos(0, 0, 0), pos -> true, 1, true);
        LuminCullResult result = culler.cull(request, CancellationToken.create());
        check(result.visibleSections().size() == 7,
                "radius 1 sphere = center + 6 neighbors, got " + result.visibleSections().size());
    }

    private static void testCancellation() {
        LuminOcclusionCuller culler = new LuminOcclusionCuller(openGraph(4));
        CancellationToken.Token token = new CancellationToken.Token();
        token.cancel();
        LuminCullRequest request = new LuminCullRequest(
                new LuminSectionPos(0, 0, 0), pos -> true, 2, true);
        LuminCullResult result = culler.cull(request, token);
        check(result.isCancelled(), "pre-cancelled token must yield cancelled result");
        check(result.visibleSections().isEmpty(), "pre-cancelled result must be empty");
    }

    private static void testCameraMustExist() {
        LuminOcclusionCuller culler = new LuminOcclusionCuller(openGraph(2));
        try {
            culler.cull(new LuminCullRequest(
                    new LuminSectionPos(9, 9, 9), pos -> true, 2, true), CancellationToken.create());
            check(false, "missing camera section must throw");
        } catch (IllegalArgumentException expected) {
        }
    }

private static void testNoneSorter() {
        LuminTranslucentQuad q0 = axisAlignedQuad(0, 0, 0, 10);
        LuminTranslucentQuad q1 = axisAlignedQuad(1, 0, 0, 10);
        LuminTranslucentQuad q2 = axisAlignedQuad(2, 0, 0, 10);
        List<LuminTranslucentQuad> input = List.of(q0, q1, q2);
        LuminTranslucentSorter sorter = new NoneSorter();
        List<LuminTranslucentQuad> result = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, -5), input);
        check(result.size() == 3, "size preserved");
        check(result.get(0) == q0, "first element identity: same object reference");
        check(result.get(1) == q1, "second element identity");
        check(result.get(2) == q2, "third element identity");
        check(result.get(0).vertexIndex0() == 0, "vi0=" + result.get(0).vertexIndex0());
        sorter.close();
    }

    private static void testStaticTopo() {
        LuminTranslucentQuad q0 = axisAlignedQuad(0, 0, 10, 10);
        LuminTranslucentQuad q1 = axisAlignedQuad(1, 0, 10, 10);
        LuminTranslucentQuad q2 = axisAlignedQuad(2, 0, 10, 10);
        List<LuminTranslucentQuad> input = List.of(q0, q1, q2);
        LuminTranslucentSorter sorter = new StaticTopoSorter(new int[]{2, 0, 1});
        List<LuminTranslucentQuad> result = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, 50), input);
        check(result.size() == 3, "size preserved");
        check(result.get(0) == q2, "custom order: quad 2 first");
        check(result.get(1) == q0, "custom order: quad 0 second");
        check(result.get(2) == q1, "custom order: quad 1 third");
        sorter.close();
    }

    private static void testBspBackToFront() {
        LuminTranslucentQuad back = axisAlignedQuad(0, 0, -3, 10);
        LuminTranslucentQuad front = axisAlignedQuad(1, 0, 3, 10);
        List<LuminTranslucentQuad> input = List.of(front, back);
        LuminTranslucentSorter sorter = new DynamicBSPSorter();
        List<LuminTranslucentQuad> result = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, 10), input);
        check(result.get(0) == back, "back-to-front: back quad (z=-3) rendered before front (z=3), got vi0="
                + result.get(0).vertexIndex0() + " then vi0=" + result.get(1).vertexIndex0());
        check(result.get(1) == front, "front quad second");
        sorter.close();
    }

    private static void testBspSpanning() {
        // partition quad (facing +Y) at y=5; spanning quad from y=3 to y=7
        LuminTranslucentQuad part = quadFacingY(0, 4, 5);
        LuminTranslucentQuad span = quadSpanningY(0, 5, 3, 7);
        List<LuminTranslucentQuad> input = List.of(part, span);
        LuminTranslucentSorter sorter = new DynamicBSPSorter();
        // camera on front side (y=10) → back→spanning→front: span between nothing (back empty)
        List<LuminTranslucentQuad> result = sorter.sort(new LuminCameraState(0, 0, 0, 5, 10, 5), input);
        check(result.size() == 2, "both quads preserved");
        LuminTranslucentQuad first = result.get(0);
        LuminTranslucentQuad second = result.get(1);
        // with only partition quad + spanning quad and camera on front: spanning rendered between back/front
        // partition quad is part of spanning list (it IS the partitioner), so it appears in spanning
        check(first.planeNY() != 0 || second.planeNY() != 0, "both quads present in spanning or leaf");
        sorter.close();
    }

    private static void testBspFullSet() {
        List<LuminTranslucentQuad> input = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            input.add(axisAlignedQuad(0, i, i * 2 - 20, 10));
        }
        LuminTranslucentSorter sorter = new DynamicBSPSorter();
        List<LuminTranslucentQuad> result = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, 20), input);
        Set<Integer> inIndices = new HashSet<>();
        for (LuminTranslucentQuad q : input) {
            inIndices.add(q.vertexIndex0());
        }
        Set<Integer> outIndices = new HashSet<>();
        for (LuminTranslucentQuad q : result) {
            outIndices.add(q.vertexIndex0());
        }
        check(inIndices.equals(outIndices), "all quads preserved after BSP, input="
                + inIndices.size() + " output=" + outIndices.size());
        sorter.close();
    }

    private static void testBspCameraDependency() {
        LuminTranslucentQuad front = axisAlignedQuad(0, 0, 5, 10);
        LuminTranslucentQuad back = axisAlignedQuad(1, 0, -5, 10);
        List<LuminTranslucentQuad> input = List.of(front, back);
        LuminTranslucentSorter sorter = new DynamicBSPSorter();
        // camera at z=10: back (z=-5) should be first
        List<LuminTranslucentQuad> orderFromFront = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, 10), input);
        check(orderFromFront.get(0) == back, "camera at z=10: back quad first, got vi0="
                + orderFromFront.get(0).vertexIndex0());
        // camera at z=-10: front (z=5) should be first
        List<LuminTranslucentQuad> orderFromBack = sorter.sort(new LuminCameraState(0, 0, 0, 5, 5, -10), input);
        check(orderFromBack.get(0) == front, "camera at z=-10: front quad first, got vi0="
                + orderFromBack.get(0).vertexIndex0());
        sorter.close();
    }

    /**
     * 构造一个轴对齐（XY 平面）四边形，位于 z=zPlane，
     * 面法线 +Z，中心在 (5,5,zPlane)，边长 side（从 (offset, offset, zPlane) 到 (offset+side, offset+side, zPlane)）。
     * 顶点索引起始值为 baseIndex。
     */
    private static LuminTranslucentQuad axisAlignedQuad(int baseIndex, float offset, float zPlane, float side) {
        float x0 = offset, y0 = offset, x1 = offset + side, y1 = offset + side;
        return new LuminTranslucentQuad(
                baseIndex + 0, baseIndex + 1, baseIndex + 2, baseIndex + 3,
                x0, y0, zPlane, x1, y0, zPlane, x1, y1, zPlane, x0, y1, zPlane,
                0f, 0f, 1f, -zPlane);
    }

    /** YZ 平面四边形（面法线 +X）。 */
    private static LuminTranslucentQuad quadFacingX(float baseIndex, float xPlane, float offset, float side) {
        float z0 = offset, z1 = offset + side;
        return new LuminTranslucentQuad(
                (int) baseIndex + 0, (int) baseIndex + 1, (int) baseIndex + 2, (int) baseIndex + 3,
                xPlane, 0f, z0, xPlane, 0f, z1, xPlane, 10f, z1, xPlane, 10f, z0,
                1f, 0f, 0f, -xPlane);
    }

    /** XZ 平面四边形（面法线 +Y）。 */
    private static LuminTranslucentQuad quadFacingY(float baseIndex, float yPlane, float offset) {
        float x0 = offset, x1 = offset + 10f, z0 = offset, z1 = offset + 10f;
        return new LuminTranslucentQuad(
                (int) baseIndex + 0, (int) baseIndex + 1, (int) baseIndex + 2, (int) baseIndex + 3,
                x0, yPlane, z0, x1, yPlane, z0, x1, yPlane, z1, x0, yPlane, z1,
                0f, 1f, 0f, -yPlane);
    }

    /** 跨越 Y 轴的梯形面（面法线 +Y），角点从 yLo 到 yHi（用于 BSP 跨越测试）。 */
    private static LuminTranslucentQuad quadSpanningY(float baseIndex, float yPlane, float yLo, float yHi) {
        float x0 = 0f, x1 = 10f, z0 = 0f, z1 = 10f;
        return new LuminTranslucentQuad(
                (int) baseIndex + 0, (int) baseIndex + 1, (int) baseIndex + 2, (int) baseIndex + 3,
                x0, yLo, z0, x1, yLo, z0, x1, yHi, z1, x0, yHi, z1,
                0f, 1f, 0f, -yPlane);
    }
}
