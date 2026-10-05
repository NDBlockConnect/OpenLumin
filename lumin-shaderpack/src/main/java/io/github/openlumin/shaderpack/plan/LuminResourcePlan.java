package io.github.openlumin.shaderpack.plan;

import io.github.openlumin.shaderpack.LuminTargetId;

import java.util.List;
import java.util.Map;

/**
 * 资源计划（WP-2 M4）：全部使用的渲染目标分配 + 分组后的清屏批次。
 *
 * <p>清屏批处理语义（设计 §4 第 3 步）：按 (清屏色, 尺寸, 乒乓) 分组，
 * 每组一次批处理清屏；乒乓目标需 main/alt **各清一遍**（{@code bothCopies}）。</p>
 */
public record LuminResourcePlan(
        Map<LuminTargetId, LuminResourceAllocation> allocations,
        List<ClearBatch> clearBatches) {

    public LuminResourcePlan {
        allocations = Map.copyOf(allocations);
        clearBatches = List.copyOf(clearBatches);
    }

    /**
     * 一个清屏批次。
     *
     * @param targets    本批目标（保序；≤ {@code MAX_DRAW_BUFFERS}）
     * @param color      清屏色
     * @param size       尺寸策略（同批一致）
     * @param bothCopies 是否需要对 main/alt 各清一遍（批内含乒乓目标）
     */
    public record ClearBatch(List<LuminTargetId> targets,
                             LuminResourceAllocation.ClearColor color,
                             LuminResourceAllocation.SizeSpec size,
                             boolean bothCopies) {
        public ClearBatch {
            targets = List.copyOf(targets);
        }
    }

    /** 某目标的分配；未使用返回 null。 */
    public LuminResourceAllocation allocation(LuminTargetId target) {
        return allocations.get(target);
    }
}
