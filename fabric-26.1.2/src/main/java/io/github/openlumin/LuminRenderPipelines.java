package io.github.openlumin;

import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * fabric-26.1.2 适配：
 * - 26.1.2 移除了 withBlend(BlendFunction)，混合移入 withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
 * - 资源标识改用 Identifier
 * - 基础 snippet 仍只声明 DynamicTransforms + Projection 两个 UBO，
 *   与各 vsh #moj_import 的 dynamictransforms.glsl + projection.glsl 对应
 */
public class LuminRenderPipelines {

    /**
     * 基础 snippet：DynamicTransforms + Projection UBO + TRANSLUCENT alpha blending。
     * 所有 OpenLumin pipeline 都需要 alpha blending：
     *   - RECTANGLE：支持半透明颜色
     *   - ROUND_RECT 等：SDF 抗锯齿依赖 smoothstep alpha 与背景混合
     */
    /** 对外公开，供 Render3DScheduler 等其他 pipeline 复用 */
    public final static RenderPipeline.Snippet BASE_SNIPPET = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline.Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(BASE_SNIPPET)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/rectangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ttf_font_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ttf_font_no_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font_no_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/round_rectangle"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/round_rectangle_outline"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT_OUTLINE, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline ELLIPSE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ellipse"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ellipse"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ellipse"))
            .withCull(false)
            .build();

    public final static RenderPipeline ARC = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/arc"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","arc"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","arc"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/shadow"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","shadow"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/texture"))
            .withVertexFormat(LuminVertexFormats.TEXTURE, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","texture"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/triangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","triangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","triangle"))
            .withCull(false)
            .build();
}
