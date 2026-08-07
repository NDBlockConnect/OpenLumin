package io.github.openlumin;

import io.github.openlumin.platform.PlatformRegistry;
import io.github.openlumin.holders.RenderTargetHolder;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import org.joml.*;

import javax.annotation.Nullable;
import java.lang.Math;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

/**
 * OpenLumin 渲染系统统一封装层（26.1 基线）。
 *
 * 与 1.21.10 基线的差异：
 * - 正交投影改用 Projection + ProjectionMatrixBuffer（1.21.10 用 CachedOrthoProjectionMatrixBuffer）
 * - writeTransform 为 4 参数（26.1.2 的 DynamicUniforms 无 lineWidth 字段）
 * - 资源标识使用 Identifier（替代 Identifier）
 * - 采样器为独立 GpuSampler，经平台抽象层解析
 */
public class LuminRenderSystem {

    /** 26.1.2：Projection + ProjectionMatrixBuffer 组合提供带缓存的正交投影 UBO */
    private static final Projection guiProjection = new Projection();
    private static final ProjectionMatrixBuffer guiProjectionMatrixBuffer = new ProjectionMatrixBuffer("lumin-gui");

    static {
        // invertY=true：Y=0在顶部，MC GUI坐标系
        guiProjection.setupOrtho(-1000.0f, 1000.0f, 1, 1, true);
    }

    @Nullable
    private static LuminRenderTarget activeTarget = null;
    private static long renderFrameId;

    public static void setActiveTarget(@Nullable LuminRenderTarget target) {
        activeTarget = target;
    }

    public static void destroyAll() {
        ShaderUniforms.closeAll();
        RenderTargetHolder.INSTANCE.destroyAll();
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
     * 26.1.2：用 Projection + ProjectionMatrixBuffer 设置正交投影矩阵。
     */
    public static void applyOrthoProjection() {
        float w = getScaledWidth();
        float h = getScaledHeight();
        guiProjection.setSize(w, h);
        GpuBufferSlice projBuffer = guiProjectionMatrixBuffer.getBuffer(guiProjection);
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
     */
    public static Matrix4f getModelViewMatrix() {
        return PlatformRegistry.get().getModelViewMatrix();
    }

    /**
     * 绑定默认 uniform（通过平台抽象层）。
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

        GpuBufferSlice dynamicUniforms = writeTransform(
                getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                new Matrix4f()
        );

        return new QuadRenderingInfo(colorView, depthView, getQuadIndexType(), ibo, indexCount, dynamicUniforms);
    }

    /**
     * 通过平台抽象层获取共享 quad index buffer。
     */
    public static GpuBuffer getQuadIndexBuffer(int indexCount) {
        return PlatformRegistry.get().getSequentialBuffer(VertexFormat.Mode.QUADS, indexCount);
    }

    public static VertexFormat.IndexType getQuadIndexType() {
        return RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type();
    }

    public static GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix) {
        return writeDynamicUniform(
                "dynamic_transforms",
                "Lumin Dynamic Transforms UBO",
                DynamicUniforms.TRANSFORM_UBO_SIZE,
                64,
                new DynamicUniforms.Transform(
                        new Matrix4f(modelView),
                        new Vector4f(colorModulator),
                        new Vector3f(modelOffset),
                        new Matrix4f(textureMatrix)
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
        private final Identifier resourceLocation;
        private final boolean useDepth;
        private int width;
        private int height;
        private boolean closed;

        private LuminRenderTarget(String name, int width, int height, boolean useDepth) {
            this.width = width;
            this.height = height;
            this.useDepth = useDepth;
            this.resourceLocation = Identifier.fromNamespaceAndPath("openlumin", "lumin-rt" + name);
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
            var device = RenderSystem.getDevice();

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

        public Identifier getIdentifier() {
            return resourceLocation;
        }

        public void clear() {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
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
