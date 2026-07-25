package com.mojang.blaze3d.platform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * NeoForge 实现：真正的 OpenGL 纹理对象
 */
public class GpuTexture {

    public static final int USAGE_TEXTURE_BINDING = 0x01;
    public static final int USAGE_RENDER_ATTACHMENT = 0x02;
    public static final int USAGE_COPY_DST = 0x04;
    public static final int USAGE_COPY_SRC = 0x08;

    private final int width;
    private final int height;
    private final int format;
    private final int textureId;
    private boolean closed = false;

    /**
     * 创建新的 GPU 纹理
     *
     * @param width  纹理宽度
     * @param height 纹理高度
     * @param format OpenGL 内部格式（如 GL_RGBA8）
     */
    public GpuTexture(int width, int height, int format) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.textureId = GL11.glGenTextures();

        // 绑定并初始化纹理
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        // 设置默认过滤模式
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // 设置默认包裹模式
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);

        // 分配纹理存储（如果指定了格式）
        if (format != 0) {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                format,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
            );
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public GpuTexture(int width, int height) {
        this(width, height, GL11.GL_RGBA8);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int format() {
        return format;
    }

    public int getId() {
        return textureId;
    }

    public GpuTextureView createView() {
        return new GpuTextureView(this);
    }

    /**
     * 上传数据到纹理
     */
    public void upload(int level, int xOffset, int yOffset, int width, int height, byte[] data) {
        if (closed) {
            throw new IllegalStateException("Texture has been closed");
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data);
            buffer.flip();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                level,
                xOffset,
                yOffset,
                width,
                height,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer
            );
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * 绑定纹理到指定的纹理单元
     */
    public void bind(int textureUnit) {
        if (closed) {
            throw new IllegalStateException("Texture has been closed");
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    public void close() {
        if (!closed) {
            GL11.glDeleteTextures(textureId);
            closed = true;
        }
    }
}
