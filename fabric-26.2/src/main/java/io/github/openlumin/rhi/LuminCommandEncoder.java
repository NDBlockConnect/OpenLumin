package io.github.openlumin.rhi;

import java.io.Closeable;

/**
 * 命令录制器 → 命令缓冲
 *
 * 录制器开始一个 render pass / compute pass，录制 draw/drawIndexed/setUniformBuffer 等调用，
 * finish() 返回 LuminCommandBuffer 提交到 LuminRHI.submit()。
 *
 * draw/drawIndexed 的参数顺序遵循 MC 26.2 真实语义：
 *   draw(vertexCount, instanceCount, firstVertex, firstInstance)
 *   drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
 * 详情见 docs/RHI_DESIGN.md（drawIndexed 参数序验实）。
 */
public interface LuminCommandEncoder extends Closeable {

    /**
     * 开始渲染通道
     */
    LuminRenderPass beginRenderPass(LuminRenderPassDesc desc);

    /**
     * 上传数据到缓冲（一次性）
     */
    LuminBuffer uploadBuffer(LuminBufferUsage usage, java.nio.ByteBuffer data);

    /**
     * 上传像素到纹理
     */
    LuminTexture uploadTexture2D(int width, int height, LuminFormat format, java.nio.ByteBuffer pixels);

    /**
     * 完成录制，返回提交缓冲
     */
    LuminCommandBuffer finish();
}

