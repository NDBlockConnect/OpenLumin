package io.github.openlumin.chunk.store;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 共享 Arena 分配器（原理参照：跨持有者共享大缓冲 + best-fit 子分配 +
 * **增量碎片整理**；零 GPU 依赖，账本层）。
 *
 * <p>与 {@link LuminRegionAllocator}（单 region 独占、first-fit）的差异：</p>
 * <ul>
 *   <li>空闲块按<b>尺寸</b>索引（TreeMap&lt;size, set of offsets&gt;），best-fit 查询 O(log n)；</li>
 *   <li>支持<b>增量碎片整理</b>：每帧按预算（拷贝次数 + 字节数）把已用段向最大空闲块
 *       方向搬移合并，预算耗尽即停（剩余工作延后帧）；</li>
 *   <li>整理为可选操作（{@link #defragment} 返回本次实际搬移字节数），空闲占比过低时跳过。</li>
 * </ul>
 *
 * <p>搬移语义：账本只记录"旧偏移 → 新偏移"的映射请求，数据搬运由持有方执行
 * （GPU 侧 = copyToBuffer；测试中模拟）。段句柄 {@link Segment} 在搬移后保持有效，
 * 其 offset 字段被更新。</p>
 */
public final class LuminArenaAllocator {

    /** 已分配段（句柄；碎片整理后 offset 原地更新）。 */
    public static final class Segment {
        long offset;
        long size;
        boolean freed;

        Segment(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        public long offset() {
            return offset;
        }

        public long size() {
            return size;
        }
    }

    /** 一次整理步：段句柄 + 目标偏移（持有方按此把数据从旧 offset 搬到 newOffset）。 */
    public record Move(Segment segment, long newOffset) {
    }

    public static final int MIN_FREE_FRACTION_PERMILLE = 30;
    public static final int MAX_DEFRAG_STEPS = 5;

    private final long capacity;
    private final TreeMap<Long, List<Segment>> freeBySize = new TreeMap<>();
    private final List<Segment> usedSegments = new ArrayList<>();
    private long used;

    public LuminArenaAllocator(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
        addFree(new Segment(0, capacity));
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

    public int usedSegmentCount() {
        return usedSegments.size();
    }

    /**
     * best-fit 分配。
     *
     * @return 段句柄；无足够连续空间返回 null（碎片整理可续命，重建由持有方决策）
     */
    public Segment allocate(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got " + size);
        }
        var entry = freeBySize.ceilingEntry(size);
        if (entry == null) {
            return null;
        }
        Segment freeBlock = entry.getValue().remove(entry.getValue().size() - 1);
        if (entry.getValue().isEmpty()) {
            freeBySize.remove(entry.getKey());
        }
        Segment segment = new Segment(freeBlock.offset, size);
        long tail = freeBlock.size - size;
        if (tail > 0) {
            addFree(new Segment(freeBlock.offset + size, tail));
        }
        usedSegments.add(segment);
        used += size;
        return segment;
    }

    /** 释放段（句柄失效；相邻空闲块自动合并）。 */
    public void free(Segment segment) {
        if (segment.freed) {
            throw new IllegalStateException("segment already freed");
        }
        if (!usedSegments.remove(segment)) {
            throw new IllegalStateException("segment not owned by this arena");
        }
        segment.freed = true;
        used -= segment.size;
        addFreeMerging(segment.offset, segment.size);
    }

    /**
     * 插入空闲块并与前后紧邻的空闲块合并（避免重复条目与碎片）。
     */
    private void addFreeMerging(long offset, long size) {
        if (size <= 0) {
            return;
        }
        long start = offset;
        long length = size;
        Segment predecessor = findFreeEndingAt(start);
        if (predecessor != null) {
            removeFreeBlock(predecessor);
            start = predecessor.offset;
            length += predecessor.size;
        }
        Segment successor = findFreeStartingAt(start + length);
        if (successor != null) {
            removeFreeBlock(successor);
            length += successor.size;
        }
        addFree(new Segment(start, length));
    }

    /** 查找结束于给定地址的空闲块。 */
    private Segment findFreeEndingAt(long offset) {
        for (var entry : freeBySize.entrySet()) {
            for (Segment block : entry.getValue()) {
                if (block.offset + block.size == offset) {
                    return block;
                }
            }
        }
        return null;
    }

    /** 查找起始于给定地址的空闲块。 */
    private Segment findFreeStartingAt(long offset) {
        for (var entry : freeBySize.entrySet()) {
            for (Segment block : entry.getValue()) {
                if (block.offset == offset) {
                    return block;
                }
            }
        }
        return null;
    }

    private void removeFreeBlock(Segment block) {
        var entry = freeBySize.get(block.size);
        if (entry != null) {
            entry.remove(block);
            if (entry.isEmpty()) {
                freeBySize.remove(block.size);
            }
        }
    }

    /**
     * 增量碎片整理：把已用段向<b>紧邻其下方的空闲块</b>搬移，逐个消除碎片空洞。
     *
     * @param maxCopies 本次最多搬移的段数（每段最多 {@value #MAX_DEFRAG_STEPS} 次步进）
     * @param maxBytes  本次最多搬移的字节数
     * @return 搬移指令列表（段句柄 offset 已更新；持有方据此搬运数据）
     */
    public List<Move> defragment(int maxCopies, long maxBytes) {
        List<Move> moves = new ArrayList<>();
        if (maxCopies <= 0 || maxBytes <= 0) {
            return moves;
        }
        if (freeBytes() * 1000L / capacity < MIN_FREE_FRACTION_PERMILLE) {
            return moves;
        }
        long movedBytes = 0;
        // 按偏移升序考察：优先把低地址的段压实（填补更靠前的洞）
        List<Segment> ordered = new ArrayList<>(usedSegments);
        ordered.sort((a, b) -> Long.compare(a.offset, b.offset));
        for (Segment segment : ordered) {
            if (moves.size() >= maxCopies || movedBytes >= maxBytes) {
                break;
            }
            for (int step = 0; step < MAX_DEFRAG_STEPS; step++) {
                if (movedBytes >= maxBytes) {
                    break;
                }
                Segment below = findFreeEndingAt(segment.offset);
                if (below == null || below.offset >= segment.offset) {
                    break;
                }
                long shift = Math.min(below.size, maxBytes - movedBytes);
                if (shift <= 0) {
                    break;
                }
                // 把段下移 shift：下方空闲块缩小，段新位置之上让出 shift 字节空闲
                // 注意：让出区间起点是 newOffset + size（段的新顶），不是旧顶
                final long newOffset = segment.offset - shift;
                removeFreeBlock(below);
                long remainingBelow = below.size - shift;
                if (remainingBelow > 0) {
                    addFreeMerging(below.offset, remainingBelow);
                }
                addFreeMerging(newOffset + segment.size, shift);
                segment.offset = newOffset;
                movedBytes += shift;
                moves.add(new Move(segment, newOffset));
            }
        }
        return moves;
    }

    private void addFree(Segment block) {
        freeBySize.computeIfAbsent(block.size, k -> new ArrayList<>()).add(block);
    }
}
