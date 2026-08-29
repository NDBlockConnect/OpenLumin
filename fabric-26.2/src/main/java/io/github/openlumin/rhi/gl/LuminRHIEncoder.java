package io.github.openlumin.rhi.gl;

import io.github.openlumin.rhi.*;
import net.minecraft.util.ARGB;

import java.nio.ByteBuffer;

/**
 * LuminRHI GL 后端的命令编码器 + 渲染通道门面
 *
 * 调用顺序：
 *   encoder = rhi.createEncoder("op")
 *   pass = encoder.beginRenderPass(desc)
 *   pass.setPipeline / setVertexBuffer / setIndexBuffer / setUniformBuffer / setTexture
 *   pass.draw / pass.drawIndexed
 *   pass.close() → encoder.finish() → LuminCommandBuffer
 *   rhi.submit(buffer) → 翻译为 MC RenderSystem 命令并执行
 */
public final class LuminRHIEncoder {

    private LuminRHIEncoder() {}

    /** LuminCommandEncoder 门面——直接将动作记录到底层 CommandBufferImpl。 */
    public static final class CommandEncoderImpl implements LuminCommandEncoder {
        private final LuminRHICommands.CommandBufferImpl buffer;

        public CommandEncoderImpl(String label) {
            this.buffer = new LuminRHICommands.CommandBufferImpl(label);
        }

        @Override
        public LuminRenderPass beginRenderPass(LuminRenderPassDesc desc) {
            RenderPassRecorder rec = new RenderPassRecorder(desc, buffer);
            return rec.passFacade;
        }

        @Override
        public LuminBuffer uploadBuffer(LuminBufferUsage usage, ByteBuffer data) {
            throw new UnsupportedOperationException("use LuminDevice.createBuffer then write data via mapped memory (Alpha 2.1)");
        }

        @Override
        public LuminTexture uploadTexture2D(int width, int height, LuminFormat format, ByteBuffer pixels) {
            throw new UnsupportedOperationException("use LuminDevice.createTexture2D then write pixels (Alpha 2.1)");
        }

        @Override
        public LuminCommandBuffer finish() { return buffer; }

        @Override public void close() { }
    }

    public static final class RenderPassRecorder {
        final LuminRHICommands.CommandBufferImpl buffer;
        final LuminRenderPassDesc desc;
        final RenderPassImpl passFacade = new RenderPassImpl();

        RenderPassRecorder(LuminRenderPassDesc desc, LuminRHICommands.CommandBufferImpl buffer) {
            this.desc = desc; this.buffer = buffer;
        }

        public final class RenderPassImpl implements LuminRenderPass {
            @Override
            public void setPipeline(LuminPipeline pipeline) {
                buffer.add(new LuminRHICommands.SetPipelineAction((LuminPipelineGL) pipeline));
            }
            @Override
            public void setVertexBuffer(int slot, LuminBufferView view) {
                buffer.add(new LuminRHICommands.SetVertexBufferAction(slot, view));
            }
            @Override
            public void setIndexBuffer(LuminBuffer buffer, LuminIndexType type) {
                RenderPassRecorder.this.buffer.add(new LuminRHICommands.SetIndexBufferAction((LuminBufferGL) buffer, type));
            }
            @Override
            public void setUniformBuffer(int slot, String name, LuminBufferView view) {
                buffer.add(new LuminRHICommands.SetUniformBufferAction(slot, name, view));
            }
            @Override
            public void setTexture(int slot, String name, LuminTextureView view, LuminSampler sampler) {
                buffer.add(new LuminRHICommands.SetTextureAction(slot, name, view, (LuminSamplerGL) sampler));
            }
            @Override public void setFramebufferSize(int width, int height) { }
            @Override
            public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
                buffer.add(new LuminRHICommands.DrawAction(vertexCount, instanceCount, firstVertex, firstInstance));
            }
            @Override
            public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
                buffer.add(new LuminRHICommands.DrawIndexedAction(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance));
            }
            @Override public void close() { }
        }
    }

    /**
     * LuminBufferGL 的颜色写入助手（Alpha 2 占位）。
     * 颜色字节序调序：int color → ARGB.toABGR（参 26.2 putColor 修复）。
     */
    public static void writeColorLuminBuffer(LuminBufferGL buf, long byteOffset, int argb) {
        int abgr = ARGB.toABGR(argb);
        // TODO Alpha 2.1：实现 persistent-mapped buffer 增量写入
    }
}
