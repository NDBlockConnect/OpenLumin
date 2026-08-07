package io.github.openlumin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * fabric-26.2 适配：26.2 的顶点格式改为字符串属性 + GpuFormat，
 * 元素注册机制移除，自定义格式直接用 builder 构建。
 * 内置 LINE_WIDTH 属性名经 DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME 提供。
 */
public class LuminVertexFormats {

    public static final VertexFormat ROUND_RECT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build();

    public static final VertexFormat ROUND_RECT_OUTLINE = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .addAttribute("OutlineWidth", GpuFormat.R32_FLOAT)
            .build();

    public static final VertexFormat TEXTURE = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build();

    public static final VertexFormat POSITION_COLOR_NORMAL_LINE_WIDTH = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build();
}
