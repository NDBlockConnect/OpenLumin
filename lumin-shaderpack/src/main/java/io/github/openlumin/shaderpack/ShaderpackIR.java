package io.github.openlumin.shaderpack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shaderpack 中间表示（WP-2 第 3 层产物）：解析层对外的唯一输出。
 *
 * <p>纯 CPU、不可变；不含任何 GPU 资源。<b>第 4 层（PassGraph 规划）只依赖本结构</b>，
 * 因此规划逻辑可完全离线单测（见 WP-2 设计 §3.1）。</p>
 *
 * @param metadata        包元信息（名称/来源/维度覆盖）
 * @param programs        程序表（程序标识 → 源与目标）
 * @param directives      包级指令汇总
 * @param targets         渲染目标规格（未声明的目标不在表中）
 * @param customUniforms  自定义 uniform/变量声明
 * @param requiredFeatures 必需能力旗标（不满足则拒绝该 pack）
 * @param optionalFeatures 可选能力旗标（不满足则以 false 提供给包）
 * @param includeGraph     include 依赖图（编译期源码展开用；持有 SourceProvider 以便后续再读）
 * @param diagnostics     解析期诊断（含位置；含 ERROR 即视为不可用）
 */
public record ShaderpackIR(
        PackMetadata metadata,
        Map<LuminProgramId, LuminProgramSource> programs,
        LuminPackDirectives directives,
        Map<LuminTargetId, LuminTargetSpec> targets,
        List<LuminCustomUniform> customUniforms,
        Set<LuminFeatureFlag> requiredFeatures,
        Set<LuminFeatureFlag> optionalFeatures,
        io.github.openlumin.shaderpack.parse.IncludeGraph includeGraph,
        List<Diagnostic> diagnostics) {

    /**
     * 包元信息。
     *
     * @param name              包名（目录名）
     * @param source           来源描述（目录路径或压缩包路径）
     * @param dimensions       覆盖的维度文件夹（如 world0/world-1/world1）；空表示全局
     * @param hasShaderSources 是否发现任何程序源
     */
    public record PackMetadata(String name, String source, Set<String> dimensions, boolean hasShaderSources) {
    }

    public ShaderpackIR {
        programs = Map.copyOf(programs);
        targets = Map.copyOf(targets);
        customUniforms = List.copyOf(customUniforms);
        requiredFeatures = Set.copyOf(requiredFeatures);
        optionalFeatures = Set.copyOf(optionalFeatures);
        diagnostics = List.copyOf(diagnostics);
    }

    /** 是否存在 ERROR 级诊断（pack 不可用）。 */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).toList();
    }

    /** 按组筛选已解析的程序。 */
    public List<LuminProgramSource> programsInGroup(LuminProgramGroup group) {
        return programs.values().stream().filter(p -> p.id().group() == group).toList();
    }

    /** 是否声明了某个目标规格。 */
    public LuminTargetSpec targetSpec(LuminTargetId target) {
        return targets.getOrDefault(target, LuminTargetSpec.defaults());
    }
}
