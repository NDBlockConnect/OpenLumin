package io.github.openlumin.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.List;

/**
 * 批量绘制请求（不可变）：同 pipeline 下的一组 section 绘制。
 * <p>共享判定：全部 draw 使用同一 indexBuffer 时自动按共享 IBO 提交
 * （同 region 的 section 天然满足）；混用 IBO 的批次由 vanilla 逐 draw 重绑。</p>
 */
public record LuminSectionBatch(List<LuminSectionDraw> draws) {

    public LuminSectionBatch {
        if (draws == null || draws.isEmpty()) {
            throw new IllegalArgumentException("batch must contain at least one draw");
        }
        draws = List.copyOf(draws);
    }

    /** 批次内所有 draw 共用的 indexBuffer；不一致返回 null（vanilla 端逐 draw 重绑）。 */
    public GpuBuffer sharedIndexBuffer() {
        GpuBuffer first = draws.get(0).indexBuffer();
        for (LuminSectionDraw draw : draws) {
            if (draw.indexBuffer() != first) {
                return null;
            }
        }
        return first;
    }

    /** 批次内所有 draw 共用的 indexType；不一致返回 null。 */
    public VertexFormat.IndexType sharedIndexType() {
        VertexFormat.IndexType first = draws.get(0).indexType();
        for (LuminSectionDraw draw : draws) {
            if (draw.indexType() != first) {
                return null;
            }
        }
        return first;
    }
}
