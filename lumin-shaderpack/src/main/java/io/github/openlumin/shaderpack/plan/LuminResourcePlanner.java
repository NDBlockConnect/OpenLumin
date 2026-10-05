package io.github.openlumin.shaderpack.plan;

import io.github.openlumin.shaderpack.LuminBufferFormat;
import io.github.openlumin.shaderpack.LuminPackDirectives;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.LuminTargetSpec;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源规划器（WP-2 M4，纯 CPU）：{@link ShaderpackIR} + {@link PassGraph}
 * → {@link LuminResourcePlan}。
 *
 * <p>规则：</p>
 * <ul>
 *   <li><b>使用集</b> = PassGraph 全部读写目标 ∪ IR 声明目标（保序去重）；</li>
 *   <li><b>格式默认</b>：colortex → RGBA8；depthtex/shadowtex → R32F（设计 §8 统一约定）；
 *       shadowcolor → RGBA8；显式 {@code *Format} 指令优先；</li>
 *   <li><b>尺寸</b>：显式 {@code size.buffer.*}（相对/绝对按小数点判定）优先，
 *       其次 IR 目标规格的 {@code SizeScale}，否则全尺寸；</li>
 *   <li><b>清屏色默认</b>（设计 §5）：colortex0=雾色 / colortex1=白 / 其余 colortex=透明黑 /
 *       depthtex·shadowtex=深度远平面 / shadowcolor=透明黑；字面量指令优先；</li>
 *   <li><b>乒乓</b>：colortex 为 main/alt 对（真），其余单实例；</li>
 *   <li><b>清屏批</b>：按 (清屏色, 尺寸, 乒乓) 分组、单批 ≤ 32 目标（保守 GL 下限）、
 *       组内顺序稳定。</li>
 * </ul>
 */
public final class LuminResourcePlanner {

    /** 单批目标数上限（保守 GL 下限；执行层可按真实能力放宽）。 */
    public static final int MAX_DRAW_BUFFERS = 32;

    private LuminResourcePlanner() {
    }

    public static LuminResourcePlan plan(ShaderpackIR ir, PassGraph graph) {
        if (ir == null) {
            throw new NullPointerException("ir");
        }
        if (graph == null) {
            throw new NullPointerException("graph");
        }

        // 使用集：图读写 ∪ IR 声明；按 (kind, index) 稳定排序
        Set<LuminTargetId> used = new LinkedHashSet<>();
        for (LuminPassNode node : graph.nodes()) {
            for (LuminResourceId resource : node.reads()) {
                used.add(resource.target());
            }
            for (LuminResourceId resource : node.writes()) {
                used.add(resource.target());
            }
        }
        used.addAll(ir.targets().keySet());
        List<LuminTargetId> ordered = new ArrayList<>(used);
        ordered.sort(Comparator.comparingInt((LuminTargetId t) -> t.kind().ordinal())
                .thenComparingInt(LuminTargetId::index));

        Map<LuminTargetId, LuminResourceAllocation> allocations = new LinkedHashMap<>();
        for (LuminTargetId target : ordered) {
            allocations.put(target, allocate(ir, target));
        }

        List<LuminResourcePlan.ClearBatch> batches = buildClearBatches(ordered, allocations);
        return new LuminResourcePlan(allocations, batches);
    }

    private static LuminResourceAllocation allocate(ShaderpackIR ir, LuminTargetId target) {
        LuminTargetSpec spec = ir.targetSpec(target);
        LuminBufferFormat format = spec.format() != null
                ? spec.format()
                : defaultFormat(target);
        LuminResourceAllocation.SizeSpec size =
                sizeOf(ir, target, spec);
        LuminResourceAllocation.ClearColor clearColor = spec.clearColor() != null
                ? LuminResourceAllocation.ClearColor.literal(spec.clearColor())
                : defaultClearColor(target);
        boolean pingPong = target.kind() == LuminTargetId.Kind.COLOR;
        return new LuminResourceAllocation(target, format, size,
                spec.clear(), clearColor, spec.mipmapEnabled(), pingPong);
    }

    private static LuminBufferFormat defaultFormat(LuminTargetId target) {
        return switch (target.kind()) {
            case COLOR, SHADOW_COLOR -> LuminBufferFormat.RGBA8;
            case DEPTH, SHADOW_DEPTH -> LuminBufferFormat.R32F;
        };
    }

    private static LuminResourceAllocation.SizeSpec sizeOf(ShaderpackIR ir, LuminTargetId target,
                                                           LuminTargetSpec spec) {
        LuminPackDirectives.BufferSize directive =
                ir.directives().bufferSize(target.canonicalName());
        if (directive != null) {
            return new LuminResourceAllocation.SizeSpec(
                    directive.width(), directive.height(),
                    directive.widthRelative(), directive.heightRelative());
        }
        if (spec.sizeScale() != null) {
            LuminTargetSpec.SizeScale scale = spec.sizeScale();
            // IR 的 SizeScale 只有单一 absolute 标志 → 两轴一致
            return new LuminResourceAllocation.SizeSpec(
                    scale.width(), scale.height(),
                    !scale.absolute(), !scale.absolute());
        }
        return LuminResourceAllocation.SizeSpec.full();
    }

    private static LuminResourceAllocation.ClearColor defaultClearColor(LuminTargetId target) {
        return switch (target.kind()) {
            case COLOR -> switch (target.index()) {
                case 0 -> LuminResourceAllocation.ClearColor.fog();
                case 1 -> LuminResourceAllocation.ClearColor.white();
                default -> LuminResourceAllocation.ClearColor.transparentBlack();
            };
            case DEPTH, SHADOW_DEPTH -> LuminResourceAllocation.ClearColor.depthFar();
            case SHADOW_COLOR -> LuminResourceAllocation.ClearColor.transparentBlack();
        };
    }

    private static List<LuminResourcePlan.ClearBatch> buildClearBatches(
            List<LuminTargetId> ordered,
            Map<LuminTargetId, LuminResourceAllocation> allocations) {
        record GroupKey(LuminResourceAllocation.ClearColor color,
                        LuminResourceAllocation.SizeSpec size,
                        boolean bothCopies) {
        }
        Map<GroupKey, List<LuminTargetId>> groups = new LinkedHashMap<>();
        for (LuminTargetId target : ordered) {
            LuminResourceAllocation allocation = allocations.get(target);
            if (!allocation.clear()) {
                continue;
            }
            GroupKey key = new GroupKey(allocation.clearColor(), allocation.size(),
                    allocation.pingPong());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
        }

        List<LuminResourcePlan.ClearBatch> batches = new ArrayList<>();
        for (Map.Entry<GroupKey, List<LuminTargetId>> entry : groups.entrySet()) {
            List<LuminTargetId> targets = entry.getValue();
            for (int start = 0; start < targets.size(); start += MAX_DRAW_BUFFERS) {
                int end = Math.min(start + MAX_DRAW_BUFFERS, targets.size());
                GroupKey key = entry.getKey();
                batches.add(new LuminResourcePlan.ClearBatch(
                        targets.subList(start, end), key.color(), key.size(), key.bothCopies()));
            }
        }
        return batches;
    }
}
