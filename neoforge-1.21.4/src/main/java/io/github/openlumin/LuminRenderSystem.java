package io.github.openlumin;

import io.github.openlumin.text.StaticFontLoader;
import io.github.openlumin.holders.RenderTargetHolder;
import io.github.openlumin.holders.RendererHolder;
import io.github.openlumin.shaders.ShaderProgram;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.ProjectionType;
import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTexture;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTextureView;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuSampler;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.TextureFormat;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.FilterMode;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.AddressMode;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuDevice;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.CommandEncoder;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.openlumin.shim.net.minecraft.client.input.MouseButtonEvent;
import io.github.openlumin.impl.DynamicUniformStorage;
import io.github.openlumin.shim.net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.Math;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;

public class LuminRenderSystem {

    private static final Logger LOGGER = LogManager.getLogger("OpenLumin");

    @Nullable
    private static LuminRenderTarget activeTarget = null;
    private static long renderFrameId;

    // ── Shader cache ───────────────────────────────────────────────────────
    private static final Map<ResourceLocation, ShaderProgram> SHADER_CACHE = new ConcurrentHashMap<>();

    /**
     * 根据 LuminPipeline 获取或编译着色器程序（按 location 缓存）
     */
    public static ShaderProgram getOrCompileShader(LuminPipeline pipeline) {
        if (pipeline == null) return null;
        ResourceLocation vsRL = pipeline.getVertexShader();
        ResourceLocation fsRL = pipeline.getFragmentShader();
        if (vsRL == null || fsRL == null) return null;

        return SHADER_CACHE.computeIfAbsent(pipeline.getLocation(), loc -> {
            try {
                ResourceManager rm = Minecraft.getInstance().getResourceManager();
                // 构造完整 shader 路径：openlumin:rectangle → assets/openlumin/shaders/rectangle.vsh
                ResourceLocation vsPath = ResourceLocation.fromNamespaceAndPath(
                    vsRL.getNamespace(), "shaders/" + vsRL.getPath() + ".vsh");
                ResourceLocation fsPath = ResourceLocation.fromNamespaceAndPath(
                    fsRL.getNamespace(), "shaders/" + fsRL.getPath() + ".fsh");
                LOGGER.info("[OpenLumin] Compiling shader: {} / {}", vsPath, fsPath);
                ShaderProgram prog = ShaderProgram.load(vsPath, fsPath, rm);
                LOGGER.info("[OpenLumin] Shader compiled OK: {}", loc);
                return prog;
            } catch (IOException e) {
                LOGGER.error("[OpenLumin] Failed to compile shader {}: {}", loc, e.getMessage(), e);
                return null;
            }
        });
    }

    public static void clearShaderCache() {
        SHADER_CACHE.values().forEach(p -> { if (p != null) p.close(); });
        SHADER_CACHE.clear();
    }
    // ────────────────────────────────────────────────────────────────────────

    public static void setActiveTarget(@Nullable LuminRenderTarget target) {
        activeTarget = target;
    }

    public static void destroyAll() {
        ShaderUniforms.closeAll();
        RenderTargetHolder.INSTANCE.destroyAll();
        RendererHolder.INSTANCE.destroyAll();
        StaticFontLoader.destroyDefault();
        if (quadIndexBuffer != null) {
            quadIndexBuffer.close();
            quadIndexBuffer = null;
            quadIndexBufferCapacity = 0;
        }
        clearShaderCache();
    }

    public static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice writeDynamicUniform(
            String key,
            String label,
            int uniformSize,
            int initialCapacity,
            T uniform
    ) {
        return ShaderUniforms.write(key, label, uniformSize, initialCapacity, uniform);
    }

    public static void endDynamicUniformFrame() {
        ShaderUniforms.endFrame();
    }

    public static void beginRenderFrame() {
        renderFrameId++;
    }

    public static long getRenderFrameId() {
        return renderFrameId;
    }

    @Nullable
    public static LuminRenderTarget getActiveTarget() {
        return activeTarget;
    }

    public static double getGuiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static float getScaledWidth() {
        return (float) Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static float getScaledHeight() {
        return (float) Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public static int getScaledWidthInt() {
        return (int) Math.ceil(getScaledWidth());
    }

    public static int getScaledHeightInt() {
        return (int) Math.ceil(getScaledHeight());
    }

    public static double toEpsilonMouseX(double mouseX) {
        return mouseX * Minecraft.getInstance().getWindow().getGuiScale() / getGuiScale();
    }

    public static double toEpsilonMouseY(double mouseY) {
        return mouseY * Minecraft.getInstance().getWindow().getGuiScale() / getGuiScale();
    }

    public static double toMinecraftGuiX(double epsilonX) {
        return epsilonX * getGuiScale() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static double toMinecraftGuiY(double epsilonY) {
        return epsilonY * getGuiScale() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static int toEpsilonMouseX(int mouseX) {
        return (int) Math.round(toEpsilonMouseX((double) mouseX));
    }

    public static int toEpsilonMouseY(int mouseY) {
        return (int) Math.round(toEpsilonMouseY((double) mouseY));
    }

    public static MouseButtonEvent toEpsilonMouseEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toEpsilonMouseX(event.x()), toEpsilonMouseY(event.y()), event.buttonInfo());
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height) {
        return ScissorUtils.toFramebufferScissor(x, y, width, height);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, float guiHeight) {
        return ScissorUtils.toFramebufferScissor(x, y, width, height, guiHeight);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, int guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, double guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public static void applyOrthoProjection() {
        float w = getScaledWidth();
        float h = getScaledHeight();
        // Build orthographic matrix: (0,0) top-left, (w,h) bottom-right, z in [-1000, 1000]
        org.joml.Matrix4f proj = new org.joml.Matrix4f().setOrtho(0f, w, h, 0f, -1000f, 1000f);
        RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * 获取当前活动目标的 colorTextureView 和 depthTextureView。
     * 如果设置了 activeTarget，则使用 activeTarget；否则使用主 RenderTarget。
     */
    public static GpuTextureView resolveColorView() {
        if (activeTarget != null) return activeTarget.colorView();
        // NeoForge 1.21.4: getColorTextureView() 在 MC 1.21.4 运行时不存在。
        // OpenGL 直接绘制路径不需要此视图；返回 dummy 非 null 值让调用方不跳过绘制。
        return new GpuTextureView(); // dummy sentinel：OpenGL路径不使用此视图
    }

    @Nullable
    public static GpuTextureView resolveDepthView() {
        if (activeTarget != null) return activeTarget.depthView();
        // NeoForge 1.21.4: depth view 不需要，返回 null 即可（调用方允许 null）
        return null;
    }

    public static QuadRenderingInfo prepareQuadRendering(int vertexCount) {
        return prepareQuadRendering(vertexCount, true);
    }

    public static QuadRenderingInfo prepareQuadRendering(int vertexCount, boolean applyProjection) {
        if (applyProjection) {
            LuminRenderSystem.applyOrthoProjection();
        }

        GpuTextureView colorView = resolveColorView();
        GpuTextureView depthView = resolveDepthView();
        if (colorView == null) return null;

        final var indexCount = vertexCount / 4 * 6;
        io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer ibo = getQuadIndexBuffer(indexCount);

        GpuBufferSlice dynamicUniforms = writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                new org.joml.Matrix4f()
        );

        return new QuadRenderingInfo(colorView, depthView, getQuadIndexType(), ibo, indexCount, dynamicUniforms);
    }

    private static io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer quadIndexBuffer;
    private static int quadIndexBufferCapacity = 0;

    public static io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer getQuadIndexBuffer(int indexCount) {
        // Ensure buffer has enough capacity
        if (indexCount > quadIndexBufferCapacity) {
            if (quadIndexBuffer != null) quadIndexBuffer.close();

            int newCapacity = Math.max(indexCount, quadIndexBufferCapacity * 2);
            newCapacity = Math.max(newCapacity, 6); // at least 1 quad
            newCapacity = (newCapacity + 5) / 6 * 6; // round up to multiple of 6

            quadIndexBuffer = new io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer(
                newCapacity * 2, io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer.USAGE_INDEX);

            // Generate quad indices: 0,1,2,0,2,3, 4,5,6,4,6,7, ...
            java.nio.ByteBuffer data = org.lwjgl.BufferUtils.createByteBuffer(newCapacity * 2);
            int quadCount = newCapacity / 6;
            for (int q = 0; q < quadCount; q++) {
                int base = q * 4;
                data.putShort((short) base);
                data.putShort((short)(base + 1));
                data.putShort((short)(base + 2));
                data.putShort((short) base);
                data.putShort((short)(base + 2));
                data.putShort((short)(base + 3));
            }
            data.flip();

            // Upload to GPU
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER,
                                                quadIndexBuffer.getBufferId());
            org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER,
                                                data, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            quadIndexBufferCapacity = newCapacity;
        }
        return quadIndexBuffer;
    }

    public static VertexFormat.IndexType getQuadIndexType() {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        return autoIndices.type();
    }

    public static GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix) {
        // NeoForge不支持RenderSystem.getDynamicUniforms()
        // return RenderSystem.getDynamicUniforms().writeTransform(
        //         modelView, colorModulator, modelOffset, textureMatrix
        // );
        return new GpuBufferSlice(null, 0, 0);
    }

    public static GpuBufferSlice writeDefaultGuiTransform() {
        return writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                new org.joml.Matrix4f()
        );
    }

    public record ScissorRect(int x, int y, int width, int height) {
    }

    public record QuadRenderingInfo(
            GpuTextureView colorView,
            @Nullable GpuTextureView depthView,
            VertexFormat.IndexType indexType,
            io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer ibo,
            int indexCount,
            GpuBufferSlice dynamicUniforms
    ) {
    }

    private static final class ShaderUniforms {

        private static final Map<String, DynamicUniformStorage<DynamicUniformStorage.DynamicUniform>> UNIFORMS = new HashMap<>();

        private ShaderUniforms() {
        }

        @SuppressWarnings("unchecked")
        private static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice write(
                String key,
                String label,
                int uniformSize,
                int initialCapacity,
                T uniform
        ) {
            DynamicUniformStorage<T> storage = (DynamicUniformStorage<T>) UNIFORMS.computeIfAbsent(key, ignored ->
                    new DynamicUniformStorage<>(label, uniformSize, initialCapacity));
            return storage.writeUniform(uniform);
        }

        private static void endFrame() {
            UNIFORMS.values().forEach(DynamicUniformStorage::endFrame);
        }

        private static void closeAll() {
            UNIFORMS.values().forEach(DynamicUniformStorage::close);
            UNIFORMS.clear();
        }

    }

    public static final class LuminRenderTarget implements AutoCloseable {

        private LuminTexture colorTexture;
        private GpuTexture depthTexture;
        private GpuTextureView depthView;
        private final ResourceLocation resourceLocation;
        private final boolean useDepth;
        private int width;
        private int height;
        private boolean closed;

        private LuminRenderTarget(String name, int width, int height, boolean useDepth) {
            this.width = width;
            this.height = height;
            this.useDepth = useDepth;
            this.resourceLocation = ResourceLocation.fromNamespaceAndPath("openlumin", "lumin-rt" + name);
            createTextures();
        }

        public static LuminRenderTarget create(String name, int width, int height) {
            return RenderTargetHolder.INSTANCE.register(new LuminRenderTarget(name, width, height, false));
        }

        public static LuminRenderTarget createWithDepth(String name, int width, int height) {
            return RenderTargetHolder.INSTANCE.register(new LuminRenderTarget(name, width, height, true));
        }

        private void createTextures() {
            closed = false;
            // NeoForge不支持RenderSystem.getDevice()
            // var device = RenderSystem.getDevice();

            // final var colorTexture = device.createTexture(
            //         "lumin-rt-color",
            //         GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
            //         TextureFormat.RGBA8,
            //         width, height, 1, 1
            // );
            // final var colorView = device.createTextureView(colorTexture);
            final var colorTexture = new GpuTexture(width, height, 0);
            final var colorView = new GpuTextureView(colorTexture);

            if (useDepth) {
                // depthTexture = device.createTexture(
                //         "lumin-rt-depth",
                //         GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                //         TextureFormat.DEPTH32,
                //         width, height, 1, 1
                // );
                // depthView = device.createTextureView(depthTexture);
                depthTexture = new GpuTexture(width, height, 0);
                depthView = new GpuTextureView(depthTexture);
            }

            // final var sampler = RenderSystem.getDevice().createSampler(
            //         AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            //         FilterMode.NEAREST, FilterMode.NEAREST,
            //         1, OptionalDouble.empty()
            // );
            final var sampler = new GpuSampler();

            this.colorTexture = new LuminTexture(colorTexture, colorView, sampler);

            Minecraft.getInstance().getTextureManager().register(resourceLocation, getColorTexture());
        }

        public void resize(int newWidth, int newHeight) {
            if (newWidth == width && newHeight == height) return;
            destroyTextures();
            width = newWidth;
            height = newHeight;
            createTextures();
        }

        public ResourceLocation getIdentifier() {
            return resourceLocation;
        }

        public void clear() {
            // NeoForge不支持RenderSystem.getDevice()
            // var encoder = RenderSystem.getDevice().createCommandEncoder();
            // if (useDepth) {
            //     encoder.clearColorAndDepthTextures(colorTexture.getTexture(), 0, depthTexture, 1.0);
            // } else {
            //     encoder.clearColorTexture(colorTexture.getTexture(), 0);
            // }
        }

        public GpuTextureView colorView() {
            return colorTexture.getTextureView();
        }

        @Nullable
        public GpuTextureView depthView() {
            return depthView;
        }

        public GpuTexture colorTexture() {
            return colorTexture.getTexture();
        }

        public GpuSampler sampler() {
            return colorTexture.getSampler();
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public LuminTexture getColorTexture() {
            return colorTexture;
        }

        private void destroyTextures() {
            if (closed) {
                return;
            }
            closed = true;
            Minecraft.getInstance().getTextureManager().release(resourceLocation);
            if (depthView != null) depthView.close();
            if (depthTexture != null) depthTexture.close();
            colorTexture = null;
            depthView = null;
            depthTexture = null;
        }

        @Override
        public void close() {
            destroyTextures();
            RenderTargetHolder.INSTANCE.unregister(this);
        }
    }

}
