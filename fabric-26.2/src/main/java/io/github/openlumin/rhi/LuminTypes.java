package io.github.openlumin.rhi;

/**
 * RHI 资源类型容器
 *
 * 原先 LuminTypes.java 合并 14 个 top-level 类型为 package-private，跨包无法 import。
 * v26.0 Alpha 2：拆分为独立 public 文件（LuminBuffer.java / LuminTexture.java / LuminSampler.java /
 * LuminShader.java / LuminFormat.java / LuminFilter.java / LuminAddressMode.java /
 * LuminBufferUsage.java / LuminIndexType.java / LuminPolygonMode.java / LuminCullMode.java /
 * LuminFrontFace.java / LuminCompareOp.java / LuminBlendFactor.java / LuminVertexAttribute.java /
 * LuminVertexFormat.java / LuminPipelineState.java / LuminPipeline.java / LuminBlendState.java /
 * LuminBufferView.java / LuminTextureView.java / LuminRenderPassDesc.java），
 * 遵循"一个 public top-level 类型一个文件"规则。
 */
