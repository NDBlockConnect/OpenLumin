package io.github.openlumin.shim.com.mojang.blaze3d.pipeline;

import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge适配层：RenderPipeline with shader metadata storage
 * (Shader compilation is delegated to LuminRenderSystem in main sourceset)
 */
public class RenderPipeline {

    private final ResourceLocation location;
    private final ResourceLocation vertexShader;
    private final ResourceLocation fragmentShader;
    private final VertexFormat vertexFormat;
    private final VertexFormat.Mode vertexMode;
    private final List<String> samplers;

    private RenderPipeline(ResourceLocation location, ResourceLocation vs, ResourceLocation fs,
                           VertexFormat format, VertexFormat.Mode mode, List<String> samplers) {
        this.location = location;
        this.vertexShader = vs;
        this.fragmentShader = fs;
        this.vertexFormat = format != null ? format : com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR;
        this.vertexMode = mode != null ? mode : VertexFormat.Mode.QUADS;
        this.samplers = samplers != null ? samplers : new ArrayList<>();
    }

    public ResourceLocation getLocation() { return location; }
    public ResourceLocation getVertexShader() { return vertexShader; }
    public ResourceLocation getFragmentShader() { return fragmentShader; }
    public VertexFormat getVertexFormat() { return vertexFormat; }
    public VertexFormat.Mode getVertexMode() { return vertexMode; }
    public List<String> getSamplers() { return samplers; }

    public void bind() {}
    public void unbind() {}
    public void close() {}

    public static Builder builder(Snippet snippet) {
        return new Builder();
    }

    public static Builder builder(RenderPipeline parent) {
        return new Builder();
    }

    public static class Builder {
        private ResourceLocation location;
        private ResourceLocation vertexShader;
        private ResourceLocation fragmentShader;
        private VertexFormat vertexFormat;
        private VertexFormat.Mode vertexMode;
        private final List<String> samplers = new ArrayList<>();

        public Builder withLocation(ResourceLocation loc) { this.location = loc; return this; }
        public Builder withVertexFormat(VertexFormat fmt, VertexFormat.Mode mode) {
            this.vertexFormat = fmt; this.vertexMode = mode; return this;
        }
        public Builder withVertexShader(ResourceLocation rl) { this.vertexShader = rl; return this; }
        public Builder withFragmentShader(ResourceLocation rl) { this.fragmentShader = rl; return this; }
        public Builder withSampler(String name) { this.samplers.add(name); return this; }

        // Stub methods for compatibility
        public Builder blend(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) { return this; }
        public Builder blend(int src, int dst) { return this; }
        public Builder depthTest(int func) { return this; }
        public Builder depthMask(boolean mask) { return this; }
        public Builder colorMask(boolean r, boolean g, boolean b, boolean a) { return this; }
        public Builder cullFace(int mode) { return this; }
        public Builder lineWidth(float width) { return this; }
        public Builder vertexShader(String shader) { return this; }
        public Builder fragmentShader(String shader) { return this; }
        public Builder withColorTargetState(ColorTargetState state) { return this; }
        public Builder withCull(boolean cull) { return this; }
        public Builder withDepthStencilState(DepthStencilState state) { return this; }
        public Builder withUniform(String name, io.github.openlumin.shim.com.mojang.blaze3d.shaders.UniformType type) { return this; }

        public Snippet buildSnippet() { return new Snippet(); }
        public RenderPipeline build(String name) { return build(); }
        public RenderPipeline build() {
            return new RenderPipeline(location, vertexShader, fragmentShader,
                                      vertexFormat, vertexMode, new ArrayList<>(samplers));
        }
    }

    public static class Snippet {
        public Snippet() {}
    }
}
