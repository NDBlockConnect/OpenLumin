package io.github.openlumin.shaderpack.compile;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.capability.LuminCapabilityReport;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.plan.LuminFramePlan;
import io.github.openlumin.shaderpack.plan.LuminResourcePlan;

import java.util.List;
import java.util.Map;

/**
 * WP-2 M3 编译产物：一个 shaderpack 的已编译表示（26.1.2 GL 平台层）。
 *
 * <p>包含按拓扑序排列的 {@link RenderPipeline} 与着色器资源部署表。
 * 管线对象为数据构建（不含 GPU 编译），可在无游戏环境下验证。</p>
 *
 * @param ir               原始中间表示（保留用于运行期诊断与属性查询）
 * @param graph            渲染图（保留用于执行层调度）
 * @param pipelines        已编译管线（按程序标识索引；仅含合成族 pass，几何 pass 延后至 WP-1 集成）
 * @param executionOrder   帧内执行序（从 PassGraph 拓扑序导出）
 * @param shaderResources  着色器资源部署表：MC 资源相对路径 → 源文本
 * @param resourcePlan     资源计划（目标分配 + 清屏批次；平台层据此惰性分配 GPU 资源）
 * @param framePlan        帧执行计划（清屏 + 逐 pass 的具体副本绑定与屏障点）
 * @param capabilityReport 能力旗标协商结果（required 缺失时 pack 被拒绝）
 * @param diagnostics      编译期诊断（INFO/WARNING 可继续；ERROR 阻止该 pack 使用）
 */
public record CompiledShaderpack(
        ShaderpackIR ir,
        PassGraph graph,
        Map<LuminProgramId, RenderPipeline> pipelines,
        List<LuminProgramId> executionOrder,
        Map<String, String> shaderResources,
        LuminResourcePlan resourcePlan,
        LuminFramePlan framePlan,
        LuminCapabilityReport capabilityReport,
        List<Diagnostic> diagnostics) {

    public CompiledShaderpack {
        pipelines = Map.copyOf(pipelines);
        executionOrder = List.copyOf(executionOrder);
        shaderResources = Map.copyOf(shaderResources);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).toList();
    }

    /** 按程序名查找管线（调试/诊断用）。 */
    public RenderPipeline pipelineFor(LuminProgramId program) {
        return pipelines.get(program);
    }
}
