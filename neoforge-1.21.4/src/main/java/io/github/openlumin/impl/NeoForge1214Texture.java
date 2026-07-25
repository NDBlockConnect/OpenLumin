package io.github.openlumin.impl;

import io.github.openlumin.api.TextureApi;
import io.github.openlumin.api.TextureHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;

/**
 * NeoForge 1.21.4 纹理实现
 */
public class NeoForge1214Texture implements TextureApi {

    @Override
    public TextureHandle createTexture(int width, int height, TextureFormat format) {
        RenderSystem.assertOnRenderThread();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

        int glFormat = toGlFormat(format);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, glFormat, width, height, 0,
                         glFormat, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return new TextureHandle(id);
    }

    @Override
    public void uploadTexture(TextureHandle handle, int width, int height, ByteBuffer pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, (Integer) handle.nativeHandle());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    @Override
    public void uploadTextureSub(TextureHandle handle, int offsetX, int offsetY,
                                 int width, int height, ByteBuffer pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, (Integer) handle.nativeHandle());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, offsetX, offsetY,
                width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    @Override
    public void bindTexture(TextureHandle handle, int unit) {
        // NeoForge不支持RenderSystem.activeTexture()
        // RenderSystem.activeTexture(GL11.GL_TEXTURE0 + unit);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture((Integer) handle.nativeHandle());
    }

    @Override
    public void setSampler(TextureHandle handle, SamplerDescriptor sampler) {
        RenderSystem.assertOnRenderThread();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, (Integer) handle.nativeHandle());

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                            toGlFilter(sampler.minFilter));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                            toGlFilter(sampler.magFilter));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                            toGlWrap(sampler.wrapS));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                            toGlWrap(sampler.wrapT));

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    @Override
    public void deleteTexture(TextureHandle handle) {
        RenderSystem.assertOnRenderThread();
        GL11.glDeleteTextures((Integer) handle.nativeHandle());
    }

    private int toGlFormat(TextureFormat format) {
        return switch (format) {
            case R8 -> GL30.GL_R8;
            case RG8 -> GL30.GL_RG8;
            case RGB8 -> GL11.GL_RGB8;
            case RGBA8 -> GL11.GL_RGBA8;
            case R16F -> GL30.GL_R16F;
            case RGBA16F -> GL30.GL_RGBA16F;
        };
    }

    private int toGlFilter(FilterMode filter) {
        return switch (filter) {
            case NEAREST -> GL11.GL_NEAREST;
            case LINEAR -> GL11.GL_LINEAR;
            case MIPMAP -> GL11.GL_LINEAR_MIPMAP_LINEAR;
        };
    }

    private int toGlWrap(WrapMode wrap) {
        return switch (wrap) {
            case CLAMP -> io.github.openlumin.shim.com.mojang.blaze3d.opengl.GlStateManager.GL_CLAMP_TO_EDGE;
            case REPEAT -> GL11.GL_REPEAT;
            case MIRROR -> io.github.openlumin.shim.com.mojang.blaze3d.opengl.GlStateManager.GL_MIRRORED_REPEAT;
        };
    }
}
