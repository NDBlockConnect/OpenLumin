package io.github.openlumin;

import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;


public class LuminRenderPipelines {

    private final static RenderPipeline.Snippet NO_BLEND_DEPTH_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/rectangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "rectangle"))
            .withFragmentShader(Identifier.of("openlumin", "rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/ttf_font_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "ttf_font"))
            .withFragmentShader(Identifier.of("openlumin", "ttf_font_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/ttf_font_no_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "ttf_font"))
            .withFragmentShader(Identifier.of("openlumin", "ttf_font_no_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/round_rectangle"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "round_rectangle"))
            .withFragmentShader(Identifier.of("openlumin", "round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/round_rectangle_outline"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT_OUTLINE, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "round_rectangle_outline"))
            .withFragmentShader(Identifier.of("openlumin", "round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/shadow"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "shadow"))
            .withFragmentShader(Identifier.of("openlumin", "shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/texture"))
            .withVertexFormat(LuminVertexFormats.TEXTURE, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.of("openlumin", "texture"))
            .withFragmentShader(Identifier.of("openlumin", "texture"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(Identifier.of("openlumin", "pipelines/triangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(Identifier.of("openlumin", "triangle"))
            .withFragmentShader(Identifier.of("openlumin", "triangle"))
            .withCull(false)
            .build();

}
