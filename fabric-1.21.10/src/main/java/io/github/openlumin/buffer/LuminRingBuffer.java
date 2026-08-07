package io.github.openlumin.buffer;

import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import io.github.openlumin.compat.RenderSystemShim;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * fabric-1.21.10 override: 移除 @GpuBuffer.Usage 注解（1.21.10 已删除该注解类型）。
 */
public class LuminRingBuffer {

    private static final int BUFFER_COUNT = 3;

    private final int usage;
    private final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];
    private final int[] sizes = new int[BUFFER_COUNT];
    private final List<GpuBuffer> retiredBuffers = new ArrayList<>();

    private GpuBuffer.MappedView mappedBuffer;
    private int current;
    private boolean mapped;
    private long frameId = Long.MIN_VALUE;

    /**
     * @param size  初始字节容量
     * @param usage GPU 使用标记（plain int，1.21.10 已移除 @GpuBuffer.Usage 注解）
     */
    public LuminRingBuffer(long size, int usage) {
        int initialSize = checkedBufferSize(size);
        this.usage = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC | usage;
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = createBuffer(i, initialSize);
            sizes[i] = initialSize;
        }
    }

    public int size() { return sizes[current]; }

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
        mappedBuffer = RenderSystemShim.getDevice().createCommandEncoder().mapBuffer(getGpuBuffer(), false, true);
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
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(getGpuBuffer().slice((int) offset, source.remaining()), false, true)) {
            MemoryUtil.memCopy(source, mappedView.data());
        }
    }

    public void close() {
        if (mapped) unmap();
        for (GpuBuffer buffer : buffers) buffer.close();
        closeRetiredBuffers();
    }

    private void resizeCurrent(int nextSize) {
        int oldSize = sizes[current];
        if (nextSize <= oldSize) return;

        ByteBuffer preservedMappedData = null;
        int preservedBytes = Math.min(oldSize, nextSize);
        if (mapped && preservedBytes > 0) {
            ByteBuffer source = mappedBuffer.data();
            preservedMappedData = MemoryUtil.memAlloc(preservedBytes);
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
            RenderSystemShim.getDevice()
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

    private static int checkedBufferSize(long size) {
        int checkedSize = Math.toIntExact(size);
        if (checkedSize <= 0) throw new IllegalArgumentException("size must be positive");
        return checkedSize;
    }

    private static int growSize(int currentSize, long requiredBytes) {
        int requiredSize = checkedBufferSize(requiredBytes);
        int nextSize = currentSize;
        while (requiredSize > nextSize) nextSize = Math.multiplyExact(nextSize, 2);
        return nextSize;
    }

    private GpuBuffer createBuffer(int index, int size) {
        return RenderSystemShim.getDevice().createBuffer(() -> "lumin-ring-buffer #" + index, usage, size);
    }
}
