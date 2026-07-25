package io.github.openlumin.shim.com.mojang.blaze3d.systems;

import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * NeoForge适配层：模拟Fabric的RenderSystem.AutoStorageIndexBuffer API
 */
public class AutoStorageIndexBuffer {

    private final VertexFormat.Mode mode;

    public AutoStorageIndexBuffer(VertexFormat.Mode mode) {
        this.mode = mode;
    }

    public GpuBuffer getBuffer(int indexCount) {
        // NeoForge使用不同的API
        return new GpuBuffer(indexCount * 4, GpuBuffer.USAGE_INDEX);
    }

    public VertexFormat.IndexType type() {
        // NeoForge使用不同的API
        return VertexFormat.IndexType.INT;
    }
}
