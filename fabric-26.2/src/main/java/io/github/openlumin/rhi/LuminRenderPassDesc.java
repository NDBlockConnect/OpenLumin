package io.github.openlumin.rhi;

/** 渲染通道描述（多 color + depth） */
public record LuminRenderPassDesc(
        LuminTextureView colorAttachment,
        LuminTextureView depthAttachment,
        int width,
        int height
) {}
