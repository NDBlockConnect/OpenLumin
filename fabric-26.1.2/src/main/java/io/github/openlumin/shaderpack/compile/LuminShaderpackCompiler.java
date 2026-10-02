package io.github.openlumin.shaderpack.compile;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminPackDirectives;
import io.github.openlumin.shaderpack.LuminProgramGroup;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminProgramSource;
import io.github.openlumin.shaderpack.LuminShaderKind;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.graph.LuminPassNode;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.graph.PassGraph;
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
 * 全屏四边形、POSITION_TEX 顶点格式、无深度测试、按指令或默认关闭混合。</p>
 * <p>几何 pass（GBUFFERS/SHADOW）的管线替换依赖 WP-1 区块引擎接线（设计 §7，M7 集成），
 * 编译层此期仅记录 INFO 诊断，不生成管线。</p>
 * <p>管线对象为数据构建（{@code RenderPipeline.builder().build()} 不触发 GPU 编译），
 * 因此可在无游戏环境下全量自测。</p>
 */
public final class LuminShaderpackCompiler {

    private static final String NAMESPACE = "openlumin";

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
                    Map.of(), diagnostics);
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
                diagnostics.addAll(buildCompositePipeline(ir, program, source, node, pipelines));
                collectShaderResources(source, shaderResources);
                executionOrder.add(program);
            } else {
                diagnostics.add(Diagnostic.info(
                        source.id().sourceBaseName(), 0,
                        "geometry pass '" + program.sourceBaseName()
                                + "' not compiled in M3 (deferred to WP-1 integration)"));
            }
        }
        return new CompiledShaderpack(ir, graph, pipelines, executionOrder,
                shaderResources, diagnostics);
    }

    /** 合成族：全屏四边形 pass（非几何替换）。 */
    static boolean isCompositeFamily(LuminProgramGroup group) {
        return switch (group) {
            case SETUP, BEGIN, SHADOWCOMP, PREPARE, DEFERRED, COMPOSITE, FINAL -> true;
            case SHADOW, GBUFFERS -> false;
        };
    }

    private static List<Diagnostic> buildCompositePipeline(ShaderpackIR ir,
                                                           LuminProgramId program,
                                                           LuminProgramSource source,
                                                           LuminPassNode node,
                                                           Map<LuminProgramId, RenderPipeline> out) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(pipelineLocation(program))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withVertexShader(shaderLocation(program))
                .withFragmentShader(shaderLocation(program))
                .withCull(false)
                .withDepthStencilState(Optional.empty());

        applyBlend(ir, program, builder, diagnostics);
        for (LuminResourceId resource : node.reads()) {
            builder.withSampler(resource.target().canonicalName());
        }
        out.put(program, builder.build());
        return diagnostics;
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

    private static void collectShaderResources(LuminProgramSource source,
                                               Map<String, String> out) {
        String base = source.id().sourceBaseName();
        for (LuminShaderKind kind : EnumSet.of(LuminShaderKind.VERTEX, LuminShaderKind.FRAGMENT)) {
            String text = source.source(kind);
            if (text != null && !text.isEmpty()) {
                out.put("shaders/" + base + "." + kind.extension(), text);
            }
        }
    }

    private static Identifier pipelineLocation(LuminProgramId program) {
        return Identifier.fromNamespaceAndPath(
                NAMESPACE, "shaderpack/" + program.sourceBaseName() + "/pipeline");
    }

    private static Identifier shaderLocation(LuminProgramId program) {
        return Identifier.fromNamespaceAndPath(
                NAMESPACE, "shaderpack/" + program.sourceBaseName());
    }
}
