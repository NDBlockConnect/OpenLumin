package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.GpuSampler;

/**
 * NeoForge适配层：模拟Fabric的RenderSetup API
 */
public class RenderSetup {

    public static Builder builder(RenderPipeline pipeline) {
        return new Builder(pipeline);
    }

    public static class Builder {
        private final RenderPipeline pipeline;

        public Builder(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public Builder withTexture(String name, net.minecraft.resources.ResourceLocation textureId, java.util.function.Supplier<GpuSampler> samplerSupplier) {
            // NeoForge使用不同的API
            return this;
        }

        public Builder bufferSize(int size) {
            // NeoForge使用不同的API
            return this;
        }

        public RenderSetup createRenderSetup() {
            // NeoForge使用不同的API
            return new RenderSetup();
        }
    }
}
