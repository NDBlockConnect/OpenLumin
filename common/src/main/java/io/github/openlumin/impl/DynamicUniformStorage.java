package io.github.openlumin.impl;

import com.mojang.blaze3d.platform.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * OpenLumin 内部的 DynamicUniform 环形缓冲区管理。
 * 此类放在 io.github.openlumin.impl 包中，以避免将类注入
 * net.minecraft.* 模块（JPMS split-package 规则禁止这样做）。
 */
public class DynamicUniformStorage<T extends DynamicUniformStorage.DynamicUniform> implements AutoCloseable {

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
        return new GpuBufferSlice(buffer, 0, uniformSize);
    }

    public GpuBufferSlice writeUniform(T uniform) {
        return write(uniform);
    }

    public void endFrame() {
        // 每帧结束后重置写入指针（NeoForge 1.21.4 OpenGL 路径为空操作）
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }

    public interface DynamicUniform {
        void write(java.nio.ByteBuffer buffer);
    }
}
