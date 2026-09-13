package io.github.openlumin.chunk.cull;

/**
 * Morton（Z 序）位树：以位图紧凑表达某个立方体邻域内"哪些 section 存在/可见"，
 * 并提供降维摘要层以支持 O(1) 的"此子树是否有内容"查询。
 *
 * <p>坐标编码：每轴 {@value #BITS_PER_AXIS} 位（默认 6 → 64³ = 262144 个 section 槽位），
 * 交错后得 {@value #MORTON_BITS} 位 Morton 码；位图存于 {@code long[]}，每 long 64 槽。</p>
 *
 * <p>降维摘要（原理参照：reduced levels）：</p>
 * <ul>
 *   <li>第一级 {@code reducedLevel1}：每 64 位字压缩为 1 位（该字是否有任何位置位）；</li>
 *   <li>第二级 {@code reducedLevel2}：进一步压缩为单个 long 的位（用于整树是否为空）。</li>
 * </ul>
 * 增量 {@link #add} 时同步更新摘要；{@link #hasAnyInSubtree} 以位运算 O(1) 判定。</p>
 *
 * <p>非线程安全。</p>
 */
public final class LuminMortonTree {

    public static final int BITS_PER_AXIS = 6;
    public static final int MORTON_BITS = BITS_PER_AXIS * 3;
    /** 位图 long 数：2^18 / 64。 */
    public static final int WORD_COUNT = 1 << (MORTON_BITS - 6);
    private static final int AXIS_MASK = (1 << BITS_PER_AXIS) - 1;
    private static final int AXIS_ORIGIN = 1 << (BITS_PER_AXIS - 1);

    private final long[] words = new long[WORD_COUNT];
    /** 层级摘要：levels[d] = 第 d 级节点位（每节点汇总其 8 个子节点）。levels[0] 即叶子位图。 */
    private final long[][] levels;
    private int cardinality;

    public LuminMortonTree() {
        levels = new long[BITS_PER_AXIS + 1][];
        levels[0] = words;
        int nodes = WORD_COUNT * 64;
        for (int d = 1; d <= BITS_PER_AXIS; d++) {
            nodes = (nodes + 7) >>> 3;
            levels[d] = new long[(nodes + 63) >>> 6];
        }
    }

    /** 交错三个坐标分量成 Morton 码（位交错 6×3）。 */
    public static int interleave(int x, int y, int z) {
        int code = 0;
        for (int bit = 0; bit < BITS_PER_AXIS; bit++) {
            code |= ((x >>> bit) & 1) << (bit * 3);
            code |= ((y >>> bit) & 1) << (bit * 3 + 1);
            code |= ((z >>> bit) & 1) << (bit * 3 + 2);
        }
        return code;
    }

    /** 从 Morton 码解出单轴分量。 */
    public static int deinterleave(int code, int axis) {
        int value = 0;
        for (int bit = 0; bit < BITS_PER_AXIS; bit++) {
            value |= ((code >>> (bit * 3 + axis)) & 1) << bit;
        }
        return value;
    }

    /** 世界 section 坐标 → 树内偏移坐标（以 {@code origin} 为中心的 64³ 邻域；越界返回 -1）。 */
    public static int encodeRelative(int worldCoordinate, int originCoordinate) {
        int relative = worldCoordinate - originCoordinate + AXIS_ORIGIN;
        if (relative < 0 || relative > AXIS_MASK) {
            return -1;
        }
        return relative;
    }

    public static boolean inRange(int worldCoordinate, int originCoordinate) {
        return encodeRelative(worldCoordinate, originCoordinate) >= 0;
    }

    /**
     * 置位某 section 槽位。
     *
     * @return true = 该槽位此前未置位
     */
    public boolean add(int relativeX, int relativeY, int relativeZ) {
        return addByMortonCode(interleave(relativeX, relativeY, relativeZ));
    }

    public boolean addByMortonCode(int mortonCode) {
        int wordIndex = mortonCode >>> 6;
        long mask = 1L << (mortonCode & 63);
        if ((words[wordIndex] & mask) != 0) {
            return false;
        }
        words[wordIndex] |= mask;
        // 逐级向上更新摘要：第 d 级节点索引 = 码右移 3d 位
        for (int level = 1; level <= BITS_PER_AXIS; level++) {
            int nodeIndex = mortonCode >>> (level * 3);
            levels[level][nodeIndex >>> 6] |= 1L << (nodeIndex & 63);
        }
        cardinality++;
        return true;
    }

    public boolean contains(int relativeX, int relativeY, int relativeZ) {
        int mortonCode = interleave(relativeX, relativeY, relativeZ);
        return (words[mortonCode >>> 6] & (1L << (mortonCode & 63))) != 0;
    }

    /**
     * 以层级摘要判定"某个立方体子树是否含任何内容"。
     *
     * @param relativeX/Y/Z 子树基准（须按 {@code childSize} 对齐）
     * @param childSize     子树边长（2 的幂，1..64）
     */
    public boolean hasAnyInSubtree(int relativeX, int relativeY, int relativeZ, int childSize) {
        if (childSize <= 1) {
            return contains(relativeX, relativeY, relativeZ);
        }
        int level = Integer.numberOfTrailingZeros(childSize);
        if (level < 1 || level > BITS_PER_AXIS) {
            throw new IllegalArgumentException("childSize must be a power of two in [1,64], got " + childSize);
        }
        int nodeIndex = interleave(relativeX, relativeY, relativeZ) >>> (level * 3);
        long[] levelWords = levels[level];
        if ((nodeIndex >>> 6) >= levelWords.length) {
            return false;
        }
        return (levelWords[nodeIndex >>> 6] & (1L << (nodeIndex & 63))) != 0;
    }

    public boolean isEmpty() {
        return levels[BITS_PER_AXIS][0] == 0L;
    }

    public int cardinality() {
        return cardinality;
    }

    public void clear() {
        for (long[] level : levels) {
            java.util.Arrays.fill(level, 0L);
        }
        cardinality = 0;
    }

    /** 原始位图（只读用途）。 */
    public long[] words() {
        return words;
    }
}
