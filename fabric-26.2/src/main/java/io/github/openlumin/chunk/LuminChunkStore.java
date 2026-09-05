package io.github.openlumin.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.chunk.store.LuminChunkStoreLedger;
import io.github.openlumin.chunk.store.LuminRegionAllocator;
import io.github.openlumin.chunk.store.LuminSectionAllocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 区块存储（Store 层，26.2 基线）：regionKey → 独立 vertex/index GPU 缓冲对
 * + 槽位账本（{@link LuminChunkStoreLedger}）。
 * <p>与 26.1.2 版（io.github.openlumin.chunk.LuminChunkStore@fabric-26.1.2）的差异点：
 * 26.2 的 map 走 {@code GpuBufferSlice.map}（非 encoder.mapBuffer）；绑定点收 slice。</p>
 * <p>容量策略：region 槽位不足时 {@link #upload} 抛出（不自动扩容）——扩容/重建由消费方
 * {@link #retire} + 重新 {@link #upload}（新 region）决策，数据重放属游戏侧职责。
 * 生命周期：{@link #retire} 立即销毁 GPU 缓冲——消费方须在渲染线程安全点调用。非线程安全。</p>
 */
public final class LuminChunkStore implements AutoCloseable {

    private static final long DEFAULT_VERTEX_CAPACITY = 1L << 20;
    private static final long DEFAULT_INDEX_CAPACITY = 1L << 18;
    private static final long ALLOC_ALIGNMENT = 16;

    private static final int REGION_USAGE =
            GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;

    private final LuminChunkStoreLedger ledger = new LuminChunkStoreLedger();
    private final Map<Long, RegionBuffers> regions = new HashMap<>();

    private static final class RegionBuffers implements AutoCloseable {
        final GpuBuffer vertexBuffer;
        final GpuBuffer indexBuffer;
        final long vertexCapacity;
        final long indexCapacity;

        RegionBuffers(long regionKey, long vertexCapacity, long indexCapacity) {
            this.vertexCapacity = vertexCapacity;
            this.indexCapacity = indexCapacity;
            this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "lumin-chunk-region-" + regionKey + "-vertex",
                    REGION_USAGE | GpuBuffer.USAGE_VERTEX, vertexCapacity);
            this.indexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "lumin-chunk-region-" + regionKey + "-index",
                    REGION_USAGE | GpuBuffer.USAGE_INDEX, indexCapacity);
        }

        GpuBufferSlice vertexSlice(long offset, long bytes) {
            return vertexBuffer.slice(offset, bytes);
        }

        void upload(GpuBuffer buffer, long offset, ByteBuffer data) {
            try (GpuBufferSlice.MappedView mapped = buffer.slice(offset, data.remaining()).map(false, true)) {
                MemoryUtil.memCopy(data, mapped.data());
            }
        }

        @Override
        public void close() {
            vertexBuffer.close();
            indexBuffer.close();
        }
    }

    /** 获取 region（不存在则以默认容量创建，幂等）。 */
    public RegionBuffers region(long regionKey) {
        return regions.computeIfAbsent(regionKey, key -> {
            ledger.acquire(key, DEFAULT_VERTEX_CAPACITY, DEFAULT_INDEX_CAPACITY);
            return new RegionBuffers(key, DEFAULT_VERTEX_CAPACITY, DEFAULT_INDEX_CAPACITY);
        });
    }

    /**
     * 上传一个 section 的网格数据到 region 槽位（渲染线程）。
     *
     * @throws IllegalStateException region 槽位不足（消费方应 retire 后以新 region 重建）
     */
    public LuminSectionAllocation upload(long regionKey, ByteBuffer vertexData, ByteBuffer indexData) {
        RegionBuffers buffers = region(regionKey);
        LuminChunkStoreLedger.RegionHandle handle = ledger.region(regionKey).orElseThrow();
        int vertexBytes = vertexData.remaining();
        int indexBytes = indexData.remaining();
        long vertexOffset = handle.vertexAllocator().allocate(vertexBytes, ALLOC_ALIGNMENT);
        long indexOffset = -1;
        boolean completed = false;
        try {
            if (vertexOffset < 0) {
                throw new IllegalStateException(
                        "region " + regionKey + " vertex capacity exhausted (" + buffers.vertexCapacity + "B)");
            }
            indexOffset = handle.indexAllocator().allocate(indexBytes, ALLOC_ALIGNMENT);
            if (indexOffset < 0) {
                throw new IllegalStateException(
                        "region " + regionKey + " index capacity exhausted (" + buffers.indexCapacity + "B)");
            }
            buffers.upload(buffers.vertexBuffer, vertexOffset, vertexData);
            buffers.upload(buffers.indexBuffer, indexOffset, indexData);
            completed = true;
            return new LuminSectionAllocation(vertexOffset, vertexBytes, indexOffset, indexBytes);
        } finally {
            if (!completed) {
                if (indexOffset >= 0) {
                    handle.indexAllocator().free(indexOffset, indexBytes);
                }
                if (vertexOffset >= 0) {
                    handle.vertexAllocator().free(vertexOffset, vertexBytes);
                }
            }
        }
    }

    /** 归还 section 槽位（region 须存活且未退役；未知 key 静默忽略）。 */
    public void free(long regionKey, LuminSectionAllocation allocation) {
        LuminChunkStoreLedger.RegionHandle handle = ledger.region(regionKey).orElse(null);
        if (handle == null || handle.isRetired()) {
            return;
        }
        handle.vertexAllocator().free(allocation.vertexOffset(), allocation.vertexBytes());
        handle.indexAllocator().free(allocation.indexOffset(), allocation.indexBytes());
    }

    public GpuBufferSlice vertexSlice(long regionKey) {
        return region(regionKey).vertexBuffer.slice();
    }

    public GpuBuffer indexBuffer(long regionKey) {
        return region(regionKey).indexBuffer;
    }

    /** 销毁 region（GPU 缓冲 + 槽位账本）；幂等。 */
    public void retire(long regionKey) {
        RegionBuffers buffers = regions.remove(regionKey);
        if (buffers != null) {
            buffers.close();
        }
        ledger.retire(regionKey);
    }

    @Override
    public void close() {
        regions.values().forEach(RegionBuffers::close);
        regions.clear();
        ledger.close();
    }
}
