package com.mojang.blaze3d.platform;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * NeoForge 实现：真正的 OpenGL Buffer Object
 */
public class GpuBuffer {

    public static final int USAGE_MAP_WRITE = 0x01;
    public static final int USAGE_COPY_DST = 0x02;
    public static final int USAGE_COPY_SRC = 0x04;
    public static final int USAGE_UNIFORM = 0x08;
    public static final int USAGE_VERTEX = 0x10;
    public static final int USAGE_INDEX = 0x20;

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

        // 确定 OpenGL buffer target
        int target = getGLTarget(usage);

        // 分配 buffer 存储
        GL15.glBindBuffer(target, bufferId);
        GL15.glBufferData(target, size, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(target, 0);
    }

    public GpuBuffer(int size, int usage) {
        this((long) size, usage);
    }

    public long size() {
        return size;
    }

    public GpuBuffer slice(int offset, int size) {
        return new GpuBuffer(size, usage);
    }

    /**
     * 写入数据到 buffer
     */
    public void write(int offset, ByteBuffer data) {
        if (closed) {
            throw new IllegalStateException("Buffer has been closed");
        }

        int target = getGLTarget(usage);
        GL15.glBindBuffer(target, bufferId);
        GL15.glBufferSubData(target, offset, data);
        GL15.glBindBuffer(target, 0);
    }

    /**
     * 获取 buffer ID
     */
    public int getBufferId() {
        return bufferId;
    }

    /**
     * 根据 usage 标志确定 OpenGL target
     */
    private int getGLTarget(int usage) {
        if ((usage & USAGE_UNIFORM) != 0) {
            return GL31.GL_UNIFORM_BUFFER;
        } else if ((usage & USAGE_INDEX) != 0) {
            return GL15.GL_ELEMENT_ARRAY_BUFFER;
        } else {
            return GL15.GL_ARRAY_BUFFER;
        }
    }

    public MappedView map(long offset, long size) {
        return new MappedView(offset, size);
    }

    public void unmap() {
        // 占位实现
    }

    public void close() {
        if (!closed) {
            GL15.glDeleteBuffers(bufferId);
            closed = true;
        }
    }

    public static class MappedView implements AutoCloseable {
        private final long offset;
        private final long size;

        public MappedView(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        public MappedView() {
            this(0, 0);
        }

        public ByteBuffer data() {
            // 占位实现，返回空buffer
            return ByteBuffer.allocate(0);
        }

        public void putFloat(int index, float value) {
            // 占位实现
        }

        public void putInt(int index, int value) {
            // 占位实现
        }

        public void put(int index, byte[] data) {
            // 占位实现
        }

        @Override
        public void close() {
            // 占位实现
        }
    }
}
