package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import io.github.openlumin.rhi.LuminBuffer;
import io.github.openlumin.rhi.LuminBufferView;

/** LuminBuffer → 包装 GpuBuffer */
public final class LuminBufferGL implements LuminBuffer {
    public final GpuBuffer gbuf;
    private final long size;
    public LuminBufferGL(GpuBuffer gbuf, long size) { this.gbuf = gbuf; this.size = size; }
    @Override public long size() { return size; }
    @Override public LuminBufferView view(long offset, long length) { return new LuminBufferView(this, offset, length); }
    @Override public void close() { gbuf.close(); }
}
