package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.rhi.*;
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

        /** Alpha 2 占位：uploadTexture 真正格式转换 + 逐行上传在 Alpha 2.1。 */
        private void uploadTexture(LuminTextureGL tex, ByteBuffer pixels) { }

        /** Alpha 2 占位：executeRenderPass 完整逻辑（MC 26.2 RenderPass 资源绑定）在 Alpha 2.1。 */
        private void executeRenderPass(CommandEncoder encoder, RenderPassCmd rp) { }

        @Override public void close() { }
    }

    /** 懒绑定 LuminShader 路径到 MC RenderPipeline。Alpha 2 占位。 */
    private static com.mojang.blaze3d.pipeline.RenderPipeline bind(LuminPipelineGL ppl) {
        return null;
    }

    /** LuminBufferGL 的颜色写入助手（Alpha 2 占位）。 */
    public static void writeColorLuminBuffer(LuminBufferGL buf, long byteOffset, int argb) {
        int abgr = ARGB.toABGR(argb);
        // TODO Alpha 2.1：实现 persistent-mapped buffer 增量写入
    }
}
