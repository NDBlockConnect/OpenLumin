package io.github.openlumin.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import io.github.openlumin.chunk.batch.LuminBatchCapabilities;
import io.github.openlumin.chunk.batch.LuminBatchPath;
import io.github.openlumin.chunk.batch.LuminBatchPlanner;
import io.github.openlumin.chunk.batch.LuminBatchTraits;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
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

    /**
     * 能力驱动的批量绘制（M5b）：按设备能力与批次特征选择路径——
     * 分离缓冲多 draw（GL/Vulkan 皆可）→ 聚合通路 → 逐 draw。
     *
     * <p>选路逻辑在纯 CPU 的 {@code LuminBatchPlanner} 中（有单测覆盖）；
     * 本方法只负责把选定路径落到 vanilla API。批次若存在 per-draw uniform 差异
     * 或不共享 IBO，则规划器会排除直接形态、退到聚合通路（语义等价，仅少一层优化）。</p>
     *
     * @param capabilities 设备能力（26.2 可用 {@code LuminBatchCapabilityProbe.detect()}）
     * @param useIndirect  间接命令是否已就绪（M5c 接入 GPU 命令缓冲后置 true）
     */
    public void drawSectionsBatched(RenderPass pass, LuminSectionBatch batch,
                                    LuminBatchCapabilities capabilities, boolean useIndirect) {
        List<LuminSectionDraw> draws = batch.draws();
        GpuBuffer sharedIndexBuffer = batch.sharedIndexBuffer();
        IndexType sharedIndexType = batch.sharedIndexType();
        boolean sharedVertexBuffer = true;
        GpuBuffer firstVertexBuffer = draws.get(0).vertexSlice().buffer();
        for (LuminSectionDraw draw : draws) {
            if (draw.vertexSlice().buffer() != firstVertexBuffer) {
                sharedVertexBuffer = false;
                break;
            }
        }
        LuminBatchTraits traits = new LuminBatchTraits(
                draws.size(),
                sharedIndexBuffer != null,
                sharedIndexType != null,
                sharedVertexBuffer,
                true,
                false,
                useIndirect);
        LuminBatchPath path = LuminBatchPlanner.plan(capabilities, traits);
        switch (path) {
            case MULTI_DRAW_SEPARATE, MULTI_DRAW_INTERLEAVED -> drawMultiDrawSeparate(pass, draws,
                    sharedIndexBuffer, sharedIndexType);
            case DRAW_MULTIPLE_INDEXED, MULTI_DRAW_INDIRECT -> drawSectionsBatched(pass, batch);
            case PER_DRAW -> drawSections(pass, draws);
        }
    }

    /**
     * 分离缓冲多 draw（26.2 签名实证：{@code multiDrawIndexed(PointerBuffer indices,
     * IntBuffer counts, IntBuffer baseVertices, int instanceCount)}）——
     * 每 draw 的索引起始（字节偏移指针）、索引计数、baseVertex 三个数组 + 统一实例数。
     * <p>要求全部 draw 共享 IBO 与索引类型；顶点缓冲绑定取首 draw 的 slice
     * （分离形态各 draw 以 baseVertex 区分顶点区段，因此要求顶点数据在同一缓冲内）。</p>
     */
    private void drawMultiDrawSeparate(RenderPass pass, List<LuminSectionDraw> draws,
                                       GpuBuffer indexBuffer, IndexType indexType) {
        if (indexBuffer == null || indexType == null) {
            throw new IllegalArgumentException("separate multi-draw requires a shared index buffer and type");
        }
        int count = draws.size();
        PointerBuffer indexOffsets = MemoryUtil.memAllocPointer(count);
        IntBuffer indexCounts = MemoryUtil.memAllocInt(count);
        IntBuffer baseVertices = MemoryUtil.memAllocInt(count);
        try {
            GpuBuffer vertexBuffer = draws.get(0).vertexSlice().buffer();
            for (int i = 0; i < count; i++) {
                LuminSectionDraw draw = draws.get(i);
                if (draw.vertexSlice().buffer() != vertexBuffer) {
                    throw new IllegalArgumentException(
                            "separate multi-draw requires all draws to share one vertex buffer");
                }
                // 索引起始字节偏移 = firstIndex × 索引元素宽度
                indexOffsets.put(i, (long) draw.firstIndex() * indexType.bytes);
                indexCounts.put(i, draw.indexCount());
                baseVertices.put(i, draw.baseVertex());
            }
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.setVertexBuffer(0, draws.get(0).vertexSlice());
            pass.multiDrawIndexed(indexOffsets, indexCounts, baseVertices, 1);
        } finally {
            MemoryUtil.memFree(indexOffsets);
            MemoryUtil.memFree(indexCounts);
            MemoryUtil.memFree(baseVertices);
        }
    }

    private static void setIndexBuffer(RenderPass pass, GpuBuffer indexBuffer, IndexType indexType) {
        if (indexBuffer == null || indexType == null) {
            throw new IllegalArgumentException("indexBuffer and indexType are required for indexed section draws");
        }
        pass.setIndexBuffer(indexBuffer, indexType);
    }
}
