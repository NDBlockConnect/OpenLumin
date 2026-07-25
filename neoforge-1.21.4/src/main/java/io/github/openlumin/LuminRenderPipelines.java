package io.github.openlumin;

import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * OpenLumin 渲染管线常量表。
 *
 * <p>全部使用 {@link LuminPipeline} 而非 MC 的 RenderPipeline，
 * 因为 MC 1.21.4 运行时不含 RenderPipeline 类，直接引用会导致
 * {@code <clinit>} 阶段 {@link NoClassDefFoundError}。</p>
 */
public class LuminRenderPipelines {

    public static final LuminPipeline RECTANGLE = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/rectangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "rectangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "rectangle"))
            .build();

    public static final LuminPipeline TTF_FONT_AA = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/ttf_font_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ttf_font"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ttf_font_aa"))
            .build();

    public static final LuminPipeline TTF_FONT_NO_AA = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/ttf_font_no_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ttf_font"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ttf_font_no_aa"))
            .build();

    public static final LuminPipeline ROUND_RECT = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/round_rectangle"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "round_rectangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "round_rectangle"))
            .build();

    public static final LuminPipeline ROUND_RECT_OUTLINE = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/round_rectangle_outline"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT_OUTLINE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "round_rectangle_outline"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "round_rectangle_outline"))
            .build();

    public static final LuminPipeline ELLIPSE = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/ellipse"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ellipse"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "ellipse"))
            .build();

    public static final LuminPipeline ARC = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/arc"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "arc"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "arc"))
            .build();

    public static final LuminPipeline SHADOW = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/shadow"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "shadow"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "shadow"))
            .build();

    public static final LuminPipeline TEXTURE = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/texture"))
            .withVertexFormat(LuminVertexFormats.TEXTURE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "texture"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "texture"))
            .build();

    public static final LuminPipeline TRIANGLE = LuminPipeline
            .builder(ResourceLocation.fromNamespaceAndPath("openlumin", "pipelines/triangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("openlumin", "triangle"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("openlumin", "triangle"))
            .build();

}
