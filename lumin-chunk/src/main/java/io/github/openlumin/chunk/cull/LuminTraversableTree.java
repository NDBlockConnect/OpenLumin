package io.github.openlumin.chunk.cull;

/**
 * 可遍历剔除树（原理参照：Morton 位树 + 近到远子序 + inside 快速跳过 + 树复用）。
 *
 * <p>与"每帧全量 BFS"相比的两处关键改进：</p>
 * <ul>
 *   <li><b>树复用</b>：{@link #isValidFor} 判定"相机每轴位移 ≤ {@link LuminCullTier#bfsWidth()}
 *       且构建时搜索距离 ≥ 当前搜索距离"时可直接复用上一棵树，避免重建（相机静止/微动时
 *       收益最大）；</li>
 *   <li><b>近到远子序遍历 + inside 标志</b>：遍历时按相机象限决定 8 个子节点的访问次序，
 *       并以 INSIDE_FRUSTUM / INSIDE_DISTANCE 位标记"完全在内"的子树，使其跳过逐叶
 *       视锥/距离判定（近处先出，远处可尽早裁剪）。</li>
 * </ul>
 *
 * <p>非线程安全（构建与遍历分属不同阶段，见消费方的节拍约定）。</p>
 */
public final class LuminTraversableTree {

    /** 完全在视锥内（子树可跳过逐叶视锥测试）。 */
    public static final int FLAG_INSIDE_FRUSTUM = 1;
    /** 完全在搜索距离内（子树可跳过逐叶距离测试）。 */
    public static final int FLAG_INSIDE_DISTANCE = 1 << 1;

    /** 遍历访问者。 */
    @FunctionalInterface
    public interface Visitor {
        /**
         * @param relativeX/Y/Z 树内相对坐标
         * @param insideFlags   {@link #FLAG_INSIDE_FRUSTUM} / {@link #FLAG_INSIDE_DISTANCE} 的按位或
         * @return true = 继续遍历该子树；false = 剪枝
         */
        boolean visit(int relativeX, int relativeY, int relativeZ, int insideFlags);
    }

    /** 视锥批测：判断以 (relativeX..+size) 为界的立方体是否完全在视锥内。 */
    @FunctionalInterface
    public interface FrustumBatchTest {
        boolean isFullyInside(int relativeX, int relativeY, int relativeZ, int size);
    }

    /** 距离批测：判断以 (relativeX..+size) 为界的立方体是否完全在搜索距离内。 */
    @FunctionalInterface
    public interface DistanceBatchTest {
        boolean isFullyInsideDistance(int relativeX, int relativeY, int relativeZ, int size);
    }

    private final LuminMortonTree tree;
    private final LuminCullTier tier;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int buildDistanceSections;
    /** 相机象限（0..7，每轴 1 位：是否位于树中心的正方向）。 */
    private final int cameraOctant;

    public LuminTraversableTree(LuminMortonTree tree, LuminCullTier tier,
                                int originX, int originY, int originZ,
                                int buildDistanceSections, int cameraOctant) {
        if (tree == null) {
            throw new NullPointerException("tree");
        }
        if (tier == null) {
            throw new NullPointerException("tier");
        }
        if (buildDistanceSections < 1) {
            throw new IllegalArgumentException("buildDistanceSections must be >= 1");
        }
        this.tree = tree;
        this.tier = tier;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.buildDistanceSections = buildDistanceSections;
        this.cameraOctant = cameraOctant & 0x7;
    }

    public LuminCullTier tier() {
        return tier;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int buildDistanceSections() {
        return buildDistanceSections;
    }

    /** 该树是否包含任何 section。 */
    public boolean isEmpty() {
        return tree.isEmpty();
    }

    /**
     * 树复用判定（原理参照：相机微动与搜索距离收缩时直接复用）。
     *
     * @param cameraX/Y/Z         当前相机 section 坐标
     * @param searchDistanceSections 当前搜索距离
     * @return true = 可复用（无需重建）
     */
    public boolean isValidFor(int cameraX, int cameraY, int cameraZ, int searchDistanceSections) {
        if (searchDistanceSections > buildDistanceSections) {
            return false;
        }
        int width = tier.bfsWidth();
        return Math.abs(cameraX - originX) <= width
                && Math.abs(cameraY - originY) <= width
                && Math.abs(cameraZ - originZ) <= width;
    }

    /**
     * 近到远遍历（8 叉递归，按相机象限决定子序；完全在内子树跳过批测）。
     *
     * @param frustumTest  视锥批测（tier 不做视锥测试时传 null）
     * @param distanceTest 距离批测
     * @param visitor      叶访问者
     */
    public void traverse(FrustumBatchTest frustumTest, DistanceBatchTest distanceTest, Visitor visitor) {
        if (frustumTest == null && tier.isFrustumTested()) {
            throw new IllegalArgumentException("tier " + tier + " requires a frustum batch test");
        }
        if (distanceTest == null) {
            throw new NullPointerException("distanceTest");
        }
        if (visitor == null) {
            throw new NullPointerException("visitor");
        }
        int size = 1 << LuminMortonTree.BITS_PER_AXIS;
        int initialFlags = 0;
        if (!tier.isFrustumTested()) {
            initialFlags |= FLAG_INSIDE_FRUSTUM;
        }
        if (distanceTest.isFullyInsideDistance(0, 0, 0, size)) {
            initialFlags |= FLAG_INSIDE_DISTANCE;
        }
        traverseNode(0, 0, 0, size, initialFlags, frustumTest, distanceTest, visitor);
    }

    private void traverseNode(int x, int y, int z, int size, int flags,
                              FrustumBatchTest frustumTest, DistanceBatchTest distanceTest,
                              Visitor visitor) {
        if (tree.isEmpty()) {
            return;
        }
        if (size == 1) {
            if (!tree.contains(x, y, z)) {
                return;
            }
            // 叶：未在视锥内则需逐叶测试（除非 tier 不做视锥测试）
            if ((flags & FLAG_INSIDE_FRUSTUM) == 0 && frustumTest != null
                    && !frustumTest.isFullyInside(x, y, z, 1)) {
                return;
            }
            if ((flags & FLAG_INSIDE_DISTANCE) == 0 && !distanceTest.isFullyInsideDistance(x, y, z, 1)) {
                return;
            }
            visitor.visit(x, y, z, flags);
            return;
        }
        int childSize = size >>> 1;
        int[] order = childOrder(cameraOctant);
        for (int index : order) {
            int cx = x + ((index & 1) != 0 ? childSize : 0);
            int cy = y + ((index & 2) != 0 ? childSize : 0);
            int cz = z + ((index & 4) != 0 ? childSize : 0);
            if (!hasAnyContentInChild(cx, cy, cz, childSize)) {
                continue;
            }
            int childFlags = flags;
            if ((childFlags & FLAG_INSIDE_FRUSTUM) == 0 && frustumTest != null
                    && frustumTest.isFullyInside(cx, cy, cz, childSize)) {
                childFlags |= FLAG_INSIDE_FRUSTUM;
            }
            if ((childFlags & FLAG_INSIDE_DISTANCE) == 0
                    && distanceTest.isFullyInsideDistance(cx, cy, cz, childSize)) {
                childFlags |= FLAG_INSIDE_DISTANCE;
            }
            // 统一递归：叶级的 contains/视锥/距离剪枝在 traverseNode 的 size==1 分支中完成
            traverseNode(cx, cy, cz, childSize, childFlags, frustumTest, distanceTest, visitor);
        }
    }

    /** 子节点是否含任何内容（以层级摘要 O(1) 判定）。 */
    private boolean hasAnyContentInChild(int x, int y, int z, int size) {
        return tree.hasAnyInSubtree(x, y, z, size);
    }

    /** 近到远子序：按"与相机象限的汉明距离"升序（距离相同按索引稳定排序）。 */
    public static int[] childOrder(int octant) {
        Integer[] boxed = {0, 1, 2, 3, 4, 5, 6, 7};
        final int near = octant & 0x7;
        java.util.Arrays.sort(boxed, (a, b) -> {
            int da = Integer.bitCount(a ^ near);
            int db = Integer.bitCount(b ^ near);
            return da != db ? Integer.compare(da, db) : Integer.compare(a, b);
        });
        int[] order = new int[8];
        for (int i = 0; i < 8; i++) {
            order[i] = boxed[i];
        }
        return order;
    }

    /** 相机在树中心各轴正方向上的象限编码（0..7）。 */
    public static int octantOf(int cameraX, int cameraY, int cameraZ,
                               int originX, int originY, int originZ) {
        int octant = 0;
        if (cameraX >= originX) {
            octant |= 1;
        }
        if (cameraY >= originY) {
            octant |= 2;
        }
        if (cameraZ >= originZ) {
            octant |= 4;
        }
        return octant;
    }
}
