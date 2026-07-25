package com.mojang.blaze3d.platform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL33;

/**
 * NeoForge 实现：OpenGL 采样器对象
 *
 * 注意：OpenGL 3.3+ 支持独立的采样器对象，但也可以将采样器参数直接设置到纹理上。
 * 这里使用独立采样器对象以匹配 Fabric 的 API。
 */
public class GpuSampler {

    public static final int FILTER_NEAREST = 0;
    public static final int FILTER_LINEAR = 1;
    public static final int WRAP_CLAMP_TO_EDGE = 0;
    public static final int WRAP_REPEAT = 1;
    public static final int WRAP_MIRRORED_REPEAT = 2;

    private final int minFilter;
    private final int magFilter;
    private final int wrapS;
    private final int wrapT;
    private final int samplerId;
    private boolean closed = false;

    public GpuSampler(int minFilter, int magFilter, int wrapS, int wrapT) {
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.wrapS = wrapS;
        this.wrapT = wrapT;

        // 创建 OpenGL 采样器对象（需要 OpenGL 3.3+）
        this.samplerId = GL33.glGenSamplers();

        // 设置过滤模式
        int glMinFilter = toGLFilter(minFilter);
        int glMagFilter = toGLFilter(magFilter);
        GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_MIN_FILTER, glMinFilter);
        GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_MAG_FILTER, glMagFilter);

        // 设置包裹模式
        int glWrapS = toGLWrap(wrapS);
        int glWrapT = toGLWrap(wrapT);
        GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_WRAP_S, glWrapS);
        GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_WRAP_T, glWrapT);
    }

    public GpuSampler() {
        this(FILTER_NEAREST, FILTER_NEAREST, WRAP_CLAMP_TO_EDGE, WRAP_CLAMP_TO_EDGE);
    }

    public int minFilter() {
        return minFilter;
    }

    public int magFilter() {
        return magFilter;
    }

    public int wrapS() {
        return wrapS;
    }

    public int wrapT() {
        return wrapT;
    }

    public int getSamplerId() {
        return samplerId;
    }

    /**
     * 绑定采样器到指定的纹理单元
     */
    public void bind(int textureUnit) {
        if (closed) {
            throw new IllegalStateException("Sampler has been closed");
        }
        GL33.glBindSampler(textureUnit, samplerId);
    }

    private int toGLFilter(int filter) {
        return switch (filter) {
            case FILTER_NEAREST -> GL11.GL_NEAREST;
            case FILTER_LINEAR -> GL11.GL_LINEAR;
            default -> GL11.GL_NEAREST;
        };
    }

    private int toGLWrap(int wrap) {
        return switch (wrap) {
            case WRAP_CLAMP_TO_EDGE -> GL13.GL_CLAMP_TO_EDGE;
            case WRAP_REPEAT -> GL11.GL_REPEAT;
            case WRAP_MIRRORED_REPEAT -> GL14.GL_MIRRORED_REPEAT;
            default -> GL13.GL_CLAMP_TO_EDGE;
        };
    }

    public void close() {
        if (!closed) {
            GL33.glDeleteSamplers(samplerId);
            closed = true;
        }
    }
}
