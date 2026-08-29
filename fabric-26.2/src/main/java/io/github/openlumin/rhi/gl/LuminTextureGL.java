package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.openlumin.rhi.LuminFormat;
import io.github.openlumin.rhi.LuminTexture;
import io.github.openlumin.rhi.LuminTextureView;

import java.nio.ByteBuffer;

/**
 * LuminTexture GL 后端实现 — 包装 GpuTexture
 *
 * 字节布局（参 docs/RHI_DESIGN.md §3 资源字节序）：
 *   - 业务层传 ByteBuffer；后端按 LuminFormat.format 决定每像素字节数与通道顺序。
 *   - RGBA8_UNORM：每像素 4 字节，业务层字节序 [R][G][B][A]（与 LuminColor 一致）。
 *   - 与 GpuBuffer 不同：纹理像素字节序就是属性顺序，无 ARGB-to-ABGR 调序（GPU 直读）。
 *   - LuminColor → texture 字节流：ARGB int → [R][G][B][A]，由 RgbHelper.argbToRgbaByteStream()。
 */
public final class LuminTextureGL implements LuminTexture {

    public final GpuTexture gtex;
    private final int w, h, d;
    private final LuminFormat format;

    public LuminTextureGL(GpuTexture gtex, int w, int h, int d, LuminFormat format) {
        this.gtex = gtex;
        this.w = w;
        this.h = h;
        this.d = d;
        this.format = format;
    }

    @Override public int width() { return w; }
    @Override public int height() { return h; }
    @Override public int depth() { return d; }
    @Override public LuminFormat format() { return format; }
    @Override public LuminTextureView view() { return new LuminTextureViewGL(this); }
    @Override public void close() { gtex.close(); }

    // -- B2: 完整上传支持（Alpha 2.1 规范要求） -----------------------

    /**
     * 上传 ARGB int 像素数据（ARGB 颜色数组）到纹理。
     * 字节序：业务层 [A, R, G, B]（int 0xAARRGGBB）→ 内部转 [R, G, B, A] 写入纹理。
     * MC 26.2 GpuTexture 在 client jar 中无 write 方法 → 必须经 CommandEncoder.writeToTexture。
     */
    public void writeArgbPixels(int[] argbPixels) {
        if (argbPixels.length != w * h) {
            throw new IllegalArgumentException("argbPixels length " + argbPixels.length + " != w*h " + (w * h));
        }
        byte[] rgba = argbToRgbaByteStream(argbPixels);
        ByteBuffer buf = ByteBuffer.wrap(rgba);
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        try {
            enc.writeToTexture(gtex, buf, /*mip*/ 0, /*layer*/ 0,
                    /*x*/ 0, /*y*/ 0, /*w*/ w, /*h*/ h);
            enc.submit();
        } finally {
            // 26.2 client jar：CommandEncoder 未实现 AutoCloseable；submit() 后释放
        }
    }

    /**
     * 上传整块字节（已按 format 字节布局组织好）。
     * @param pixels 完整像素缓冲（长度 = w * h * bytesPerPixel(format)）
     */
    public void writeRawBytes(ByteBuffer pixels) {
        int expected = w * h * bytesPerPixel(format);
        if (pixels.remaining() < expected) {
            throw new IllegalArgumentException("pixels.remaining " + pixels.remaining() + " < expected " + expected);
        }
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        try {
            enc.writeToTexture(gtex, pixels, /*mip*/ 0, /*layer*/ 0,
                    /*x*/ 0, /*y*/ 0, /*w*/ w, /*h*/ h);
            enc.submit();
        } finally {
        }
    }

    /**
     * 上传到指定 mip 层级（mipmap 链的更新路径）。
     */
    public void writeRawBytesMip(ByteBuffer pixels, int mipLevel) {
        int mipW = Math.max(1, w >> mipLevel);
        int mipH = Math.max(1, h >> mipLevel);
        int expected = mipW * mipH * bytesPerPixel(format);
        if (pixels.remaining() < expected) {
            throw new IllegalArgumentException("pixels.remaining " + pixels.remaining() + " < expected " + expected);
        }
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        try {
            enc.writeToTexture(gtex, pixels, mipLevel, /*layer*/ 0,
                    /*x*/ 0, /*y*/ 0, mipW, mipH);
            enc.submit();
        } finally {
        }
    }

    // -- 静态助手（用于 RgbHelper 内部） -----------------------

    /**
     * ARGB int 数组 → 字节流（每个 int 4 字节，内存布局 [R, G, B, A]）。
     * 注：MC GpuFormat.RGBA8_UNORM 的 GPU 端属性读取按 [R][G][B][A] 内存序。
     * 业务层 Color（java.awt.Color）的 getRGB() 返回 ARGB int 0xAARRGGBB，需按位提取。
     */
    public static byte[] argbToRgbaByteStream(int[] argbPixels) {
        byte[] out = new byte[argbPixels.length * 4];
        for (int i = 0; i < argbPixels.length; i++) {
            int c = argbPixels[i];
            out[i * 4]     = (byte) ((c >> 16) & 0xFF); // R
            out[i * 4 + 1] = (byte) ((c >> 8)  & 0xFF); // G
            out[i * 4 + 2] = (byte) (c         & 0xFF); // B
            out[i * 4 + 3] = (byte) ((c >> 24) & 0xFF); // A
        }
        return out;
    }

    public static int bytesPerPixel(LuminFormat f) {
        return switch (f) {
            case R8G8B8A8_UNORM, R8G8B8A8_SRGB -> 4;
            case R16G16B16A16_FLOAT -> 8;
            case R32G32B32A32_FLOAT -> 16;
            case R11G11B10_FLOAT -> 4;
            case A8_UNORM -> 1;
            case D32_FLOAT -> 4;
            case D24_UNORM_S8_UINT -> 4;
            case D32_FLOAT_S8X24_UINT -> 8;
            case BC1_UNORM, BC3_UNORM, BC5_UNORM, BC7_UNORM -> throw new UnsupportedOperationException("BCn requires vendor extension; not in MC 26.2");
        };
    }
}
