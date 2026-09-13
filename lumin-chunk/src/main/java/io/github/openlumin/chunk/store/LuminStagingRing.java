package io.github.openlumin.chunk.store;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Staging 环账本（原理参照：持久映射 staging 环 + fence 区间回收；账本层零 GPU 依赖）。
 *
 * <p>语义：写游标环形回绕（跨尾的分配<b>拆两段</b>，各返回独立区间）；
 * {@link #submit} 把 [游标，分配尾) 标记为"在途"并推进游标；持有方在 GPU fence 信号后调用
 * {@link #reclaim}（以提交序号为单位）回收空间。真实 fence 由基线 GPU 层提供——
 * 此处用单调递增提交序号模拟其时序，语义与 Sodium 的 FencedMemoryRegion 相同。</p>
 *
 * <p>上传字节上限（容量 × {@value #UPLOAD_LIMIT_FRACTION}）由
 * {@link #uploadByteLimit()} 提供，供帧预算约束。</p>
 */
public final class LuminStagingRing {

    public static final double UPLOAD_LIMIT_FRACTION = 0.8;

    /** 一次 staging 分配（可为一或两段连续区间，跨尾时为二）。 */
    public static final class Allocation {
        final long[] offsets;
        final long[] lengths;
        final long submitId;

        Allocation(long[] offsets, long[] lengths, long submitId) {
            this.offsets = offsets;
            this.lengths = lengths;
            this.submitId = submitId;
        }

        public int segmentCount() {
            return offsets.length;
        }

        public long offset(int index) {
            return offsets[index];
        }

        public long length(int index) {
            return lengths[index];
        }

        public long submitId() {
            return submitId;
        }

        public long totalBytes() {
            long total = 0;
            for (long length : lengths) {
                total += length;
            }
            return total;
        }
    }

    private final long capacity;
    private final Deque<PendingRange> pending = new ArrayDeque<>();
    private long writeCursor;
    private long inFlightBytes;
    private long submitCounter;

    private static final class PendingRange {
        final long submitId;
        final long bytes;

        PendingRange(long submitId, long bytes) {
            this.submitId = submitId;
            this.bytes = bytes;
        }
    }

    public LuminStagingRing(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
    }

    public long capacity() {
        return capacity;
    }

    public long uploadByteLimit() {
        return (long) (capacity * UPLOAD_LIMIT_FRACTION);
    }

    public long inFlightBytes() {
        return inFlightBytes;
    }

    /**
     * 分配 staging 区间（跨尾自动拆两段；空间不足返回 null——持有方等待 fence 回收）。
     * <p>注意：{@link #uploadByteLimit()} 是给帧预算用的<b>建议值</b>，本方法只受
     * 物理容量约束（进行中总量 ≤ 容量）——单笔大上传不应被预算建议卡死。</p>
     */
    public Allocation allocate(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("bytes must be > 0, got " + bytes);
        }
        if (bytes > capacity) {
            throw new IllegalArgumentException("allocation exceeds staging capacity: " + bytes + " > " + capacity);
        }
        if (inFlightBytes + bytes > capacity) {
            return null;
        }
        long first = Math.min(bytes, capacity - writeCursor);
        long[] offsets;
        long[] lengths;
        if (first == bytes) {
            offsets = new long[]{writeCursor};
            lengths = new long[]{bytes};
        } else {
            long second = bytes - first;
            if (second > writeCursor) {
                return null;
            }
            offsets = new long[]{writeCursor, 0};
            lengths = new long[]{first, second};
        }
        long submitId = ++submitCounter;
        pending.addLast(new PendingRange(submitId, bytes));
        inFlightBytes += bytes;
        writeCursor = (writeCursor + bytes) % capacity;
        return new Allocation(offsets, lengths, submitId);
    }

    /**
     * 回收所有提交序号 ≤ {@code completedSubmitId} 的在途区间（原理：fence 信号后批量回收）。
     *
     * @return 本次回收的字节数
     */
    public long reclaim(long completedSubmitId) {
        long reclaimed = 0;
        while (!pending.isEmpty() && pending.peekFirst().submitId <= completedSubmitId) {
            reclaimed += pending.removeFirst().bytes;
        }
        inFlightBytes -= reclaimed;
        return reclaimed;
    }
}
