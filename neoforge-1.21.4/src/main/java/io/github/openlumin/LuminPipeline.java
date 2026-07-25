package io.github.openlumin;

import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * OpenLumin 自有渲染管线描述符，完全独立于 MC 的 RenderPipeline。
 *
 * <p>MC 1.21.4 运行时不含 {@code io.github.openlumin.shim.com.mojang.blaze3d.pipeline.RenderPipeline} 类，
 * 直接使用它会导致 {@link NoClassDefFoundError}。
 * 此类仅持有着色器 ResourceLocation 和顶点格式信息，
 * 供 {@link LuminImmediateRenderer} 和 {@link LuminRenderSystem} 使用。</p>
 */
public final class LuminPipeline {

    private final ResourceLocation location;
    private final ResourceLocation vertexShader;
    private final ResourceLocation fragmentShader;
    private final VertexFormat vertexFormat;
    private final VertexFormat.Mode mode;

    private LuminPipeline(Builder builder) {
        this.location       = builder.location;
        this.vertexShader   = builder.vertexShader;
        this.fragmentShader = builder.fragmentShader;
        this.vertexFormat   = builder.vertexFormat;
        this.mode           = builder.mode;
    }

    public ResourceLocation getLocation()       { return location; }
    public ResourceLocation getVertexShader()   { return vertexShader; }
    public ResourceLocation getFragmentShader() { return fragmentShader; }
    public VertexFormat     getVertexFormat()   { return vertexFormat; }
    public VertexFormat.Mode getMode()          { return mode; }

    public static Builder builder(ResourceLocation location) {
        return new Builder(location);
    }

    public static final class Builder {
        private final ResourceLocation location;
        private ResourceLocation vertexShader;
        private ResourceLocation fragmentShader;
        private VertexFormat     vertexFormat;
        private VertexFormat.Mode mode;

        private Builder(ResourceLocation location) {
            this.location = location;
        }

        public Builder withVertexShader(ResourceLocation vs) {
            this.vertexShader = vs; return this;
        }
        public Builder withFragmentShader(ResourceLocation fs) {
            this.fragmentShader = fs; return this;
        }
        public Builder withVertexFormat(VertexFormat fmt, VertexFormat.Mode m) {
            this.vertexFormat = fmt; this.mode = m; return this;
        }

        public LuminPipeline build() {
            return new LuminPipeline(this);
        }
    }
}
