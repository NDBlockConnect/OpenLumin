package io.github.openlumin;

import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

/**
 * fabric-26.2 适配：26.2 管线 uniform/sampler 声明迁移到 BindGroupLayout 体系；
 * 顶点格式经 withVertexBinding(0, format) + withPrimitiveTopology 绑定。
 */
public class LuminRenderPipelines {

    private static final BindGroupLayout TRANSFORM_UBOS = BindGroupLayout.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout WORLD_UBOS = BindGroupLayout.builder()
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout SAMPLER0 = BindGroupLayout.builder()
            .withSampler("Sampler0")
            .build();

    /** 对外公开，供 Render3DScheduler 等其他 pipeline 复用 */
    /** 后处理共享绑定组：自定义 UBO + 输入采样器 */
    public static final BindGroupLayout POST_LAYOUT = BindGroupLayout.builder()
            .withUniform("BlurUniforms", UniformType.UNIFORM_BUFFER)
            .withUniform("BoxBlurUniforms", UniformType.UNIFORM_BUFFER)
            .withUniform("FilterColor", UniformType.UNIFORM_BUFFER)
            .withUniform("FxaaInfo", UniformType.UNIFORM_BUFFER)
            .withUniform("GlslSandboxInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("InputSampler")
            .build();

    /**
     * 3D 盒体模糊专用绑定组：BoxBlurUniforms UBO + 输入采样器。
     * blur_3d_box 管线同时经 BASE_SNIPPET 获得 DynamicTransforms/Projection 矩阵绑定。
     */
    public static final BindGroupLayout BOX_BLUR_LAYOUT = BindGroupLayout.builder()
            .withUniform("BoxBlurUniforms", UniformType.UNIFORM_BUFFER)
            .withSampler("InputSampler")
            .build();

    public final static RenderPipeline.Snippet POST_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(POST_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline.Snippet BASE_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(TRANSFORM_UBOS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline.Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(BASE_SNIPPET)
            .withBindGroupLayout(WORLD_UBOS)
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/rectangle"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withBindGroupLayout(SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ttf_font_aa"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font_aa"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(BASE_SNIPPET)
            .withBindGroupLayout(SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ttf_font_no_aa"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ttf_font_no_aa"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/round_rectangle"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/round_rectangle_outline"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","round_rectangle_outline"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT_OUTLINE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline ELLIPSE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/ellipse"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","ellipse"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","ellipse"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline ARC = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/arc"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","arc"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","arc"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/shadow"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","shadow"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","shadow"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(BASE_SNIPPET)
            .withBindGroupLayout(SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/texture"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","texture"))
            .withVertexBinding(0, LuminVertexFormats.TEXTURE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipelines/triangle"))
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","triangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","triangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();
}
