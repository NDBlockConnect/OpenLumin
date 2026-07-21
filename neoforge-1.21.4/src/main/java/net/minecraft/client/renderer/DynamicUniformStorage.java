package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * NeoForge适配层：模拟Fabric的DynamicUniformStorage API
 */
public class DynamicUniformStorage<T extends DynamicUniformStorage.DynamicUniform> {

    private final String label;
    private final int uniformSize;
    private final int capacity;
    private GpuBuffer buffer;

    public DynamicUniformStorage(String label, int uniformSize, int initialCapacity) {
        this.label = label;
        this.uniformSize = uniformSize;
        this.capacity = initialCapacity;
        this.buffer = new GpuBuffer((long) uniformSize * initialCapacity, GpuBuffer.USAGE_UNIFORM);
    }

    public GpuBufferSlice write(T uniform) {
        // NeoForge使用不同的API
        return new GpuBufferSlice(buffer, 0, uniformSize);
    }

    public GpuBufferSlice writeUniform(T uniform) {
        return write(uniform);
    }

    public void endFrame() {
        // NeoForge使用不同的API
    }

    public void close() {
        if (buffer != null) {
            buffer.close();
        }
    }

    public interface DynamicUniform {
        void write(java.nio.ByteBuffer buffer);
    }
}
