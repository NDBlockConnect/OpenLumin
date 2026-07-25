package com.mojang.blaze3d.pipeline;

import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * NeoForge适配层：模拟Fabric的RenderPipeline API
 */
public class RenderPipeline {

    public static Builder builder(Snippet snippet) {
        return new Builder();
    }

    public static Builder builder(RenderPipeline parent) {
        return new Builder();
    }

    public void bind() {
        // NeoForge使用不同的API
    }

    public void unbind() {
        // NeoForge使用不同的API
    }

    public void close() {
        // NeoForge使用不同的API
    }

    public static class Builder {
        public Builder blend(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
            return this;
        }

        public Builder blend(int src, int dst) {
            return this;
        }

        public Builder depthTest(int func) {
            return this;
        }

        public Builder depthMask(boolean mask) {
            return this;
        }

        public Builder colorMask(boolean r, boolean g, boolean b, boolean a) {
            return this;
        }

        public Builder cullFace(int mode) {
            return this;
        }

        public Builder lineWidth(float width) {
            return this;
        }

        public Builder vertexShader(String shader) {
            return this;
        }

        public Builder fragmentShader(String shader) {
            return this;
        }

        public Builder withColorTargetState(ColorTargetState state) {
            return this;
        }

        public Builder withLocation(ResourceLocation location) {
            return this;
        }

        public Builder withVertexFormat(VertexFormat format, VertexFormat.Mode mode) {
            return this;
        }

        public Builder withVertexShader(ResourceLocation shader) {
            return this;
        }

        public Builder withFragmentShader(ResourceLocation shader) {
            return this;
        }

        public Builder withCull(boolean cull) {
            return this;
        }

        public Builder withSampler(String sampler) {
            return this;
        }

        public Builder withDepthStencilState(DepthStencilState state) {
            return this;
        }

        public Builder withUniform(String name, com.mojang.blaze3d.shaders.UniformType type) {
            return this;
        }

        public Snippet buildSnippet() {
            return new Snippet();
        }

        public RenderPipeline build(String name) {
            return new RenderPipeline();
        }

        public RenderPipeline build() {
            return new RenderPipeline();
        }
    }

    public static class Snippet {
        public Snippet() {
        }
    }
}
