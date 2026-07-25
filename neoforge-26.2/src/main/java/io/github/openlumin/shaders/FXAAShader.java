package io.github.openlumin.shaders;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystemExtensions;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import net.minecraft.client.Minecraft;

/**
 * fabric-1.21.10 override：bindTexture → bindSampler，createSampler 已移除。
 */
public class FXAAShader {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private static final ResourceLocation vertexShader = ResourceLocation.withDefaultNamespace("core/screenquad");
    private static final ResourceLocation fragmentShader = ResourceLocation.fromNamespaceAndPath("openlumin","fxaa");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipeline/fxaa"))
                    .withVertexShader(vertexShader)
                    .withFragmentShader(fragmentShader)
                    .withUniform("FxaaInfo", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget() {
        render(Minecraft.getInstance().getMainRenderTarget());
    }

    public void render(RenderTarget framebuffer) {
        this.ensureProgram();

        if (framebuffer == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return;
        }

        if (framebuffer.getColorTexture() == null || framebuffer.getColorTextureView() == null) {
            return;
        }

        this.ensureInput(framebuffer);

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystemExtensions.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                framebuffer.getColorTexture(),
                this.input.getColorTexture(),
                0, 0, 0, 0, 0,
                framebuffer.width, framebuffer.height
        );

        GpuBufferSlice fxaaInfo = LuminRenderSystem.writeDynamicUniform(
                "fxaa_info",
                "Epsilon FXAA UBO",
                UNIFORMS_SIZE,
                4,
                new FXAAInfo(framebuffer.width, framebuffer.height)
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon FXAA",
                framebuffer.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(this.pipeline);
            renderPass.setUniform("FxaaInfo", fxaaInfo);
            // 1.21.10: bindSampler(name, GpuTextureView) — 无需 GpuSampler 对象
            renderPass.bindSampler("InputSampler", this.input.getColorTextureView());
            renderPass.draw(0, 3);
        }
    }

    private record FXAAInfo(float width, float height) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(width, height, 1.0f / width, 1.0f / height);
        }
    }
}
