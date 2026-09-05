package io.github.openlumin.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 级绘制的<b>唯一出口</b>：所有区块网格 drawIndexed 集中于此，
 * 消化 26.x 双基线的参数序差异——业务层不得直接调用 RenderPass.drawIndexed/draw。
 *
 * <p>双基线 vanilla 签名（均为 javap 反汇编 deobf jar 的压栈序 + GL 消费点实证）：</p>
 * <pre>
 *   MC 26.1.2（GlCommandEncoder.drawFromBuffers → glDrawElements*BaseVertex）：
 *     drawIndexed(baseVertex, firstIndex, indexCount, instanceCount)
 *     draw(firstVertex, vertexCount)                      // instanceCount 恒 1
 *   MC 26.2（GuiRenderer 压栈序实证，FACT 2026-08-25）：
 *     drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, baseInstance)
 *     draw(vertexCount, instanceCount, firstVertex, baseInstance)
 * </pre>
 * <p>26.2 跟进时在本类增加对应分支（或按基线提供实现），签名语义不变。</p>
 */
public final class LuminSectionRenderer {

    /**
     * 绘制单个 section。pipeline/绑定的 uniform/纹理由调用方在 pass 上先行设置；
     * 顶点槽位固定 0，索引缓冲每次重绑（RenderPass 无查询接口，正确性优先）。
     */
    public void drawSection(RenderPass pass, LuminSectionDraw draw) {
        pass.setVertexBuffer(0, draw.vertexBuffer());
        setIndexBuffer(pass, draw.indexBuffer(), draw.indexType());
        pass.drawIndexed(draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1);
    }

    /**
     * 按序绘制一批 section（典型：同 region 内按 baseVertex 升序，利于顶点缓存局部性）。
     */
    public void drawSections(RenderPass pass, List<LuminSectionDraw> draws) {
        for (LuminSectionDraw draw : draws) {
            drawSection(pass, draw);
        }
    }

    /**
     * 批量绘制：走 vanilla {@code drawMultipleIndexed} 聚合通路
     * （逐 draw 的 VBO/IBO 绑定与 uniform 上传由 vanilla 循环内完成，省 Java 层调用栈）。
     * 批次内 IBO/IndexType 不一致时 vanilla 逐 draw 重绑，语义与逐 draw 等价。
     *
     * @throws UnsupportedOperationException 后端不提供批量通路时（调用方可回退
     *                                      {@link #drawSections(RenderPass, List)}）
     */
    public void drawSectionsBatched(RenderPass pass, LuminSectionBatch batch) {
        List<LuminSectionDraw> draws = batch.draws();
        List<RenderPass.Draw<Object>> vanillaDraws = new ArrayList<>(draws.size());
        for (LuminSectionDraw draw : draws) {
            vanillaDraws.add(new RenderPass.Draw<>(
                    0, draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(),
                    draw.firstIndex(), draw.indexCount(), draw.baseVertex()));
        }
        pass.drawMultipleIndexed(vanillaDraws, batch.sharedIndexBuffer(),
                batch.sharedIndexType(), null, null);
    }

    private static void setIndexBuffer(RenderPass pass, GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
        if (indexBuffer == null || indexType == null) {
            throw new IllegalArgumentException("indexBuffer and indexType are required for indexed section draws");
        }
        pass.setIndexBuffer(indexBuffer, indexType);
    }
}
