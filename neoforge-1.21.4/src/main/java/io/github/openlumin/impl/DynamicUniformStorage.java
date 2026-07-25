package io.github.openlumin.impl;

import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * OpenLumin 内部的 DynamicUniform 环形缓冲区管理。
 * 注意：本类在 io.github.openlumin.impl 包中，以避免 JPMS split-package 问题
 * （net.minecraft.* 包属于 MC 模块，mod jar 不能向其中注入类）。
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
        // no-op for NeoForge 1.21.4 OpenGL path
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
