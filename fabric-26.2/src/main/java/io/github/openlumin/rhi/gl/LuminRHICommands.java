package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.openlumin.rhi.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * LuminRHI GL 后端的命令编码器、命令缓冲、渲染通道实现（Alpha 2 占位）
 *
 * Alpha 2 范围：接口 + 录制结构 + draw/drawIndexed 参数语义（参 26.2 修复实证）。
 * Alpha 2.1 范围：完整的 MC 26.2 RenderPass 资源绑定（setPipeline / setVertexBuffer / setIndexBuffer /
 * setUniformBuffer / bindTexture / draw / drawIndexed 翻译链）。
 *
 * 颜色字节序：ARGB → toABGR 调序（参 26.2 putColor 修复）。
 */
public final class LuminRHICommands {

    private LuminRHICommands() {}

    interface RecordedCommand {}

    record UploadBufferCmd(LuminBufferGL target, ByteBuffer data) implements RecordedCommand {}
    record UploadTextureCmd(LuminTextureGL target, ByteBuffer pixels) implements RecordedCommand {}
    record RenderPassCmd(LuminRenderPassDesc desc, List<RenderPassAction> actions) implements RecordedCommand {}

    /** Alpha 2 简化：取消 sealed/permits；动作记录直接实现 RecordedCommand 链。 */
    interface RenderPassAction extends RecordedCommand {}

    record SetPipelineAction(LuminPipelineGL pipeline) implements RenderPassAction {}
    record SetVertexBufferAction(int slot, LuminBufferView view) implements RenderPassAction {}
    record SetIndexBufferAction(LuminBufferGL buffer, LuminIndexType type) implements RenderPassAction {}
    record SetUniformBufferAction(int slot, String name, LuminBufferView view) implements RenderPassAction {}
    record SetTextureAction(int slot, String name, LuminTextureView view, LuminSamplerGL sampler) implements RenderPassAction {}
    record DrawAction(int vertexCount, int instanceCount, int firstVertex, int firstInstance) implements RenderPassAction {}
    record DrawIndexedAction(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) implements RenderPassAction {}

    static final class CommandBufferImpl implements LuminCommandBuffer {
        final String label;
        final List<RecordedCommand> commands = new ArrayList<>();
        int drawCalls = 0;

        CommandBufferImpl(String label) { this.label = label; }

        void add(RecordedCommand cmd) {
            commands.add(cmd);
            if (cmd instanceof RenderPassCmd) {
                drawCalls += ((RenderPassCmd) cmd).actions.stream()
                    .filter(a -> a instanceof DrawAction || a instanceof DrawIndexedAction).count();
            }
        }

        @Override public int drawCallCount() { return drawCalls; }

        /** 提交到 MC RenderSystem。Alpha 2 占位：完整 MC 26.2 RenderPass 翻译在 Alpha 2.1 范围。 */
        void submit() {
            // 占位：仅创建 encoder 引用（实际资源绑定留待 Alpha 2.1）。
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (RecordedCommand cmd : commands) {
                if (cmd instanceof RenderPassCmd rp) {
                    executeRenderPass(encoder, rp);
                }
            }
        }

        /** Alpha 2.1 实现：uploadTexture 占位（纹理上传走 LuminTextureGL.writeRawBytes）。 */
        private void uploadTexture(LuminTextureGL tex, ByteBuffer pixels) { }

        /**
         * Alpha 2.1 实现：完整 MC 26.2 RenderPass 翻译链。
         * 按 8 个 RenderPassAction 顺序执行：
         *   SetPipeline → SetVertexBuffer* → SetIndexBuffer → SetUniformBuffer* → SetTexture* → Draw / DrawIndexed
         * 全部在 try-with-resources RenderPass 块内。
         */
        private void executeRenderPass(CommandEncoder encoder, RenderPassCmd rp) {
            // 解析 color/depth attachment — 业务层 LuminTextureView 必须映到 MC GpuTextureView。
            // 26.2 缺失 GpuTexture.getColorTextureView()；用 RenderTarget 包装（见 LuminRHI_GL.Swapchain 桥接）。
            com.mojang.blaze3d.textures.GpuTextureView colorView = toMcTextureView(rp.desc.colorAttachment());
            com.mojang.blaze3d.textures.GpuTextureView depthView = rp.desc.depthAttachment() != null
                    ? toMcTextureView(rp.desc.depthAttachment())
                    : null;
            // createRenderPass 多参版本：color + clearColor + depth + clearDepth + area
            // 26.2 缺 depth = 0 的 5 参 overload（须用 4 参 + 6 参）。
            try (com.mojang.blaze3d.systems.RenderPass pass = depthView != null
                    ? encoder.createRenderPass(() -> "lumin_pass",
                            colorView, java.util.Optional.empty(),
                            depthView, java.util.OptionalDouble.empty())
                    : encoder.createRenderPass(() -> "lumin_pass",
                            colorView, java.util.Optional.empty())) {
                for (RenderPassAction action : rp.actions) {
                    if (action instanceof SetPipelineAction sp) {
                        if (sp.pipeline() instanceof LuminPipelineGL ppl) {
                            if (ppl.mcPipeline == null) {
                                ppl.mcPipeline = bind(ppl);
                            }
                            if (ppl.mcPipeline != null) {
                                pass.setPipeline(ppl.mcPipeline);
                            }
                        }
                    } else if (action instanceof SetVertexBufferAction sv) {
                        LuminBufferGL lb = (LuminBufferGL) sv.view().buffer();
                        // GpuBuffer.slice(offset, length) → GpuBufferSlice（MC 26.2）
                        pass.setVertexBuffer(sv.slot(), lb.gbuf.slice(sv.view().offset(), sv.view().length()));
                    } else if (action instanceof SetIndexBufferAction si) {
                        LuminBufferGL lb = (LuminBufferGL) si.buffer();
                        // MC 26.2 路径：com.mojang.blaze3d.IndexType（不在 VertexFormat 内）
                        com.mojang.blaze3d.IndexType mcType = switch (si.type()) {
                            case UINT16 -> com.mojang.blaze3d.IndexType.SHORT;
                            case UINT32 -> com.mojang.blaze3d.IndexType.INT;
                        };
                        pass.setIndexBuffer(lb.gbuf, mcType);
                    } else if (action instanceof SetUniformBufferAction su) {
                        LuminBufferGL lb = (LuminBufferGL) su.view().buffer();
                        com.mojang.blaze3d.buffers.GpuBufferSlice slice =
                                lb.gbuf.slice(su.view().offset(), su.view().length());
                        pass.setUniform(su.name(), slice);
                    } else if (action instanceof SetTextureAction st) {
                        if (st.view() instanceof LuminTextureViewGL vw) {
                            com.mojang.blaze3d.textures.GpuTextureView mcView = vw.toMc();
                            if (st.sampler() instanceof LuminSamplerGL sg) {
                                pass.bindTexture(st.name(), mcView, sg.gsamp);
                            }
                        }
                    } else if (action instanceof DrawAction d) {
                        // draw(vertexCount, instanceCount, firstVertex, firstInstance)
                        pass.draw(d.firstVertex(), d.vertexCount(), d.instanceCount(), d.firstInstance());
                    } else if (action instanceof DrawIndexedAction di) {
                        // 26.2 实证语义：drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)
                        pass.drawIndexed(di.indexCount(), di.instanceCount(), di.firstIndex(), di.vertexOffset(), di.firstInstance());
                    }
                }
            }
        }

        /** LuminTextureView → MC GpuTextureView 桥接。 */
        private static com.mojang.blaze3d.textures.GpuTextureView toMcTextureView(LuminTextureView view) {
            if (view instanceof LuminTextureViewGL vw) {
                return vw.toMc();
            }
            throw new UnsupportedOperationException("Alpha 2.1: only LuminTextureViewGL supported");
        }

        @Override public void close() { }
    }

    /** Alpha 2.1 实现：将 LuminShader 路径懒绑定到 MC RenderPipeline。
     *
     * 路径约定（与 LuminRenderPipelines.RECTANGLE 对齐）：
     *   - vertexPath / fragmentPath 形如 "rectangle" → namespace = "openlumin"
     *   - LuminVertexFormat.attributes() 决定顶点格式（POSITION_COLOR / POSITION_TEXTURE_COLOR 等）
     *   - LuminPipelineState 决定 blend / depth / cull / polygon
     */
    private static com.mojang.blaze3d.pipeline.RenderPipeline bind(LuminPipelineGL ppl) {
        LuminShader shader = ppl.shader();
        if (!(shader instanceof LuminShaderGL sgl)) {
            return null;
        }
        // 路径解析：namespace = "openlumin"，path = shader 路径
        String vPath = sgl.vertexPath;
        String fPath = sgl.fragmentPath;
        if (vPath == null || fPath == null) {
            return null;
        }
        Identifier vId = Identifier.fromNamespaceAndPath("openlumin", vPath);
        Identifier fId = Identifier.fromNamespaceAndPath("openlumin", fPath);
        Identifier locId = Identifier.fromNamespaceAndPath("openlumin", "pipelines/" + vPath);

        // 顶点格式（按 LuminVertexFormat.attributes 推断 → DefaultVertexFormat 常量）
        VertexFormat mcVtx = toMcVertexFormat(ppl.vertexFormat());

        // 拓扑（MC 26.2 PrimitiveTopology：LINES/TRIANGLES/QUADS/POINTS/TRIANGLE_STRIP/TRIANGLE_FAN）
        PrimitiveTopology primTopo = toMcPrimitiveTopology(ppl.vertexFormat());

        // 渲染状态
        LuminPipelineState st = ppl.state();
        boolean depthTest = st.depthTest();
        boolean depthWrite = st.depthWrite();
        CompareOp depthCmp = toMcCompareOp(st.depthCompare());
        DepthStencilState ds = new DepthStencilState(depthCmp, depthWrite);

        // 颜色目标 + 混合
        LuminBlendState blend = st.blend();
        boolean blendEnabled = blend.enabled();
        // ColorTargetState(BlendFunction) 单参构造器：GpuFormat 默认 + WRITE_COLOR
        com.mojang.blaze3d.pipeline.ColorTargetState cts;
        if (blendEnabled) {
            cts = new com.mojang.blaze3d.pipeline.ColorTargetState(
                    com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT);
        } else {
            // 不混合：BlendFunction.OVERLAY 单参构造器（不启用 blend）
            cts = new com.mojang.blaze3d.pipeline.ColorTargetState(
                    com.mojang.blaze3d.pipeline.BlendFunction.OVERLAY);
        }

        // 构造
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(locId)
                .withVertexShader(vId)
                .withFragmentShader(fId)
                .withVertexBinding(0, mcVtx)
                .withPrimitiveTopology(primTopo)
                .withPolygonMode(toMcPolygonMode(st.polygonMode()))
                .withCull(st.cullMode() != LuminCullMode.NONE)
                .withDepthStencilState(ds)
                .withColorTargetState(0, cts);

        return b.build();
    }

    /** LuminVertexFormat.attributes → MC VertexFormat 常量映射。
     * MC 26.2 DefaultVertexFormat 常量：POSITION / POSITION_COLOR / POSITION_TEX /
     * POSITION_TEX_COLOR / POSITION_COLOR_NORMAL / POSITION_COLOR_LIGHTMAP。 */
    private static VertexFormat toMcVertexFormat(LuminVertexFormat vf) {
        var attrs = vf.attributes();
        if (attrs == null || attrs.length == 0) return DefaultVertexFormat.POSITION;
        if (attrs.length == 1) return DefaultVertexFormat.POSITION;
        if (attrs.length == 2) return DefaultVertexFormat.POSITION_TEX;
        if (attrs.length == 3) return DefaultVertexFormat.POSITION_TEX_COLOR;
        return DefaultVertexFormat.POSITION_COLOR;
    }

    private static PrimitiveTopology toMcPrimitiveTopology(LuminVertexFormat vf) {
        // MC 26.2 PrimitiveTopology 只有：LINES/TRIANGLES/QUADS/POINTS/TRIANGLE_STRIP/TRIANGLE_FAN
        // 简化为：POSITION_COLOR → QUADS，POSITION_TEX_COLOR → TRIANGLES。
        var attrs = vf.attributes();
        if (attrs == null) return PrimitiveTopology.TRIANGLES;
        if (attrs.length == 1) return PrimitiveTopology.QUADS;
        return PrimitiveTopology.TRIANGLES;
    }

    private static PolygonMode toMcPolygonMode(LuminPolygonMode m) {
        // MC 26.2 PolygonMode 仅 FILL / WIREFRAME；LINE/POINT 退化到 WIREFRAME。
        return switch (m) {
            case FILL -> PolygonMode.FILL;
            case LINE, POINT -> PolygonMode.WIREFRAME;
        };
    }

    private static CompareOp toMcCompareOp(LuminCompareOp m) {
        return switch (m) {
            case NEVER -> CompareOp.NEVER_PASS;
            case LESS -> CompareOp.LESS_THAN;
            case EQUAL -> CompareOp.EQUAL;
            case LEQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
            case GREATER -> CompareOp.GREATER_THAN;
            case NOTEQUAL -> CompareOp.NOT_EQUAL;
            case GEQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
            case ALWAYS -> CompareOp.ALWAYS_PASS;
        };
    }

    /** LuminBufferGL 的颜色写入助手（Alpha 2 占位）。 */
    public static void writeColorLuminBuffer(LuminBufferGL buf, long byteOffset, int argb) {
        int abgr = ARGB.toABGR(argb);
        // TODO Alpha 2.1：实现 persistent-mapped buffer 增量写入
    }
}
