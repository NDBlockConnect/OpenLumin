package io.github.openlumin.shaders;

import net.minecraft.resources.Identifier;
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.openlumin.LuminRenderPipelines;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;

import java.nio.ByteBuffer;
import java.util.Optional;

import net.minecraft.client.Minecraft;

/**
 * fabric-1.21.10 override锛歜indTexture 鈫?bindSampler锛宑reateSampler 宸茬Щ闄ゃ€? */
public class FXAAShader {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private static final Identifier vertexShader = Identifier.withDefaultNamespace("core/screenquad");
    private static final Identifier fragmentShader = Identifier.fromNamespaceAndPath("openlumin","fxaa");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(LuminRenderPipelines.POST_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipeline/fxaa"))
                    .withVertexShader(vertexShader)
                    .withFragmentShader(fragmentShader)
                    .withBindGroupLayout(LuminRenderPipelines.POST_LAYOUT)
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, false, GpuFormat.RGBA8_UNORM);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget() {
        render(Minecraft.getInstance().gameRenderer.mainRenderTarget());
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

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
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
                Optional.empty()
        )) {
            renderPass.setPipeline(this.pipeline);
            renderPass.setUniform("FxaaInfo", fxaaInfo);
            // 1.21.10: bindSampler(name, GpuTextureView) 鈥?鏃犻渶 GpuSampler 瀵硅薄
            renderPass.bindTexture("InputSampler", this.input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(3, 1, 0, 0);
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
