package com.mojang.blaze3d.platform;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Compile-only stub：仅供 stubs sourceSet 内的其他文件（GpuBufferSlice、RenderPass 等）引用。
 * 运行时使用 main/java/com/mojang/blaze3d/platform/GpuBuffer.java（真实 OpenGL 实现）。
 */
public class GpuBuffer {

    public static final int USAGE_MAP_WRITE = 0x01;
    public static final int USAGE_COPY_DST  = 0x02;
    public static final int USAGE_COPY_SRC  = 0x04;
    public static final int USAGE_UNIFORM   = 0x08;
    public static final int USAGE_VERTEX    = 0x10;
    public static final int USAGE_INDEX     = 0x20;

    @Retention(RetentionPolicy.SOURCE)
    public @interface Usage {}

    private final int bufferId;
    private final long size;
    private final int usage;
    private boolean closed = false;

    public GpuBuffer(long size, int usage) {
        this.size = size;
        this.usage = usage;
        this.bufferId = GL15.glGenBuffers();
        int target = resolveTarget(usage);
        GL15.glBindBuffer(target, bufferId);
        GL15.glBufferData(target, size, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(target, 0);
    }

    public GpuBuffer(int size, int usage) {
        this((long) size, usage);
    }

    public long size() { return size; }

    public int getBufferId() { return bufferId; }

    public GpuBuffer slice(int offset, int size) { return this; }

    public void write(int offset, ByteBuffer data) {
        int target = resolveTarget(usage);
        GL15.glBindBuffer(target, bufferId);
        GL15.glBufferSubData(target, offset, data);
        GL15.glBindBuffer(target, 0);
    }

    public MappedView map(long offset, long size) {
        int target = resolveTarget(usage);
        GL15.glBindBuffer(target, bufferId);
        ByteBuffer mapped = GL30.glMapBufferRange(target, offset, size,
                GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT);
        return new MappedView(mapped, target);
    }

    public void close() {
        if (!closed) {
            GL15.glDeleteBuffers(bufferId);
            closed = true;
        }
    }

    static int resolveTarget(int usage) {
        if ((usage & USAGE_UNIFORM) != 0) return GL31.GL_UNIFORM_BUFFER;
        if ((usage & USAGE_INDEX)   != 0) return GL15.GL_ELEMENT_ARRAY_BUFFER;
        return GL15.GL_ARRAY_BUFFER;
    }

    public static class MappedView implements AutoCloseable {
        private ByteBuffer data;
        private final int glTarget;

        public MappedView(ByteBuffer data, int glTarget) {
            this.data = data;
            this.glTarget = glTarget;
        }

        public MappedView() { this.data = null; this.glTarget = GL15.GL_ARRAY_BUFFER; }
        public MappedView(long offset, long size) { this(); }

        public ByteBuffer data() { return data != null ? data : ByteBuffer.allocate(0); }
        public void putFloat(int index, float value) { if (data != null) data.putFloat(index, value); }
        public void putInt(int index, int value) { if (data != null) data.putInt(index, value); }
        public void put(int index, byte[] bytes) {
            if (data != null) for (int i = 0; i < bytes.length; i++) data.put(index + i, bytes[i]);
        }

        @Override
        public void close() {
            if (data != null) {
                GL30.glUnmapBuffer(glTarget);
                GL15.glBindBuffer(glTarget, 0);
                data = null;
            }
        }
    }
}
