package com.mojang.blaze3d.platform;

/**
 * NeoForge 实现：纹理视图（OpenGL 中直接引用纹理对象）
 */
public class GpuTextureView implements AutoCloseable {

    private final GpuTexture texture;

    public GpuTextureView(GpuTexture texture) {
        this.texture = texture;
    }

    public GpuTextureView() {
        this(null);
    }

    public GpuTexture texture() {
        return texture;
    }

    /**
     * 获取纹理 ID（用于 OpenGL 绑定）
     */
    public int getTextureId() {
        return texture != null ? texture.getId() : 0;
    }

    @Override
    public void close() {
        // View 不拥有纹理，不需要释放
    }
}
