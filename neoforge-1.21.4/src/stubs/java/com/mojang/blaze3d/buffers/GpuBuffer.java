package com.mojang.blaze3d.buffers;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Stub: com.mojang.blaze3d.buffers.GpuBuffer（Fabric/Vanilla 包路径）
 * neoforge-1.21.4 运行时由 NeoForge 提供真实实现。
 */
public class GpuBuffer implements AutoCloseable {

    public static final int USAGE_MAP_WRITE = 0x01;
    public static final int USAGE_COPY_DST  = 0x02;
    public static final int USAGE_COPY_SRC  = 0x04;
    public static final int USAGE_UNIFORM   = 0x08;
    public static final int USAGE_VERTEX    = 0x10;
    public static final int USAGE_INDEX     = 0x20;

    @Retention(RetentionPolicy.SOURCE)
    public @interface Usage {}

    public GpuBuffer(Supplier<String> label, int usage, int size) {}
    public GpuBuffer(int size, int usage) {}
    public GpuBuffer(long size, int usage) {}

    public long size() { return 0; }

    public GpuBuffer slice(int offset, int size) { return this; }

    public void write(int offset, ByteBuffer data) {}

    @Override
    public void close() {}

    public static class MappedView implements AutoCloseable {
        public MappedView() {}
        public MappedView(long offset, long size) {}

        public ByteBuffer data() { return ByteBuffer.allocate(0); }
        public void putFloat(int index, float value) {}
        public void putInt(int index, int value) {}
        public void put(int index, byte[] data) {}

        @Override
        public void close() {}
    }
}
