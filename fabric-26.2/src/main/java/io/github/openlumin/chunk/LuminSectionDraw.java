package io.github.openlumin.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * 单个 section 的绘制请求（数据包，不可变）。
 * <p>字段序即 26.2 原生 drawIndexed 参数序（见 {@link LuminSectionRenderer} 的双基线对照表）；
 * M2 instanceCount 固定 1（instancing 批量随 M3 扩展）。</p>
 */
public record LuminSectionDraw(
        GpuBufferSlice vertexSlice,
        GpuBuffer indexBuffer,
        IndexType indexType,
        int indexCount,
        int firstIndex,
        int baseVertex) {
}
