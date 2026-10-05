package io.github.openlumin.shaderpack.plan;

import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.graph.LuminHazard;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;

import java.util.List;
import java.util.Map;

/**
 * 帧执行计划（WP-2 M4，纯 CPU）：清屏批次 + 逐 pass 的**具体副本绑定**。
 *
 * <p>乒乓解析：维护每个目标的"当前副本"（初值 MAIN，清屏已覆盖两副本）；
 * 读解析为当前副本；写解析为另一副本并翻转为新当前。**自反馈 pass（同读同写）
 * 因此天然合法**——读旧副本、写新副本，无需同副本竞争（消解 M2 的 self-feedback 告警）。</p>
 */
public record LuminFramePlan(List<Step> steps) {

    public LuminFramePlan {
        steps = List.copyOf(steps);
    }

    /** 帧步骤：清屏批次或 pass 执行。 */
    public sealed interface Step permits ClearStep, PassStep {
    }

    /** 清屏批次步骤（main/alt 各清由 {@code batch.bothCopies()} 指示）。 */
    public record ClearStep(LuminResourcePlan.ClearBatch batch) implements Step {
    }

    /**
     * pass 执行步骤：全部绑定已解析到具体副本。
     *
     * @param program       程序标识
     * @param sequence      图内序号
     * @param reads         读绑定：目标 → 副本
     * @param writes        写绑定：目标 → 副本
     * @param viewport      视口规格
     * @param compute       是否计算 pass
     * @param hazardsBefore 本 pass 前需处理的竞争（来自 PassGraph）
     */
    public record PassStep(LuminProgramId program,
                           int sequence,
                           Map<LuminTargetId, LuminResourceId.Copy> reads,
                           Map<LuminTargetId, LuminResourceId.Copy> writes,
                           LuminPassNode.ViewportSpec viewport,
                           boolean compute,
                           List<LuminHazard> hazardsBefore) implements Step {
        public PassStep {
            reads = Map.copyOf(reads);
            writes = Map.copyOf(writes);
            viewport = viewport == null ? LuminPassNode.ViewportSpec.full() : viewport;
            hazardsBefore = List.copyOf(hazardsBefore);
        }
    }

    /** 仅取 pass 步骤（跳过清屏）。 */
    public List<PassStep> passSteps() {
        return steps.stream()
                .filter(PassStep.class::isInstance)
                .map(PassStep.class::cast)
                .toList();
    }
}
