package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.openlumin.rhi.*;

/**
 * LuminRHI GL 后端——包装 Minecraft 26.2 的 GpuDevice。
 *
 * 资源持有：
 *  - LuminBufferGL 包装 GpuBuffer
 *  - LuminTextureGL 包装 GpuTexture
 *  - LuminSamplerGL 包装 GpuSampler
 *  - LuminShaderGL：路径占位
 *  - LuminPipelineGL：shader 路径 + 顶点格式 + 状态（懒绑定 MC RenderPipeline）
 *  - SwapchainImpl：适配 Minecraft.getMainRenderTarget()（主窗口 surface）
 *
 * 颜色字节序：上传到缓冲时调用 ARGB.toABGR 调序（参 26.2 putColor 修复）。
 */
public final class LuminRHI_GL implements LuminRHI {

    private final LuminRHIInfo info;
    private final DeviceImpl device = new DeviceImpl();
    private final SwapchainImpl swapchain = new SwapchainImpl();

    public LuminRHI_GL() {
        this.info = new LuminRHIInfo("OpenGL",
                RenderSystem.getDevice().getClass().getSimpleName(),
                16384, 65536,
                true, true, true, true, true, true, true, true);
    }

    @Override public LuminRHIInfo info() { return info; }
    @Override public LuminDevice device() { return device; }
    @Override public LuminSwapchain swapchain() { return swapchain; }
    @Override public LuminCommandEncoder createEncoder(String label) { return new LuminRHIEncoder.CommandEncoderImpl(label); }
    @Override
    public void submit(LuminCommandBuffer buffer) {
        if (!(buffer instanceof LuminRHICommands.CommandBufferImpl cb)) {
            throw new IllegalArgumentException("not a GL command buffer");
        }
        cb.submit();
    }
    @Override
    public void present(LuminSwapchainImage image, boolean vsync) {
        if (!(image instanceof SwapchainImageImpl img)) {
            throw new IllegalArgumentException("not a GL swapchain image");
        }
        img.presentInternal(vsync);
    }

    final class DeviceImpl implements LuminDevice {
        @Override
        public LuminBuffer createBuffer(LuminBufferUsage usage, long size) {
            int mcUsage = switch (usage) {
                case VERTEX -> GpuBuffer.USAGE_VERTEX;
                case INDEX -> GpuBuffer.USAGE_INDEX;
                case UNIFORM -> GpuBuffer.USAGE_UNIFORM;
                case STORAGE -> GpuBuffer.USAGE_HINT_CLIENT_STORAGE;
                case COPY_SRC, COPY_DST -> GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;
            };
            GpuBuffer gbuf = RenderSystem.getDevice().createBuffer(() -> "lumin", mcUsage, size);
            return new LuminBufferGL(gbuf, size);
        }
        @Override
        public LuminTexture createTexture2D(int width, int height, LuminFormat format) {
            com.mojang.blaze3d.GpuFormat gf = toGpuFormat(format);
            int w = width, h = height;
            GpuTexture tex = RenderSystem.getDevice().createTexture(() -> "lumin_tex", 15, gf, w, h, 1, 1);
            return new LuminTextureGL(tex, w, h, 1, format);
        }
        @Override
        public LuminTexture createTexture3D(int width, int height, int depth, LuminFormat format) {
            com.mojang.blaze3d.GpuFormat gf = toGpuFormat(format);
            int w = width, h = height, d = depth;
            GpuTexture tex = RenderSystem.getDevice().createTexture(() -> "lumin_tex3d", 15, gf, w, h, d, 1);
            return new LuminTextureGL(tex, w, h, d, format);
        }
        @Override
        public LuminSampler createSampler(LuminFilter min, LuminFilter mag, LuminAddressMode u, LuminAddressMode v, LuminAddressMode w) {
            com.mojang.blaze3d.textures.AddressMode sa = toAddressMode(u);
            com.mojang.blaze3d.textures.AddressMode sb = toAddressMode(v);
            com.mojang.blaze3d.textures.AddressMode sc = toAddressMode(w);
            com.mojang.blaze3d.textures.FilterMode sm = toFilter(min);
            com.mojang.blaze3d.textures.FilterMode sf = toFilter(mag);
            com.mojang.blaze3d.textures.GpuSampler samp = RenderSystem.getSamplerCache().getSampler(sa, sb, sm, sf, false);
            return new LuminSamplerGL(samp, min, mag, u, v, w);
        }
        @Override
        public LuminShader createShader(String vertexPath, String fragmentPath) {
            return new LuminShaderGL(vertexPath, fragmentPath);
        }
        @Override
        public LuminPipeline createPipeline(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state) {
            return new LuminPipelineGL(shader, vertexFormat, state);
        }
    }

    final class SwapchainImpl implements LuminSwapchain {
        @Override public int width() { return 0; }
        @Override public int height() { return 0; }
        @Override public LuminFormat format() { return LuminFormat.R8G8B8A8_UNORM; }
        @Override public LuminSwapchainImage acquireNextImage() { return new SwapchainImageImpl(); }
        @Override public void resize(int width, int height) { }
    }

    final class SwapchainImageImpl implements LuminSwapchainImage {
        SwapchainImageImpl() { }
        @Override public LuminTexture texture() { throw new UnsupportedOperationException("Alpha 2.1"); }
        @Override public void present(boolean vsync) { }
        void presentInternal(boolean vsync) { present(vsync); }
    }

    private static com.mojang.blaze3d.GpuFormat toGpuFormat(LuminFormat f) {
        return switch (f) {
            case R8G8B8A8_UNORM -> com.mojang.blaze3d.GpuFormat.RGBA8_UNORM;
            case R8G8B8A8_SRGB -> com.mojang.blaze3d.GpuFormat.RGBA8_UNORM; // 26.2 客户端 jar 无 SRGB8_ALPHA8：退化到 UNORM（Alpha 2.1：探测 deobf）
            case R16G16B16A16_FLOAT -> com.mojang.blaze3d.GpuFormat.RGBA16_FLOAT;
            case R32G32B32A32_FLOAT -> com.mojang.blaze3d.GpuFormat.RGBA32_FLOAT;
            case R11G11B10_FLOAT -> com.mojang.blaze3d.GpuFormat.RGB10A2_UNORM; // 26.2 客户端 jar 无 R11G11B10_FLOAT：退化到 RGB10A2
            case A8_UNORM -> com.mojang.blaze3d.GpuFormat.R8_UNORM;
            case D32_FLOAT, D24_UNORM_S8_UINT, D32_FLOAT_S8X24_UINT -> throw new UnsupportedOperationException("Depth formats (D32_FLOAT / D24_S8 / D32_S8X24) not in MC 26.2 client jar GpuFormat; require GpuFormat.ComponentType lookup or vendor extension (Alpha 2.1)");
            case BC1_UNORM, BC3_UNORM, BC5_UNORM, BC7_UNORM -> throw new UnsupportedOperationException("BCn requires vendor-specific format; MC 26.2 lacks support");
        };
    }
    private static com.mojang.blaze3d.textures.AddressMode toAddressMode(LuminAddressMode m) {
        return switch (m) {
            case REPEAT -> com.mojang.blaze3d.textures.AddressMode.REPEAT;
            case CLAMP_TO_EDGE -> com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE;
            case MIRRORED_REPEAT -> com.mojang.blaze3d.textures.AddressMode.REPEAT; // 26.2 客户端 jar AddressMode 缺 MIRRORED_REPEAT：退化到 REPEAT（Alpha 2.1：探测 deobf）
        };
    }
    private static com.mojang.blaze3d.textures.FilterMode toFilter(LuminFilter f) {
        return switch (f) {
            case NEAREST -> com.mojang.blaze3d.textures.FilterMode.NEAREST;
            case LINEAR -> com.mojang.blaze3d.textures.FilterMode.LINEAR;
        };
    }
}
