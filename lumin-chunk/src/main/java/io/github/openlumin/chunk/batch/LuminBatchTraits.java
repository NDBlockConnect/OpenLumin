package io.github.openlumin.chunk.batch;

/**
 * 批次特征（决定哪些批量路径可行）。
 *
 * @param drawCount            批次内 draw 数（1 时逐 draw 与批量等价）
 * @param sharedIndexBuffer    全部 draw 是否共用同一索引缓冲（分离/交错/间接形态的前提）
 * @param sharedIndexType      全部 draw 的索引类型是否一致（同上）
 * @param sharedVertexBuffer   全部 draw 是否共用同一顶点缓冲（分离形态的额外前提：
 *                             各 draw 以 baseVertex 区分顶点区段）
 * @param uniformInstanceCount 是否所有 draw 的实例数一致（交错/分离形态要求统一实例数）
 * @param perDrawUniforms      是否存在 per-draw uniform 差异（仅聚合通路与间接形态支持）
 * @param indirectCommandsReady 间接命令缓冲是否已就绪（indirect 路径前提）
 */
public record LuminBatchTraits(
        int drawCount,
        boolean sharedIndexBuffer,
        boolean sharedIndexType,
        boolean sharedVertexBuffer,
        boolean uniformInstanceCount,
        boolean perDrawUniforms,
        boolean indirectCommandsReady) {

    public LuminBatchTraits {
        if (drawCount < 1) {
            throw new IllegalArgumentException("drawCount must be >= 1, got " + drawCount);
        }
    }

    /** 单个 draw：无批量收益。 */
    public static LuminBatchTraits single() {
        return new LuminBatchTraits(1, true, true, true, true, false, false);
    }

    /** 同 region 的常规批次：共享 VBO/IBO/类型、统一实例数、无 per-draw uniform。 */
    public static LuminBatchTraits uniformRegionBatch(int drawCount) {
        return new LuminBatchTraits(drawCount, true, true, true, true, false, false);
    }

    /** 混杂批次：IBO 不共享且存在 per-draw uniform。 */
    public static LuminBatchTraits mixed(int drawCount) {
        return new LuminBatchTraits(drawCount, false, false, false, false, true, false);
    }
}
