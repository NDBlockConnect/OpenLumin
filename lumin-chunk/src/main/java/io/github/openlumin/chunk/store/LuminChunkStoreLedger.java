package io.github.openlumin.chunk.store;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 区块存储账本：regionKey → 顶点/索引槽位分配器 + 生命周期（acquire/retire）。
 * <p>生命周期语义：</p>
 * <ul>
 *   <li>{@link #acquire} 幂等：同 key 已存在未退役 handle 时原样返回；</li>
 *   <li>{@link #retire} 幂等：标记退役，之后该 handle 的分配器拒绝新分配
 *       （已分配槽位数据仍有效，供 GPU 层决定缓冲销毁时机）；</li>
 *   <li>retire 后再 {@link #acquire} 同 key = 重建：替换为全新 handle（旧 handle 失效）。</li>
 * </ul>
 * <p>GPU 缓冲的创建/销毁不在账本层（零 GPU 依赖）；retire 到实际释放的时机由 GPU 层
 * （帧末安全点）决定。非线程安全：渲染线程专用。</p>
 */
public final class LuminChunkStoreLedger implements AutoCloseable {

    public static final class RegionHandle {
        private final LuminRegionAllocator vertexAllocator;
        private final LuminRegionAllocator indexAllocator;
        private boolean retired;

        RegionHandle(LuminRegionAllocator vertexAllocator, LuminRegionAllocator indexAllocator) {
            this.vertexAllocator = vertexAllocator;
            this.indexAllocator = indexAllocator;
        }

        public LuminRegionAllocator vertexAllocator() {
            return allocator(vertexAllocator);
        }

        public LuminRegionAllocator indexAllocator() {
            return allocator(indexAllocator);
        }

        public boolean isRetired() {
            return retired;
        }

        private LuminRegionAllocator allocator(LuminRegionAllocator allocator) {
            if (retired) {
                throw new IllegalStateException("region handle is retired");
            }
            return allocator;
        }

        void retire() {
            this.retired = true;
        }
    }

    private final Map<Long, RegionHandle> regions = new HashMap<>();

    /**
     * 幂等获取（不存在则创建）。同 key 已存在但已退役时，替换为全新 handle 返回。
     */
    public RegionHandle acquire(long regionKey, long vertexCapacity, long indexCapacity) {
        RegionHandle existing = regions.get(regionKey);
        if (existing != null && !existing.isRetired()) {
            return existing;
        }
        RegionHandle created = new RegionHandle(
                new LuminRegionAllocator(vertexCapacity), new LuminRegionAllocator(indexCapacity));
        regions.put(regionKey, created);
        return created;
    }

    public Optional<RegionHandle> region(long regionKey) {
        return Optional.ofNullable(regions.get(regionKey));
    }

    /** 标记退役；幂等，未知 key 静默忽略。 */
    public void retire(long regionKey) {
        RegionHandle handle = regions.get(regionKey);
        if (handle != null) {
            handle.retire();
        }
    }

    public int activeCount() {
        return (int) regions.values().stream().filter(h -> !h.isRetired()).count();
    }

    @Override
    public void close() {
        regions.clear();
    }
}
