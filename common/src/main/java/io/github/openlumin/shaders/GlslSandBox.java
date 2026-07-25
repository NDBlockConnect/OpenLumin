package io.github.openlumin.shaders;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystemExtensions;
import com.mojang.blaze3d.platform.GpuTextureView;
import io.github.openlumin.impl.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.client.Minecraft;

public class GlslSandBox implements AutoCloseable {

    public static final GlslSandBox INSTANCE = new GlslSandBox();

    public static final ResourceLocation SEA_LEVEL = ResourceLocation.fromNamespaceAndPath("openlumin","menu/sea_level");
    public static final ResourceLocation CLOUDS = ResourceLocation.fromNamespaceAndPath("openlumin","menu/clouds");
    public static final ResourceLocation ALIEN_TERRAIN = ResourceLocation.fromNamespaceAndPath("openlumin","menu/alien_terrain");
    public static final ResourceLocation INFERNO = ResourceLocation.fromNamespaceAndPath("openlumin","menu/inferno");
    public static final ResourceLocation PLANET = ResourceLocation.fromNamespaceAndPath("openlumin","menu/planet");
    public static final ResourceLocation BLACK_HOLE = ResourceLocation.fromNamespaceAndPath("openlumin","menu/black_hole");
    public static final ResourceLocation MINECRAFT = ResourceLocation.fromNamespaceAndPath("openlumin","menu/minecraft");

    private static final int SANDBOX_INFO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private final Map<ResourceLocation, RenderPipeline> pipelines = new HashMap<>();

    private long initTime = System.currentTimeMillis();

    private RenderPipeline getOrCreatePipeline(ResourceLocation fragmentShader) {
        return pipelines.computeIfAbsent(fragmentShader, shader -> RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(shader.getNamespace(), "pipelines/glsl_sandbox/" + shader.getPath().replace('/', '_')))
                .withVertexShader(ResourceLocation.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(shader)
                .withUniform("GlslSandboxInfo", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .build()
        );
    }

    public void resetTime() {
        initTime = System.currentTimeMillis();
    }

    public void render(ResourceLocation fragmentShader, double mouseX, double mouseY) {
        render(fragmentShader, mouseX, mouseY, initTime);
    }

    public void render(ResourceLocation fragmentShader, double mouseX, double mouseY, long startTimeMs) {
        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;

        final var activeTarget = LuminRenderSystem.getActiveTarget();
        final int targetWidth = activeTarget != null ? activeTarget.width() : Minecraft.getInstance().getMainRenderTarget().width;
        final int targetHeight = activeTarget != null ? activeTarget.height() : Minecraft.getInstance().getMainRenderTarget().height;

        if (targetWidth <= 0 || targetHeight <= 0) return;

        float scaleX = targetWidth / LuminRenderSystem.getScaledWidth();
        float scaleY = targetHeight / LuminRenderSystem.getScaledHeight();

        float mousePxX = (float) mouseX * scaleX;
        float mousePxY = (float) mouseY * scaleY;
        float mouseUvX = mousePxX / targetWidth;
        float mouseUvY = (targetHeight - 1.0f - mousePxY) / targetHeight;
        float elapsedTime = (System.currentTimeMillis() - startTimeMs) / 1000.0f;
        GpuBufferSlice sandboxInfo = LuminRenderSystem.writeDynamicUniform(
                "glsl_sandbox_info",
                "Lumin GLSL Sandbox UBO",
                SANDBOX_INFO_SIZE,
                4,
                new SandboxInfo(targetWidth, targetHeight, elapsedTime, mouseUvX, mouseUvY, mousePxX, mousePxY)
        );

        final var encoder = RenderSystemExtensions.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "Lumin GLSL Sandbox",
                colorView, OptionalInt.empty(),
                LuminRenderSystem.resolveDepthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(getOrCreatePipeline(fragmentShader));
            // TODO: RenderSystem.bindDefaultUniforms not available in NeoForge 1.21.4
            pass.setUniform("GlslSandboxInfo", sandboxInfo);
            pass.draw(0, 3);
        }
    }

    @Override
    public void close() {
        pipelines.clear();
    }

    private record SandboxInfo(
            float width,
            float height,
            float elapsedTime,
            float mouseUvX,
            float mouseUvY,
            float mousePxX,
            float mousePxY
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(width, height, elapsedTime, 0.0f)
                    .putVec4(mouseUvX, mouseUvY, mousePxX, mousePxY);
        }

    }

}
