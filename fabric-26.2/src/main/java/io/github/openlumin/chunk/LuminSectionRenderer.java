package io.github.openlumin.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 级绘制的<b>唯一出口</b>：所有区块网格 drawIndexed 集中于此，
 * 消化 26.x 双基线的参数序差异——业务层不得直接调用 RenderPass.drawIndexed/draw。
 *
 * <p>双基线 vanilla 签名（均为 javap 反汇编 deobf jar 的压栈序 + GL 消费点实证）：</p>
 * <pre>
 *   MC 26.2（GuiRenderer 压栈序 + FACT 2026-08-25）：
 *     drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, baseInstance)
 *     draw(vertexCount, instanceCount, firstVertex, baseInstance)
 *   MC 26.1.2（GlCommandEncoder.drawFromBuffers → glDrawElements*BaseVertex，2026-08-31）：
 *     drawIndexed(baseVertex, firstIndex, indexCount, instanceCount)
 *     draw(firstVertex, vertexCount)                      // instanceCount 恒 1
 * </pre>
 * <p>26.2 还可用 multiDrawIndexed/drawIndexedIndirect（vanilla 原生，M3 批量路径候选）。</p>
 */
public final class LuminSectionRenderer {

    /**
     * 绘制单个 section。pipeline/绑定的 uniform/纹理由调用方在 pass 上先行设置；
     * 顶点槽位固定 0，索引缓冲每次重绑（RenderPass 无查询接口，正确性优先）。
     */
    public void drawSection(RenderPass pass, LuminSectionDraw draw) {
        pass.setVertexBuffer(0, draw.vertexSlice());
        setIndexBuffer(pass, draw.indexBuffer(), draw.indexType());
        pass.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
    }

    /**
     * 按序逐个绘制一批 section（批量通路的回退路径）。
     */
    public void drawSections(RenderPass pass, List<LuminSectionDraw> draws) {
        for (LuminSectionDraw draw : draws) {
            drawSection(pass, draw);
        }
    }

    /**
     * 批量绘制：走 vanilla {@code drawMultipleIndexed} 聚合通路
     * （逐 draw 的 VBO/IBO 绑定与 uniform 上传由 vanilla 循环内完成）。
     * <p>vanilla Draw 仅接受整 buffer 顶点绑定，因此 slice 偏移经
     * {@code strideBytes} 折算进 baseVertex（slice 偏移必须是 stride 的整数倍，
     * LuminChunkStore 的 16 字节分配对齐与标准顶点步长满足此约束）。</p>
     *
     * @throws UnsupportedOperationException 后端不提供批量通路时（调用方可回退
     *                                      {@link #drawSection(RenderPass, LuminSectionDraw)} 循环）
     * @throws IllegalArgumentException      slice 偏移未按 stride 对齐
     */
    public void drawSectionsBatched(RenderPass pass, LuminSectionBatch batch) {
        List<LuminSectionDraw> draws = batch.draws();
        List<RenderPass.Draw<Object>> vanillaDraws = new ArrayList<>(draws.size());
        for (LuminSectionDraw draw : draws) {
            long offset = draw.vertexSlice().offset();
            if (offset % draw.strideBytes() != 0) {
                throw new IllegalArgumentException(
                        "vertex slice offset " + offset + " not aligned to stride " + draw.strideBytes());
            }
            int baseVertex = Math.toIntExact(offset / draw.strideBytes()) + draw.baseVertex();
            vanillaDraws.add(new RenderPass.Draw<>(
                    0, draw.vertexSlice().buffer(), draw.indexBuffer(), draw.indexType(),
                    draw.firstIndex(), draw.indexCount(), baseVertex));
        }
        pass.drawMultipleIndexed(vanillaDraws, batch.sharedIndexBuffer(),
                batch.sharedIndexType(), null, null);
    }

    private static void setIndexBuffer(RenderPass pass, GpuBuffer indexBuffer, IndexType indexType) {
        if (indexBuffer == null || indexType == null) {
            throw new IllegalArgumentException("indexBuffer and indexType are required for indexed section draws");
        }
        pass.setIndexBuffer(indexBuffer, indexType);
    }
}
