package io.github.openlumin.impl;

import io.github.openlumin.api.GpuBufferApi;
import io.github.openlumin.api.GpuBufferHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import java.nio.ByteBuffer;

/**
 * NeoForge 1.21.4 GPU 缓冲实现
 */
public class NeoForge1214GpuBuffer implements GpuBufferApi {

    @Override
    public GpuBufferHandle createBuffer(long sizeBytes, BufferUsage usage) {
        int id = GL15.glGenBuffers();
        return new GpuBufferHandle(id);
    }

    @Override
    public ByteBuffer mapBuffer(GpuBufferHandle handle, long offset, long length) {
        RenderSystem.assertOnRenderThread();
        int id = (Integer) handle.nativeHandle();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        return GL15.glMapBuffer(GL15.GL_ARRAY_BUFFER, GL15.GL_WRITE_ONLY);
    }

    @Override
    public void unmapBuffer(GpuBufferHandle handle) {
        GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void bindBuffer(GpuBufferHandle handle, int bindingPoint) {
        int id = (Integer) handle.nativeHandle();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
    }

    @Override
    public void deleteBuffer(GpuBufferHandle handle) {
        RenderSystem.assertOnRenderThread();
        int id = (Integer) handle.nativeHandle();
        GL15.glDeleteBuffers(id);
    }
}
