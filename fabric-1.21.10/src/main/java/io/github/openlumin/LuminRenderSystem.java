package io.github.openlumin;

import io.github.openlumin.platform.PlatformRegistry;
import io.github.openlumin.text.StaticFontLoader;
import io.github.openlumin.holders.RenderTargetHolder;
import io.github.openlumin.holders.RendererHolder;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.compat.RenderSystemShim;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;

import javax.annotation.Nullable;
import java.lang.Math;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

import net.minecraft.client.Minecraft;

/**
 * OpenLumin 渲染系统统一封装层。
 *
 * 通过平台抽象层（LuminPlatform）支持 Fabric/Forge/NeoForge 跨加载器兼容，
 * 隔离不同 Minecraft 版本的 API 差异。
 */
public class LuminRenderSystem {

    /** 1.21.10：使用 CachedOrthoProjectionMatrixBuffer 替代 Projection+ProjectionMatrixBuffer */
    private static final CachedOrthoProjectionMatrixBuffer guiProjectionMatrixBuffer =
            new CachedOrthoProjectionMatrixBuffer("lumin-gui", -1000.0f, 1000.0f, true);  // true = invertY：Y=0在顶部，MC GUI坐标系

    @Nullable
    private static LuminRenderTarget activeTarget = null;
    private static long renderFrameId;

    public static void setActiveTarget(@Nullable LuminRenderTarget target) {
        activeTarget = target;
    }

    public static void destroyAll() {
        ShaderUniforms.closeAll();
        RenderTargetHolder.INSTANCE.destroyAll();
        RendererHolder.INSTANCE.destroyAll();
        StaticFontLoader.destroyDefault();
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
        return (float) (Minecraft.getInstance().getWindow().getWidth() / getGuiScale());
    }

    public static float getScaledHeight() {
        return (float) (Minecraft.getInstance().getWindow().getHeight() / getGuiScale());
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

    /**
     * 1.21.10：用 CachedOrthoProjectionMatrixBuffer 设置正交投影矩阵。
     */
    public static void applyOrthoProjection() {
        float w = getScaledWidth();
        float h = getScaledHeight();
        GpuBufferSlice projBuffer = guiProjectionMatrixBuffer.getBuffer(w, h);
        RenderSystem.setProjectionMatrix(projBuffer, ProjectionType.ORTHOGRAPHIC);
    }

    public static GpuTextureView resolveColorView() {
        if (activeTarget != null) return activeTarget.colorView();
        return PlatformRegistry.get().resolveColorView();
    }

    @Nullable
    public static GpuTextureView resolveDepthView() {
        if (activeTarget != null) return activeTarget.depthView();
        return PlatformRegistry.get().resolveDepthView();
    }

    /**
     * 获取当前模型视图矩阵（通过平台抽象层）。
     * 封装 PlatformRegistry.get().getModelViewMatrix() 调用，提供统一入口。
     */
    public static Matrix4f getModelViewMatrix() {
        return PlatformRegistry.get().getModelViewMatrix();
    }

    /**
     * 绑定默认 uniform（通过平台抽象层）。
     * 封装 PlatformRegistry.get().bindDefaultUniforms() 调用，提供统一入口。
     */
    public static void bindDefaultUniforms(com.mojang.blaze3d.systems.RenderPass pass) {
        PlatformRegistry.get().bindDefaultUniforms(pass);
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
        GpuBuffer ibo = getQuadIndexBuffer(indexCount);

        // 1.21.10：TextureTransform 改用单位矩阵，writeTransform 增加第5参数 lineWidth=1.0f
        GpuBufferSlice dynamicUniforms = writeTransform(
                getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                new Matrix4f()
        );

        return new QuadRenderingInfo(colorView, depthView, getQuadIndexType(), ibo, indexCount, dynamicUniforms);
    }

    /**
     * 1.21.10：通过平台抽象层获取共享 quad index buffer。
     */
    public static GpuBuffer getQuadIndexBuffer(int indexCount) {
        return PlatformRegistry.get().getSequentialBuffer(VertexFormat.Mode.QUADS, indexCount);
    }

    public static VertexFormat.IndexType getQuadIndexType() {
        return RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type();
    }

    /**
     * 1.21.10：writeTransform 第5参数 lineWidth=1.0f（原生 API 新增）。
     */
    public static GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix) {
        return writeTransform(modelView, colorModulator, modelOffset, textureMatrix, 1.0f);
    }

    public static GpuBufferSlice writeTransform(
            Matrix4fc modelView,
            Vector4fc colorModulator,
            Vector3fc modelOffset,
            Matrix4fc textureMatrix,
            float lineWidth
    ) {
        return writeDynamicUniform(
                "dynamic_transforms",
                "Lumin Dynamic Transforms UBO",
                DynamicUniforms.TRANSFORM_UBO_SIZE,
                64,
                new DynamicUniforms.Transform(
                        new Matrix4f(modelView),
                        new Vector4f(colorModulator),
                        new Vector3f(modelOffset),
                        new Matrix4f(textureMatrix),
                        lineWidth
                )
        );
    }

    public static GpuBufferSlice writeDefaultGuiTransform() {
        return writeTransform(
                getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                new Matrix4f()
        );
    }

    public record ScissorRect(int x, int y, int width, int height) {}

    public record QuadRenderingInfo(
            GpuTextureView colorView,
            @Nullable GpuTextureView depthView,
            VertexFormat.IndexType indexType,
            GpuBuffer ibo,
            int indexCount,
            GpuBufferSlice dynamicUniforms
    ) {}

    private static final class ShaderUniforms {

        private static final Map<String, DynamicUniformStorage<DynamicUniformStorage.DynamicUniform>> UNIFORMS = new HashMap<>();

        private ShaderUniforms() {}

        @SuppressWarnings("unchecked")
        private static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice write(
                String key, String label, int uniformSize, int initialCapacity, T uniform
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
            var device = RenderSystemShim.getDevice();

            final var colorTex = device.createTexture(
                    "lumin-rt-color",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    TextureFormat.RGBA8,
                    width, height, 1, 1
            );
            final var colorView = device.createTextureView(colorTex);

            if (useDepth) {
                depthTexture = device.createTexture(
                        "lumin-rt-depth",
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                        TextureFormat.DEPTH32,
                        width, height, 1, 1
                );
                depthView = device.createTextureView(depthTexture);
            }

            // 1.21.10：无 GpuSampler，LuminTexture 不再持有 sampler
            this.colorTexture = new LuminTexture(colorTex, colorView);
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
            var encoder = RenderSystemShim.getDevice().createCommandEncoder();
            if (useDepth) {
                encoder.clearColorAndDepthTextures(colorTexture.getTexture(), 0, depthTexture, 1.0);
            } else {
                encoder.clearColorTexture(colorTexture.getTexture(), 0);
            }
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
            if (closed) return;
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
