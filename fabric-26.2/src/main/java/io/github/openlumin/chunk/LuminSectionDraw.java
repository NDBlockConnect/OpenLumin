package io.github.openlumin.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * 单个 section 的绘制请求（数据包，不可变）。
 * <p>字段序即 26.2 原生 drawIndexed 参数序（见 {@link LuminSectionRenderer} 的双基线对照表）；
 * M2 instanceCount 固定 1（instancing 批量随 M3 扩展）。</p>
 * <p>{@code strideBytes}：顶点记录步长。单 draw 的 slice 精确绑定不使用它；
 * 批量通路（vanilla Draw 仅接受整 buffer 绑定）用它把 slice 偏移折算进 baseVertex，
 * 因此 slice 偏移必须是 stride 的整数倍。</p>
 */
public record LuminSectionDraw(
        GpuBufferSlice vertexSlice,
        int strideBytes,
        GpuBuffer indexBuffer,
        IndexType indexType,
        int indexCount,
        int firstIndex,
        int baseVertex) {
}
