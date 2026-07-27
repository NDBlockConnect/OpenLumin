package io.github.openlumin;

import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * fabric-1.21.10 override：
 * - POST_PROCESSING_SNIPPET 用于后处理，自带 withVertexFormat 且不含 Projection UBO ——
 *   直接当基础 snippet 会导致 ProjMat 为零矩阵，顶点全被裁剪。
 * - 正确做法：从零构建 snippet，仅声明 DynamicTransforms + Projection 两个 UBO，
 *   与 rectangle.vsh / round_rectangle.vsh 等 #moj_import 的 dynamictransforms.glsl +
 *   projection.glsl 完全对应。
 * - withColorTargetState() / withDepthStencilState() 在 1.21.10 已移除，不调用。
 */
public class LuminRenderPipelines {

    /**
     * 基础 snippet：DynamicTransforms + Projection UBO + TRANSLUCENT alpha blending。
     * 1.21.10 API：withBlend(BlendFunction) 替代已删除的 withColorTargetState()。
     * 所有 OpenLumin pipeline 都需要 alpha blending：
     *   - RECTANGLE：支持半透明颜色
     *   - ROUND_RECT 等：SDF 抗锯齿依赖 smoothstep alpha 与背景混合
     */
    /** 对外公开，供 Render3DScheduler 等其他 pipeline 复用 */
    public final static RenderPipeline.Snippet BASE_SNIPPET = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .buildSnippet();

    public final static RenderPipeline.Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(BASE_SNIPPET)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/rectangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","rectangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/ttf_font_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","ttf_font_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/ttf_font_no_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","ttf_font_no_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/round_rectangle"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/round_rectangle_outline"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT_OUTLINE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline ELLIPSE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/ellipse"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","ellipse"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","ellipse"))
            .withCull(false)
            .build();

    public final static RenderPipeline ARC = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/arc"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","arc"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","arc"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/shadow"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","shadow"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/texture"))
            .withVertexFormat(LuminVertexFormats.TEXTURE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","texture"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","texture"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipelines/triangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin","triangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin","triangle"))
            .withCull(false)
            .build();
}
