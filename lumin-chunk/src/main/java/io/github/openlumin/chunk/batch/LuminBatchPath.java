package io.github.openlumin.chunk.batch;

/**
 * 批量提交路径（按"能力描述力"从强到弱排列；{@link #ordinal()} 越小越优先）。
 *
 * <ul>
 *   <li>{@link #MULTI_DRAW_INDIRECT}：GPU 侧命令缓冲驱动（最强，最省 CPU）；
 *       需 {@code multiDrawIndirect}；</li>
 *   <li>{@link #MULTI_DRAW_INTERLEAVED}：交错直传多 draw（单缓冲数组）；
 *       需 {@code multiDrawDirectInterleaved}（GL 不支持）；</li>
 *   <li>{@link #MULTI_DRAW_SEPARATE}：分离缓冲多 draw（偏移/计数/baseVertex 三数组）；
 *       需 {@code multiDrawDirectSeparate}（GL 亦支持）；</li>
 *   <li>{@link #DRAW_MULTIPLE_INDEXED}：聚合通路（per-draw 绑定由 vanilla 循环执行）；</li>
 *   <li>{@link #PER_DRAW}：逐 draw 循环（最强兼容，最弱性能）。</li>
 * </ul>
 */
public enum LuminBatchPath {
    MULTI_DRAW_INDIRECT,
    MULTI_DRAW_INTERLEAVED,
    MULTI_DRAW_SEPARATE,
    DRAW_MULTIPLE_INDEXED,
    PER_DRAW;

    /** 该路径是否属"单次提交多个 draw"（相对逐 draw 有收益）。 */
    public boolean isBatched() {
        return this != PER_DRAW;
    }
}
