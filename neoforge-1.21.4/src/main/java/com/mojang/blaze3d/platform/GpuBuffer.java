package com.mojang.blaze3d.platform;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * NeoForge适配层：模拟Fabric的GpuBuffer API
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

    private final long size;
    private final int usage;

    public GpuBuffer(long size, int usage) {
        this.size = size;
        this.usage = usage;
    }

    public GpuBuffer(int size, int usage) {
        this.size = size;
        this.usage = usage;
    }

    public long size() {
        return size;
    }

    public GpuBuffer slice(int offset, int size) {
        return new GpuBuffer(size, usage);
    }

    public MappedView map(long offset, long size) {
        return new MappedView(offset, size);
    }

    public void unmap() {
        // NeoForge使用不同的API
    }

    public void close() {
        // NeoForge使用不同的API
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
            // NeoForge使用不同的API，返回空buffer
            return ByteBuffer.allocate(0);
        }

        public void putFloat(int index, float value) {
            // NeoForge使用不同的API
        }

        public void putInt(int index, int value) {
            // NeoForge使用不同的API
        }

        public void put(int index, byte[] data) {
            // NeoForge使用不同的API
        }

        @Override
        public void close() {
            // NeoForge使用不同的API
        }
    }
}
