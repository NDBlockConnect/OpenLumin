package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import io.github.openlumin.rhi.LuminBuffer;
import io.github.openlumin.rhi.LuminBufferView;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * LuminBuffer GL 后端实现 — 包装 GpuBuffer
 *
 * 字节序调序（颜色）：ARGB int → ABGR 内存布局（参 26.2 putColor 修复实证）：
 *   - GpuBuffer 写入位置 = 字节序 ARGB int 通过 LITTLE_ENDIAN 直接写入后，
 *     内存为 [BB][GG][RR][AA]（低位字节在前）。
 *   - GPU 端按 RGBA8_UNORM 属性读取，内存序 [R][G][B][A]，
 *     即 [BB][GG][RR][AA] → R=BB 错位（红蓝交换）。
 *   - 修正：写入前 ARGB.toABGR 调序，输入 0xAARRGGBB → 内存序 [RR][GG][BB][AA] = RGBA 正确。
 */
public final class LuminBufferGL implements LuminBuffer {

    public final GpuBuffer gbuf;
    private final long size;

    public LuminBufferGL(GpuBuffer gbuf, long size) {
        this.gbuf = gbuf;
        this.size = size;
    }

    @Override
    public long size() { return size; }

    @Override
    public LuminBufferView view(long offset, long length) {
        return new LuminBufferView(this, offset, length);
    }

    @Override
    public void close() { gbuf.close(); }

    // -- B1: 完整上传支持（Alpha 2.1 规范要求） -----------------------

    /**
     * 上传 ARGB 颜色 int 到缓冲（GL 后端：自动 toABGR 调序写入 32 位定点数）。
     * 业务层传 Color.getRGB()（ARGB int）即可，字节序由后端处理。
     *
     * @param viewOffsetBytes 缓冲内字节偏移
     * @param argb            ARGB 颜色
     */
    public void writeColorInt(long viewOffsetBytes, int argb) {
        GpuBufferSlice.MappedView mapped = gbuf.slice(viewOffsetBytes, 4L).map(false, true);
        try (mapped) {
            // MappedView.data() 已是该子切片的 ByteBuffer；写入偏移 = 相对切片起点。
            // toABGR 调序：ARGB int (0xAARRGGBB) → ABGR 内存序 (0xAABBGGRR)。
            mapped.data().putInt(0, ARGB.toABGR(argb));
        }
    }

    /**
     * 上传 float 数据到缓冲（如变换矩阵 / 顶点坐标）。
     * @param viewOffsetBytes 写入起点（缓冲内字节偏移）
     * @param data            float 数组
     */
    public void writeFloats(long viewOffsetBytes, float[] data) {
        if (data.length == 0) return;
        long lengthBytes = (long) data.length * 4L;
        GpuBufferSlice.MappedView mapped = gbuf.slice(viewOffsetBytes, lengthBytes).map(false, true);
        try (mapped) {
            ByteBuffer buf = mapped.data();
            for (int i = 0; i < data.length; i++) {
                buf.putFloat(i * 4, data[i]);
            }
        }
    }

    /**
     * 上传索引数据到 INDEX 类型缓冲。
     */
    public void writeInts(long viewOffsetBytes, int[] data) {
        if (data.length == 0) return;
        long lengthBytes = (long) data.length * 4L;
        GpuBufferSlice.MappedView mapped = gbuf.slice(viewOffsetBytes, lengthBytes).map(false, true);
        try (mapped) {
            ByteBuffer buf = mapped.data();
            for (int i = 0; i < data.length; i++) {
                buf.putInt(i * 4, data[i]);
            }
        }
    }

    /**
     * 上传整块字节（用于混合数据：Position + Color 交错等）。
     */
    public void writeBytes(long viewOffsetBytes, ByteBuffer src) {
        int length = src.remaining();
        if (length == 0) return;
        GpuBufferSlice.MappedView mapped = gbuf.slice(viewOffsetBytes, length).map(false, true);
        try (mapped) {
            ByteBuffer dst = mapped.data();
            // 保留 src 当前位置：批量复制
            int oldLimit = src.limit();
            int oldPos = src.position();
            src.limit(oldPos + length).position(oldPos);
            dst.put(src);
            src.limit(oldLimit).position(oldPos);
        }
    }

    // -- 静态助手（用于 LuminRHICommands 内部） -----------------------

    /**
     * ARGB int → ABGR 内存序调序（参 docs/RHI_DESIGN.md §3 颜色字节序策略）。
     */
    public static int argbToAbgrMemoryOrder(int argb) {
        return ARGB.toABGR(argb);
    }

    /**
     * 在已映射的 ByteBuffer 中按 32 位定点写一个 int（业务层已做 ARGB → ABGR 调序时使用）。
     * 包内访问：LuminRHICommands 内部使用。
     */
    static void memPutIntAt(ByteBuffer buf, int relativeOffset, int value) {
        buf.putInt(relativeOffset, value);
    }

    /**
     * 把一段 ByteBuffer 数据按 LITTLE_ENDIAN 解释为连续的 32 位整型数组（MappedView.data() 直接写入用）。
     */
    static int[] bytesToIntArray(ByteBuffer src) {
        int n = src.remaining() / 4;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = src.getInt(src.position() + i * 4);
        }
        return out;
    }
}
