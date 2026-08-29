package io.github.openlumin.rhi;

/** 渲染通道（录制作用域） */
public interface LuminRenderPass extends AutoCloseable {
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
