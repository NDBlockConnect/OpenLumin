package io.github.openlumin.chunk.store;

import java.util.Map;
import java.util.TreeMap;

/**
 * 单地址空间 first-fit 偏移分配器（region 内 vertex 或 index 缓冲的槽位账本）。
 * <p>不负责容量增长：{@link #allocate} 空间不足返回 -1，扩容（重建 GPU 缓冲 + 重放数据）
 * 由持有方（LuminChunkStore 的消费方）决策——账本层保持纯 CPU、零 GPU 语义。</p>
 * <p>非线程安全：调用方约束在渲染线程（与 GPU 缓冲操作同线程）。</p>
 */
public final class LuminRegionAllocator {

    private final long capacity;
    private final TreeMap<Long, Long> freeBlocks = new TreeMap<>();
    private long used;

    public LuminRegionAllocator(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
        this.freeBlocks.put(0L, capacity);
    }

    public long capacity() {
        return capacity;
    }

    public long usedBytes() {
        return used;
    }

    public long freeBytes() {
        return capacity - used;
    }

    /**
     * first-fit 分配。
     *
     * @param size      请求字节数（>0）
     * @param alignment 对齐要求（2 的幂，>0）
     * @return 起始偏移；无足够连续空间返回 -1
     */
    public long allocate(long size, long alignment) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got " + size);
        }
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("alignment must be a power of two, got " + alignment);
        }
        var iterator = freeBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> block = iterator.next();
            long start = alignUp(block.getKey(), alignment);
            long padding = start - block.getKey();
            if (padding + size > block.getValue()) {
                continue;
            }
            iterator.remove();
            if (padding > 0) {
                freeBlocks.put(block.getKey(), padding);
            }
            long tail = block.getValue() - padding - size;
            if (tail > 0) {
                freeBlocks.put(start + size, tail);
            }
            used += size;
            return start;
        }
        return -1;
    }

    /**
     * 释放区间并合并相邻空闲块（前驱/后继）。重复释放同一区间属于调用方错误，
     * 会破坏账本一致性（库内部使用，不做 O(n) 已分配校验）。
     */
    public void free(long offset, long size) {
        if (offset < 0 || size <= 0 || offset > capacity - size) {
            throw new IllegalArgumentException(
                    "range [" + offset + ", " + (offset + size) + ") out of capacity " + capacity);
        }
        used -= size;
        Long successor = freeBlocks.get(offset + size);
        if (successor != null) {
            freeBlocks.remove(offset + size);
            size += successor;
        }
        Map.Entry<Long, Long> predecessor = freeBlocks.floorEntry(offset);
        if (predecessor != null && predecessor.getKey() + predecessor.getValue() == offset) {
            freeBlocks.remove(predecessor.getKey());
            offset = predecessor.getKey();
            size += predecessor.getValue();
        }
        freeBlocks.put(offset, size);
    }

    private static long alignUp(long value, long alignment) {
        return (value + alignment - 1) & -alignment;
    }
}
