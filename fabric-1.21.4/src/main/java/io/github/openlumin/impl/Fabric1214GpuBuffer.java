package io.github.openlumin.impl;

import io.github.openlumin.api.GpuBufferApi;
import io.github.openlumin.api.GpuBufferHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import java.nio.ByteBuffer;

/**
 * Fabric 1.21.4 GPU 缓冲实现。
 * <p>
 * 使用原始 OpenGL 缓冲 API（LWJGL GL15）。
 * 后续可迁移到 Mojang 的 GpuDevice 抽象。
 */
public class Fabric1214GpuBuffer implements GpuBufferApi {

    @Override
    public GpuBufferHandle createBuffer(long sizeBytes, BufferUsage usage) {
        RenderSystem.assertOnRenderThread();
        int id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, sizeBytes, toGlUsage(usage));
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
        RenderSystem.assertOnRenderThread();
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

    private static int toGlUsage(BufferUsage usage) {
        return switch (usage) {
            case STATIC_DRAW -> GL15.GL_STATIC_DRAW;
            case DYNAMIC_DRAW -> GL15.GL_DYNAMIC_DRAW;
            case STREAM_DRAW -> GL15.GL_STREAM_DRAW;
        };
    }
}
