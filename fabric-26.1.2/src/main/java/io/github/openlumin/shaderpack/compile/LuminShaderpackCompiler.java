package io.github.openlumin.shaderpack.compile;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminFeatureFlag;
import io.github.openlumin.shaderpack.LuminPackDirectives;
import io.github.openlumin.shaderpack.LuminProgramGroup;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminProgramSource;
import io.github.openlumin.shaderpack.LuminShaderKind;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.capability.LuminCapabilityNegotiator;
import io.github.openlumin.shaderpack.capability.LuminCapabilityReport;
import io.github.openlumin.shaderpack.capability.LuminCapabilitySet;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackPreprocessor;
import io.github.openlumin.shaderpack.plan.LuminFramePlan;
import io.github.openlumin.shaderpack.plan.LuminFramePlanner;
import io.github.openlumin.shaderpack.plan.LuminResourcePlan;
import io.github.openlumin.shaderpack.plan.LuminResourcePlanner;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WP-2 M3 编译器（26.1.2 GL 平台层）：{@link ShaderpackIR} + {@link PassGraph}
 * → 逐程序 {@link RenderPipeline} 与着色器资源部署表。
 *
 * <p>M3 首片覆盖<b>合成族 pass</b>（SETUP/BEGIN/DEFERRED/COMPOSITE/FINAL/PREPARE/SHADOWCOMP）：
 * 全屏三角形（引擎控制，{@code gl_VertexID} 约定，与 26.1.2 内建
 * {@code minecraft:core/screenquad} 同构）、无深度测试、按指令或默认关闭混合。</p>
 * <p>几何 pass（GBUFFERS/SHADOW）的管线替换依赖 WP-1 区块引擎接线（设计 §7，M7 集成），
 * 编译层此期仅记录 INFO 诊断，不生成管线。</p>
 * <p>compute 程序（仅有 .csh 的 SETUP/SHADOWCOMP 等）在 26.1.2 GL 路径无计算管线抽象
 * （已 javap 实证：pipeline 包无 ComputePipeline），此期记录 INFO 诊断并延后。</p>
 * <p>管线对象为数据构建（{@code RenderPipeline.builder().build()} 不触发 GPU 编译），
 * 因此可在无游戏环境下全量自测。</p>
 */
public final class LuminShaderpackCompiler {

    private static final String NAMESPACE = "openlumin";
    private static final String SHADER_BASE = "shaderpack/";

    /**
     * 26.1.2 GL 路径的引擎能力集（WP-2 M6）。
     *
     * <p><b>保守原则</b>：只登记当前引擎确实具备的能力，未实现的（compute/SSBO/图像/
     * 逐缓冲混合/曲面细分/运动向量等）一律不登记——包声明 required 时会明确拒绝，
     * 声明 optional 时以 false 提供，绝不静默降级。</p>
     */
    public static final LuminCapabilitySet GL_PATH_CAPABILITIES = LuminCapabilitySet.of(
            // 26.1.2 渲染 API 围绕独立 GpuSampler 一等对象构建（管线以 withSampler 绑定）
            LuminFeatureFlag.SEPARATE_HARDWARE_SAMPLERS);

    /**
     * 内建全屏顶点着色器（合成 pass 缺 vsh 时的回退）。
     * <p>与 26.1.2 内建 {@code minecraft:core/screenquad} 同构：{@code gl_VertexID}
     * 全屏三角形，无顶点属性（EMPTY 格式 + TRIANGLES）。</p>
     */
    private static final String FULLSCREEN_SHADER_NAME = "_fullscreen";
    private static final Identifier FULLSCREEN_VERTEX_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, SHADER_BASE + FULLSCREEN_SHADER_NAME);
    private static final String FULLSCREEN_VERTEX_RESOURCE =
            "shaders/" + SHADER_BASE + FULLSCREEN_SHADER_NAME + ".vsh";
    private static final String FULLSCREEN_VERTEX_SOURCE = """
            #version 330

            // 引擎内建全屏三角形：顶点 0..2 覆盖屏幕（标准 gl_VertexID 映射）。
            out vec2 texCoord;

            void main() {
                vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                texCoord = uv;
            }
            """;

    private LuminShaderpackCompiler() {
    }

    /**
     * 编译入口。
     *
     * @param ir    已解析的中间表示（含 ERROR 时拒绝编译）
     * @param graph 由 IR 构建的渲染图
     * @return 编译产物（含诊断；ERROR 级诊断存在时 {@code pipelines} 为空）
     */
    public static CompiledShaderpack compile(ShaderpackIR ir, PassGraph graph) {
        if (ir == null) {
            throw new NullPointerException("ir");
        }
        if (graph == null) {
            throw new NullPointerException("graph");
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (ir.hasErrors()) {
            diagnostics.add(Diagnostic.error(
                    ir.metadata().source(), 0,
                    "pack contains parse errors; refusing to compile ("
                            + ir.errors().size() + " error(s))"));
            return new CompiledShaderpack(ir, graph, Map.of(), List.of(),
                    Map.of(), new LuminResourcePlan(Map.of(), List.of()),
                    new LuminFramePlan(List.of()),
                    LuminCapabilityReport.notNegotiated(), diagnostics);
        }

        // WP-2 M6：能力协商先行——required 缺失即明确拒绝（设计 §5，不静默降级）
        LuminCapabilityReport capabilityReport =
                LuminCapabilityNegotiator.negotiate(ir, GL_PATH_CAPABILITIES);
        if (!capabilityReport.accepted()) {
            diagnostics.add(Diagnostic.error(
                    ir.metadata().source(), 0,
                    "pack requires unsupported feature(s): "
                            + capabilityReport.missingRequired()
                            + "; refusing to compile (" + capabilityReport.describe() + ")"));
            return new CompiledShaderpack(ir, graph, Map.of(), List.of(),
                    Map.of(), new LuminResourcePlan(Map.of(), List.of()),
                    new LuminFramePlan(List.of()),
                    capabilityReport, diagnostics);
        }

        Map<LuminProgramId, RenderPipeline> pipelines = new LinkedHashMap<>();
        List<LuminProgramId> executionOrder = new ArrayList<>();
        Map<String, String> shaderResources = new LinkedHashMap<>();

        for (LuminPassNode node : graph.orderedNodes()) {
            LuminProgramId program = node.program();
            LuminProgramSource source = ir.programs().get(program);
            if (source == null || !source.enabled()) {
                continue;
            }
            if (isCompositeFamily(program.group())) {
                if (isComputeOnly(source)) {
                    diagnostics.add(Diagnostic.info(source.id().sourceBaseName(), 0,
                            "compute pass '" + program.sourceBaseName()
                                    + "' not compiled in M3 (no compute pipeline on 26.1.2 GL path)"));
                    continue;
                }
                if (!source.has(LuminShaderKind.FRAGMENT)) {
                    diagnostics.add(Diagnostic.warning(source.id().sourceBaseName(), 0,
                            "composite pass '" + program.sourceBaseName()
                                    + "' has no fragment stage; skipped"));
                    continue;
                }
                buildCompositePipeline(ir, program, source, node, pipelines, diagnostics);
                collectShaderResources(ir, source, shaderResources, diagnostics);
                if (!source.has(LuminShaderKind.VERTEX)) {
                    shaderResources.putIfAbsent(FULLSCREEN_VERTEX_RESOURCE, FULLSCREEN_VERTEX_SOURCE);
                }
                executionOrder.add(program);
            } else {
                diagnostics.add(Diagnostic.info(
                        source.id().sourceBaseName(), 0,
                        "geometry pass '" + program.sourceBaseName()
                                + "' not compiled in M3 (deferred to WP-1 integration)"));
            }
        }
        // WP-2 M4：资源计划 + 帧执行计划（清屏批次、乒乓绑定、屏障点）
        LuminResourcePlan resourcePlan = LuminResourcePlanner.plan(ir, graph);
        LuminFramePlan framePlan = LuminFramePlanner.plan(graph, resourcePlan);
        return new CompiledShaderpack(ir, graph, pipelines, executionOrder,
                shaderResources, resourcePlan, framePlan, capabilityReport, diagnostics);
    }

    /** 合成族：全屏四边形 pass（非几何替换）。 */
    static boolean isCompositeFamily(LuminProgramGroup group) {
        return switch (group) {
            case SETUP, BEGIN, SHADOWCOMP, PREPARE, DEFERRED, COMPOSITE, FINAL -> true;
            case SHADOW, GBUFFERS -> false;
        };
    }

    /** 仅含计算阶段（无 vsh/fsh）：26.1.2 GL 路径延后。 */
    static boolean isComputeOnly(LuminProgramSource source) {
        return source.has(LuminShaderKind.COMPUTE)
                && !source.has(LuminShaderKind.VERTEX)
                && !source.has(LuminShaderKind.FRAGMENT);
    }

    private static void buildCompositePipeline(ShaderpackIR ir,
                                               LuminProgramId program,
                                               LuminProgramSource source,
                                               LuminPassNode node,
                                               Map<LuminProgramId, RenderPipeline> out,
                                               List<Diagnostic> diagnostics) {
        boolean hasVertex = source.has(LuminShaderKind.VERTEX);
        if (hasVertex) {
            diagnostics.add(Diagnostic.info(source.id().sourceBaseName(), 0,
                    "custom composite vertex shader wired; OptiFine-style attribute/uniform"
                            + " translation is not applied yet (engine gl_VertexID convention)"));
        }
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(pipelineLocation(program))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withVertexShader(hasVertex ? shaderLocation(program) : FULLSCREEN_VERTEX_SHADER)
                .withFragmentShader(shaderLocation(program))
                .withCull(false)
                .withDepthStencilState(Optional.empty());

        applyBlend(ir, program, builder, diagnostics);
        for (LuminResourceId resource : node.reads()) {
            builder.withSampler(resource.target().canonicalName());
        }
        out.put(program, builder.build());
    }

    /**
     * 混合状态：{@code blend.<pass>[.<buffer>]} 指令优先；未声明时合成组默认关闭
     * （直接写入目标，与 OptiFine composite 的默认语义一致）。
     */
    private static void applyBlend(ShaderpackIR ir, LuminProgramId program,
                                   RenderPipeline.Builder builder,
                                   List<Diagnostic> diagnostics) {
        LuminPackDirectives.BlendOverride override = ir.directives().blendOverride(program);
        if (override != null && override.enabled()) {
            SourceFactor sourceFactor = mapSourceFactor(override.sourceFactor());
            DestFactor destFactor = mapDestFactor(override.destinationFactor());
            if (sourceFactor != null && destFactor != null) {
                BlendFunction blend = new BlendFunction(sourceFactor, destFactor);
                builder.withColorTargetState(new ColorTargetState(blend));
            } else {
                diagnostics.add(Diagnostic.warning(
                        program.sourceBaseName(), 0,
                        "unknown blend factor codes " + override.sourceFactor() + "/"
                                + override.destinationFactor()
                                + "; falling back to disabled blending"));
                builder.withColorTargetState(noBlend());
            }
        } else {
            builder.withColorTargetState(noBlend());
        }
    }

    private static ColorTargetState noBlend() {
        return new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL);
    }

    /**
     * BlendOverride 的引擎中立序号（解析层 parseBlendFactor 产出）→ 26.1.2 枚举。
     * 代码表：0=ZERO 1=ONE 2=SRC_COLOR 3=ONE_MINUS_SRC_COLOR 4=SRC_ALPHA
     * 5=ONE_MINUS_SRC_ALPHA 6=DST_ALPHA 7=ONE_MINUS_DST_ALPHA 8=DST_COLOR 9=ONE_MINUS_DST_COLOR。
     */
    static SourceFactor mapSourceFactor(int code) {
        return switch (code) {
            case 0 -> SourceFactor.ZERO;
            case 1 -> SourceFactor.ONE;
            case 2 -> SourceFactor.SRC_COLOR;
            case 3 -> SourceFactor.ONE_MINUS_SRC_COLOR;
            case 4 -> SourceFactor.SRC_ALPHA;
            case 5 -> SourceFactor.ONE_MINUS_SRC_ALPHA;
            case 6 -> SourceFactor.DST_ALPHA;
            case 7 -> SourceFactor.ONE_MINUS_DST_ALPHA;
            case 8 -> SourceFactor.DST_COLOR;
            case 9 -> SourceFactor.ONE_MINUS_DST_COLOR;
            default -> null;
        };
    }

    static DestFactor mapDestFactor(int code) {
        return switch (code) {
            case 0 -> DestFactor.ZERO;
            case 1 -> DestFactor.ONE;
            case 2 -> DestFactor.SRC_COLOR;
            case 3 -> DestFactor.ONE_MINUS_SRC_COLOR;
            case 4 -> DestFactor.SRC_ALPHA;
            case 5 -> DestFactor.ONE_MINUS_SRC_ALPHA;
            case 6 -> DestFactor.DST_ALPHA;
            case 7 -> DestFactor.ONE_MINUS_DST_ALPHA;
            case 8 -> DestFactor.DST_COLOR;
            case 9 -> DestFactor.ONE_MINUS_DST_COLOR;
            default -> null;
        };
    }

    /**
     * 部署着色器资源：源文本先经预处理（include 展开 + #version/#extension 顶部回填），
     * 部署键与 {@code Identifier("openlumin","shaderpack/<base>")} 的资源解析路径一致。
     */
    private static void collectShaderResources(ShaderpackIR ir, LuminProgramSource source,
                                               Map<String, String> out,
                                               List<Diagnostic> diagnostics) {
        String base = source.id().sourceBaseName();
        for (LuminShaderKind kind : EnumSet.of(LuminShaderKind.VERTEX, LuminShaderKind.FRAGMENT)) {
            String text = source.source(kind);
            if (text != null && !text.isEmpty()) {
                String path = source.path(kind);
                if (path != null && ir.includeGraph() != null) {
                    ShaderpackPreprocessor.Result preprocessed = ShaderpackPreprocessor.preprocess(
                            ir.includeGraph(), path, Map.of());
                    diagnostics.addAll(preprocessed.diagnostics());
                    text = preprocessed.source();
                }
                out.put("shaders/" + SHADER_BASE + base + "." + kind.extension(), text);
            }
        }
    }

    private static Identifier pipelineLocation(LuminProgramId program) {
        return Identifier.fromNamespaceAndPath(
                NAMESPACE, SHADER_BASE + program.sourceBaseName() + "/pipeline");
    }

    private static Identifier shaderLocation(LuminProgramId program) {
        return Identifier.fromNamespaceAndPath(
                NAMESPACE, SHADER_BASE + program.sourceBaseName());
    }
}
