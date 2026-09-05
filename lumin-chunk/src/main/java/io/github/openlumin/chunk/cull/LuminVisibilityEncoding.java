package io.github.openlumin.chunk.cull;

/**
 * 方向对可见性位编码：6×6 有向 (from, to) 对 → 0..35 位（long 可容纳 36 位）。
 * <p>用于消费方/调试器在单个 long 内表达"沿 from 面看去 to 方向连通"这类
 * 有向对命题；section 级遍历位掩码（6 位）见 {@link LuminSectionVisibilityGraph}。</p>
 */
public final class LuminVisibilityEncoding {

    private static final int COUNT = LuminGraphDirection.values().length;

    /**
     * @return (from, to) 有向对的位索引（0..35）
     */
    public static int bit(LuminGraphDirection from, LuminGraphDirection to) {
        return from.ordinal() * COUNT + to.ordinal();
    }

    /**
     * @return 一条轴线两个方向对（a→b 与 b→a）的联合掩码
     */
    public static long oppositePairMask(LuminGraphDirection a, LuminGraphDirection b) {
        return (1L << bit(a, b)) | (1L << bit(b, a));
    }

    /**
     * @return 三条轴线（UP/DOWN、N/S、W/E）的全遮挡对联合掩码（6 个有向位）
     */
    public static long fullyOccludedPairsMask() {
        long mask = 0L;
        for (LuminGraphDirection dir : LuminGraphDirection.values()) {
            mask |= oppositePairMask(dir, dir.opposite());
        }
        return mask;
    }

    /**
     * 遍历位掩码（6 位）：bit d 置位 = 沿方向 d 的邻接 section 连通可通行。
     */
    public static long traversalBit(LuminGraphDirection direction) {
        return 1L << direction.ordinal();
    }

    public static boolean isTraversalOpen(long traversalBits, LuminGraphDirection direction) {
        return (traversalBits & traversalBit(direction)) != 0;
    }

    private LuminVisibilityEncoding() {
    }
}
