package io.github.openlumin.shaderpack.exec;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.frame.LuminFrameUniforms;
import io.github.openlumin.shaderpack.frame.LuminShaderpackUniformBlock;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.plan.LuminFramePlan;
import io.github.openlumin.shaderpack.plan.LuminResourceAllocation;
import io.github.openlumin.shaderpack.plan.LuminResourcePlan;
import io.github.openlumin.shaderpack.compile.CompiledShaderpack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Shaderpack 帧执行器（WP-2 M4 平台层，26.1.2 GL 路径）。
 *
 * <p>每帧流程（对齐 vanilla PostPass 的实证模式 + 本引擎规划层）：</p>
 * <ol>
 *   <li>尺寸变化 → 重建全部目标（{@link ShaderpackTargets}）；</li>
 *   <li>写入 {@code ShaderpackUniforms} UBO（{@code writeToBuffer}，需 USAGE_COPY_DST）；</li>
 *   <li>清屏：{@code clearColorTexture}/{@code clearDepthTexture}（ARGB 实证自
 *       GlCommandEncoder 的 ARGB.redFloat 拆包）；乒乓目标 main/alt 各清一遍；</li>
 *   <li>逐 pass：跳过无管线者（几何/compute 延后）；MRT 仅绑首目标（确定性取最小
 *       (kind, index)，26.1.2 通道仅单颜色附件）；</li>
 *   <li>pass 内：setPipeline → bindDefaultUniforms（vanilla 同款）→ 按需绑
 *       ShaderpackUniforms → 读集绑采样器 → {@code draw(3, 1)}（gl_VertexID 全屏三角形）。</li>
 * </ol>
 *
 * <p><b>本期明确限制</b>（不静默）：MRT 多目标写入降级为首目标；深度附件不作为 pass 附件
 * （depthtex 仅作可采样纹理）；屏障由 GL 同命令流隐式保证（Vulkan 侧后续显式化）。</p>
 */
public final class ShaderpackFrameExecutor implements AutoCloseable {

    private final CompiledShaderpack compiled;
    private ShaderpackTargets targets;
    private GpuBuffer uniformBuffer;
    private int width;
    private int height;
    private boolean closed;

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Set<LuminProgramId> skippedPrograms = new LinkedHashSet<>();
    private final Set<String> warnedOnce = new LinkedHashSet<>();

    public ShaderpackFrameExecutor(CompiledShaderpack compiled) {
        if (compiled == null) {
            throw new NullPointerException("compiled");
        }
        this.compiled = compiled;
    }

    public CompiledShaderpack compiled() {
        return compiled;
    }

    /**
     * 执行一帧（须在 MC 帧流程内调用——bindDefaultUniforms 依赖帧态动态 uniform）。
     *
     * @param sampler         默认采样器（26.1.2 独立 GpuSampler）
     * @param viewportWidth   主目标宽
     * @param viewportHeight  主目标高
     * @param fogColorArgb    雾色（ARGB；colortex0 的 FOG 清屏色解析源）
     * @param uniforms        本帧 uniform 快照
     */
    public void execute(GpuSampler sampler, int viewportWidth, int viewportHeight,
                        int fogColorArgb, LuminFrameUniforms uniforms) {
        if (closed) {
            throw new IllegalStateException("executor closed");
        }
        if (compiled.hasErrors()) {
            return; // 能力/解析拒绝的 pack 不执行
        }
        GpuDevice device = RenderSystem.getDevice();
        ensureTargets(device, viewportWidth, viewportHeight);
        ensureUniformBuffer(device);

        CommandEncoder encoder = device.createCommandEncoder();
        writeUniforms(encoder, uniforms);
        clearTargets(encoder, fogColorArgb);
        for (LuminFramePlan.Step step : compiled.framePlan().steps()) {
            if (step instanceof LuminFramePlan.PassStep pass) {
                executePass(encoder, pass, sampler);
            }
        }
    }

    private void ensureTargets(GpuDevice device, int viewportWidth, int viewportHeight) {
        if (targets != null && targets.width() == viewportWidth
                && targets.height() == viewportHeight) {
            return;
        }
        if (targets != null) {
            targets.close();
        }
        targets = ShaderpackTargets.allocate(device, compiled.resourcePlan(),
                viewportWidth, viewportHeight);
        diagnostics.addAll(targets.diagnostics());
        width = viewportWidth;
        height = viewportHeight;
    }

    private void ensureUniformBuffer(GpuDevice device) {
        if (uniformBuffer != null) {
            return;
        }
        uniformBuffer = device.createBuffer(
                () -> "openlumin:shaderpack/uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                LuminShaderpackUniformBlock.byteSize());
    }

    private void writeUniforms(CommandEncoder encoder, LuminFrameUniforms uniforms) {
        ByteBuffer data = ByteBuffer.allocate(LuminShaderpackUniformBlock.byteSize())
                .order(ByteOrder.nativeOrder());
        LuminShaderpackUniformBlock.write(uniforms, data);
        data.rewind();
        encoder.writeToBuffer(uniformBuffer.slice(), data);
    }

    private void clearTargets(CommandEncoder encoder, int fogColorArgb) {
        LuminResourcePlan plan = compiled.resourcePlan();
        for (LuminResourcePlan.ClearBatch batch : plan.clearBatches()) {
            int argb = resolveClearColor(batch.color(), fogColorArgb);
            for (LuminTargetId target : batch.targets()) {
                if (isDepthTarget(target)) {
                    var texture = targets.texture(target, LuminResourceId.Copy.MAIN);
                    if (texture != null) {
                        encoder.clearDepthTexture(texture, 1.0);
                    }
                    continue;
                }
                var main = targets.texture(target, LuminResourceId.Copy.MAIN);
                if (main != null) {
                    encoder.clearColorTexture(main, argb);
                }
                if (batch.bothCopies()) {
                    var alt = targets.texture(target, LuminResourceId.Copy.ALT);
                    if (alt != null) {
                        encoder.clearColorTexture(alt, argb);
                    }
                }
            }
        }
    }

    private void executePass(CommandEncoder encoder, LuminFramePlan.PassStep pass,
                             GpuSampler sampler) {
        RenderPipeline pipeline = compiled.pipelineFor(pass.program());
        if (pipeline == null) {
            if (skippedPrograms.add(pass.program())) {
                diagnostics.add(Diagnostic.info(pass.program().sourceBaseName(), 0,
                        "pass skipped at execution: no compiled pipeline (geometry/compute deferred)"));
            }
            return;
        }
        LuminTargetId primary = primaryWriteTarget(pass.writes());
        if (primary == null) {
            warnOnce("empty-write:" + pass.program().sourceBaseName(),
                    Diagnostic.warning(pass.program().sourceBaseName(), 0,
                            "pass has no write target; skipped"));
            return;
        }
        if (pass.writes().size() > 1) {
            warnOnce("mrt:" + pass.program().sourceBaseName(),
                    Diagnostic.warning(pass.program().sourceBaseName(), 0,
                            "pass writes " + pass.writes().size()
                                    + " targets; 26.1.2 pass supports a single color attachment"
                                    + " — binding " + primary.canonicalName()));
        }
        GpuTextureView colorView = targets.view(primary, pass.writes().get(primary));
        if (colorView == null) {
            warnOnce("missing-view:" + primary.canonicalName(),
                    Diagnostic.warning(primary.canonicalName(), 0,
                            "write target has no allocated view; pass skipped"));
            return;
        }
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "openlumin:shaderpack/" + pass.program().sourceBaseName(),
                colorView, OptionalInt.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            boolean needsUniforms = pipeline.getUniforms().stream()
                    .anyMatch(u -> u.name().equals(LuminShaderpackUniformBlock.BLOCK_NAME));
            if (needsUniforms) {
                renderPass.setUniform(LuminShaderpackUniformBlock.BLOCK_NAME, uniformBuffer);
            }
            for (var read : pass.reads().entrySet()) {
                GpuTextureView view = targets.view(read.getKey(), read.getValue());
                if (view != null) {
                    renderPass.bindTexture(read.getKey().canonicalName(), view, sampler);
                }
            }
            renderPass.draw(3, 1);
        }
    }

    private void warnOnce(String key, Diagnostic diagnostic) {
        if (warnedOnce.add(key)) {
            diagnostics.add(diagnostic);
        }
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (targets != null) {
            targets.close();
            targets = null;
        }
    }

    /** 深度类目标（清屏走 clearDepthTexture）。 */
    static boolean isDepthTarget(LuminTargetId target) {
        return target.kind() == LuminTargetId.Kind.DEPTH
                || target.kind() == LuminTargetId.Kind.SHADOW_DEPTH;
    }

    /** 清屏色解析：字面量打包 ARGB；符号来源按帧上下文解析。 */
    public static int resolveClearColor(LuminResourceAllocation.ClearColor color,
                                        int fogColorArgb) {
        return switch (color.source()) {
            case LITERAL -> packArgb(color.rgba());
            case FOG -> fogColorArgb;
            case WHITE, DEPTH_FAR -> 0xFFFFFFFF;
            case TRANSPARENT_BLACK -> 0x00000000;
        };
    }

    /** RGBA[0..1] → ARGB int（与 GlCommandEncoder 的 ARGB 拆包一致）。 */
    public static int packArgb(float[] rgba) {
        if (rgba == null || rgba.length != 4) {
            throw new IllegalArgumentException("rgba must have exactly 4 components");
        }
        int a = channel(rgba[3]);
        int r = channel(rgba[0]);
        int g = channel(rgba[1]);
        int b = channel(rgba[2]);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(float value) {
        int v = Math.round(value * 255f);
        return Math.max(0, Math.min(255, v));
    }

    /**
     * MRT 降级时的确定性主写目标：最小 (kind, index)。
     * <p>{@code PassStep.writes} 为 {@code Map.copyOf}（不保序），故必须显式定序。</p>
     */
    static LuminTargetId primaryWriteTarget(java.util.Map<LuminTargetId, LuminResourceId.Copy> writes) {
        return writes.keySet().stream()
                .min(Comparator.comparingInt((LuminTargetId t) -> t.kind().ordinal())
                        .thenComparingInt(LuminTargetId::index))
                .orElse(null);
    }
}
