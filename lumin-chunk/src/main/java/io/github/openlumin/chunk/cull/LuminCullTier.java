package io.github.openlumin.chunk.cull;

/**
 * 剔除层级（原理参照：三级可见性 WIDE ⊃ REGULAR ⊃ LOCAL，反向不成立）。
 *
 * <p>三级的差别（由宽到窄）：</p>
 * <ul>
 *   <li>{@link #WIDE}：最保守——仅依赖几何连通图，<b>不做逐 section 视锥测试</b>，
 *       并允许 ±1 chunk 的边界容差（{@link #bfsWidth()} = 1），用于"树外"或大模型跨界；</li>
 *   <li>{@link #REGULAR}：标准档——连通图 + 逐 section 视锥测试（宽度 0）；</li>
 *   <li>{@link #LOCAL}：最强档——在 REGULAR 之上再加<b>射线可见性测试</b>，仅对近距生效。</li>
 * </ul>
 *
 * <p>蕴含链保证：视锥可见 ⇒ REGULAR 可见 ⇒ WIDE 可见；反之不成立。
 * 因此窄档命中即可停止更宽档的判定（测试与遍历据此短路）。</p>
 */
public enum LuminCullTier {
    WIDE(1, false, false),
    REGULAR(0, true, false),
    LOCAL(0, true, true);

    private final int bfsWidth;
    private final boolean frustumTested;
    private final boolean rayTested;

    LuminCullTier(int bfsWidth, boolean frustumTested, boolean rayTested) {
        this.bfsWidth = bfsWidth;
        this.frustumTested = frustumTested;
        this.rayTested = rayTested;
    }

    /** BFS 边界容差（axis 移动多少 chunk 内仍视为有效）。 */
    public int bfsWidth() {
        return bfsWidth;
    }

    /** 是否需要逐 section 视锥测试。 */
    public boolean isFrustumTested() {
        return frustumTested;
    }

    /** 是否需要射线可见性测试（仅 LOCAL）。 */
    public boolean isRayTested() {
        return rayTested;
    }

    /** 由窄到宽的探测顺序（用于"取最窄有效档"）。 */
    public static final LuminCullTier[] NARROW_TO_WIDE = {LOCAL, REGULAR, WIDE};
}
