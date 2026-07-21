package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * NeoForge适配层：模拟Fabric的ProjectionMatrixBuffer API
 */
public class ProjectionMatrixBuffer {

    private final String name;

    public ProjectionMatrixBuffer(String name) {
        this.name = name;
    }

    public GpuBufferSlice getBuffer(Projection projection) {
        // NeoForge使用不同的API
        return new GpuBufferSlice(null, 0, 0);
    }

    public void close() {
        // NeoForge使用不同的API
    }
}
