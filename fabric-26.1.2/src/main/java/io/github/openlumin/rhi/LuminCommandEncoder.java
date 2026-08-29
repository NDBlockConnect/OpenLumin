package io.github.openlumin.rhi;

import java.io.Closeable;

/**
 * 命令录制器 → 命令缓冲
 *
 * 录制器开始一个 render pass / compute pass，录制 draw/drawIndexed/setUniformBuffer 等调用，
 * finish() 返回 LuminCommandBuffer 提交到 LuminRHI.submit()。
 *
 * draw/drawIndexed 的参数顺序遵循 MC 26.2 真实语义：
 *   draw(indexCount, instanceCount, firstIndex, baseVertex, baseInstance)
 *   draw(vertexCount, instanceCount, firstVertex, baseInstance)
 * 详情见 docs/26.2-RHI.md（drawIndexed 参数序验实）。
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

/** 渲染通道描述（多 color + depth） */
record LuminRenderPassDesc(
        LuminTextureView colorAttachment,
        LuminTextureView depthAttachment,
        int width,
        int height
) {}

/** 提交命令缓冲 */
interface LuminCommandBuffer extends Closeable {
    int drawCallCount();
    @Override void close();
}

/** 渲染通道（录制作用域） */
interface LuminRenderPass extends AutoCloseable {
    void setPipeline(LuminPipeline pipeline);
    void setVertexBuffer(int slot, LuminBufferView view);
    void setIndexBuffer(LuminBuffer buffer, LuminIndexType type);
    void setUniformBuffer(int slot, String name, LuminBufferView view);
    void setTexture(int slot, String name, LuminTextureView view, LuminSampler sampler);
    void setFramebufferSize(int width, int height);

    // draw 参数按真实语义（26.2 实证）
    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance);
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);

    @Override void close();
}
