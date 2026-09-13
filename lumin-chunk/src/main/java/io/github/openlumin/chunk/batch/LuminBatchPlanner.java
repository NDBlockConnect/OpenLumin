package io.github.openlumin.chunk.batch;

/**
 * 批量路径选择器（纯 CPU 决策逻辑，原理参照：按设备能力 + 批次特征选最优可行路径）。
 *
 * <p>决策序（由强到弱，首个可行者胜）：</p>
 * <ol>
 *   <li>批次仅有 1 个 draw → 一律 {@link LuminBatchPath#PER_DRAW}（无批量收益，且避免
 *       走批量 API 的额外参数构造）；</li>
 *   <li>{@code indirectCommandsReady} 且设备支持 {@code multiDrawIndirect} → 间接多 draw；</li>
 *   <li>共享 IBO/类型且统一实例数且无 per-draw uniform 时：
 *       设备支持 {@code multiDrawDirectInterleaved} → 交错；否则支持
 *       {@code multiDrawDirectSeparate} → 分离；</li>
 *   <li>支持聚合通路 → {@link LuminBatchPath#DRAW_MULTIPLE_INDEXED}（per-draw 绑定/uniform
 *       由 vanilla 循环完成，兼容混杂批次）；</li>
 *   <li>兜底 → {@link LuminBatchPath#PER_DRAW}。</li>
 * </ol>
 *
 * <p>不变量：无论选择哪条路径，提交语义必须与逐 draw 等价（IBO/类型/实例/顶点偏移一致）；
 * 选择器只决定"用哪条通路表达"，不改语义。</p>
 */
public final class LuminBatchPlanner {

    private LuminBatchPlanner() {
    }

    /**
     * 选择最优可行路径。
     *
     * @param capabilities 设备能力
     * @param traits       批次特征
     * @return 选定路径（永不为 null）
     */
    public static LuminBatchPath plan(LuminBatchCapabilities capabilities, LuminBatchTraits traits) {
        if (capabilities == null) {
            throw new NullPointerException("capabilities");
        }
        if (traits == null) {
            throw new NullPointerException("traits");
        }
        if (traits.drawCount() <= 1) {
            return LuminBatchPath.PER_DRAW;
        }
        if (traits.indirectCommandsReady() && capabilities.multiDrawIndirect()) {
            return LuminBatchPath.MULTI_DRAW_INDIRECT;
        }
        boolean directShapesFeasible = traits.sharedIndexBuffer()
                && traits.sharedIndexType()
                && traits.sharedVertexBuffer()
                && traits.uniformInstanceCount()
                && !traits.perDrawUniforms();
        if (directShapesFeasible) {
            if (capabilities.multiDrawDirectInterleaved()) {
                return LuminBatchPath.MULTI_DRAW_INTERLEAVED;
            }
            if (capabilities.multiDrawDirectSeparate()) {
                return LuminBatchPath.MULTI_DRAW_SEPARATE;
            }
        }
        if (capabilities.drawMultipleIndexed()) {
            return LuminBatchPath.DRAW_MULTIPLE_INDEXED;
        }
        return LuminBatchPath.PER_DRAW;
    }

    /**
     * 描述为何选定该路径（排障/日志用；不改变决策）。
     */
    public static String describe(LuminBatchPath path, LuminBatchCapabilities capabilities, LuminBatchTraits traits) {
        return switch (path) {
            case PER_DRAW -> traits.drawCount() <= 1
                    ? "single draw: batching has no benefit"
                    : "no batching capability available (or mismatched batch traits)";
            case MULTI_DRAW_INDIRECT -> "multiDrawIndirect available and indirect commands ready";
            case MULTI_DRAW_INTERLEAVED -> "shared IBO/type with uniform instance count; interleaved multi-draw supported";
            case MULTI_DRAW_SEPARATE -> "shared IBO/type with uniform instance count; separate-buffer multi-draw supported";
            case DRAW_MULTIPLE_INDEXED -> "falling back to aggregated drawMultipleIndexed path";
        };
    }
}
