package io.github.openlumin.buffer;

import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * fabric-26.1.2 适配：GPU 偏移统一为 long（26.1.2 的 GpuBuffer.slice 接收 long offset/length）。
 */
public class LuminRingBuffer {

    private static final int BUFFER_COUNT = 3;

    private final int usage;
    private final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];
    private final long[] sizes = new long[BUFFER_COUNT];
    private final List<GpuBuffer> retiredBuffers = new ArrayList<>();

    private GpuBuffer.MappedView mappedBuffer;
    private int current;
    private boolean mapped;
    private long frameId = Long.MIN_VALUE;

    /**
     * @param size  初始字节容量
     * @param usage GPU 使用标记（plain int）
     */
    public LuminRingBuffer(long size, int usage) {
        long initialSize = checkedBufferSize(size);
        this.usage = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC | usage;
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = createBuffer(i, initialSize);
            sizes[i] = initialSize;
        }
    }

    public long size() { return sizes[current]; }

    public boolean isMapped() { return mapped; }

    public ByteBuffer getMappedBuffer() {
        if (mappedBuffer == null) throw new IllegalStateException("LuminRingBuffer is not mapped");
        return mappedBuffer.data();
    }

    public void ensureCapacity(long requiredBytes) {
        if (requiredBytes <= size()) return;
        resizeCurrent(growSize(size(), requiredBytes));
    }

    public void tryMap() {
        if (mapped) return;
        beginFrameIfNeeded();
        mappedBuffer = RenderSystem.getDevice().createCommandEncoder().mapBuffer(getGpuBuffer(), false, true);
        mapped = true;
    }

    public void unmap() {
        if (!mapped) return;
        mappedBuffer.close();
        mappedBuffer = null;
        mapped = false;
    }

    public void rotate() {
        beginFrameIfNeeded();
        current = (current + 1) % buffers.length;
    }

    public GpuBuffer unmapAndRotate() {
        GpuBuffer lastGpuBuffer = getGpuBuffer();
        unmap();
        rotate();
        return lastGpuBuffer;
    }

    public GpuBuffer getGpuBuffer() { return buffers[current]; }

    public void write(CommandEncoder commandEncoder, long offset, ByteBuffer source) {
        ensureCapacity(offset + source.remaining());
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(getGpuBuffer().slice(offset, source.remaining()), false, true)) {
            MemoryUtil.memCopy(source, mappedView.data());
        }
    }

    public void close() {
        if (mapped) unmap();
        for (GpuBuffer buffer : buffers) buffer.close();
        closeRetiredBuffers();
    }

    private void resizeCurrent(long nextSize) {
        long oldSize = sizes[current];
        if (nextSize <= oldSize) return;

        ByteBuffer preservedMappedData = null;
        long preservedBytes = Math.min(oldSize, nextSize);
        if (mapped && preservedBytes > 0) {
            ByteBuffer source = mappedBuffer.data();
            preservedMappedData = MemoryUtil.memAlloc((int) preservedBytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(preservedMappedData), preservedBytes);
        }

        if (mapped) unmap();

        GpuBuffer oldBuffer = buffers[current];
        GpuBuffer nextBuffer = createBuffer(current, nextSize);
        buffers[current] = nextBuffer;
        sizes[current] = nextSize;

        if (preservedMappedData != null) {
            try {
                tryMap();
                MemoryUtil.memCopy(MemoryUtil.memAddress(preservedMappedData), MemoryUtil.memAddress(mappedBuffer.data()), preservedBytes);
            } finally {
                MemoryUtil.memFree(preservedMappedData);
            }
        } else if (preservedBytes > 0) {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .copyToBuffer(oldBuffer.slice(0, preservedBytes), nextBuffer.slice(0, preservedBytes));
        }

        retiredBuffers.add(oldBuffer);
    }

    private void beginFrameIfNeeded() {
        long currentFrameId = LuminRenderSystem.getRenderFrameId();
        if (frameId == currentFrameId) return;
        frameId = currentFrameId;
        closeRetiredBuffers();
    }

    private void closeRetiredBuffers() {
        if (retiredBuffers.isEmpty()) return;
        retiredBuffers.forEach(GpuBuffer::close);
        retiredBuffers.clear();
    }

    private static long checkedBufferSize(long size) {
        if (size <= 0 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("size must be positive and fit in an int-backed allocation");
        return size;
    }

    private static long growSize(long currentSize, long requiredBytes) {
        long requiredSize = checkedBufferSize(requiredBytes);
        long nextSize = currentSize;
        while (requiredSize > nextSize) nextSize = Math.multiplyExact(nextSize, 2);
        return nextSize;
    }

    private GpuBuffer createBuffer(int index, long size) {
        return RenderSystem.getDevice().createBuffer(() -> "lumin-ring-buffer #" + index, usage, size);
    }
}
