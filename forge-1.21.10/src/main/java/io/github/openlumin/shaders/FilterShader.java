package io.github.openlumin.shaders;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;

public class FilterShader {

    public static final FilterShader INSTANCE = new FilterShader();

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private ShaderProgram shaderProgram;
    private int inputFBO;
    private int inputTexture;
    private int inputWidth;
    private int inputHeight;

    private void ensureProgram() {
        if (this.shaderProgram == null) {
            try {
                // 加载 shader（使用 Minecraft 内置的 screenquad.vsh 和自定义的 filter.fsh）
                ResourceLocation vertexShader = ResourceLocation.withDefaultNamespace("shaders/core/screenquad.vsh");
                ResourceLocation fragmentShader = ResourceLocation.fromNamespaceAndPath("openlumin", "shaders/filter.fsh");

                this.shaderProgram = ShaderProgram.load(
                    vertexShader,
                    fragmentShader,
                    Minecraft.getInstance().getResourceManager()
                );

                // 绑定 uniform block 和采样器
                shaderProgram.bindUniformBlock("FilterColor", 0);
                shaderProgram.setSamplerUnit("InputSampler", 0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load filter shader", e);
            }
        }
    }

    private void ensureInputFBO(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (inputFBO == 0 || inputWidth != fbWidth || inputHeight != fbHeight) {
            // 清理旧资源
            if (inputFBO != 0) {
                GL30.glDeleteFramebuffers(inputFBO);
                GL11.glDeleteTextures(inputTexture);
            }

            // 创建新的 FBO 和纹理
            inputWidth = fbWidth;
            inputHeight = fbHeight;

            inputTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fbWidth, fbHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);

            inputFBO = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, inputFBO);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, inputTexture, 0);

            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Failed to create filter input FBO");
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    public void renderToMainTarget(Color color) {
        render(Minecraft.getInstance().getMainRenderTarget(), color);
    }

    public void render(RenderTarget framebuffer, Color color) {
        this.ensureProgram();

        if (framebuffer == null || color == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return;
        }

        this.ensureInputFBO(framebuffer);

        // 1. 复制 framebuffer 内容到 inputFBO
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, inputFBO);
        GL30.glBlitFramebuffer(
            0, 0, framebuffer.width, framebuffer.height,
            0, 0, framebuffer.width, framebuffer.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
        );

        // 2. 准备 uniform 数据
        GpuBufferSlice filterColor = LuminRenderSystem.writeDynamicUniform(
            "filter_color",
            "Epsilon Filter UBO",
            UNIFORMS_SIZE,
            4,
            new FilterColor(color)
        );

        // 3. 绑定到 framebuffer 进行渲染
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.frameBufferId);
        GL11.glViewport(0, 0, framebuffer.width, framebuffer.height);

        // 使用 shader
        shaderProgram.use();

        // 绑定 UBO
        GL31.glBindBufferRange(
            GL31.GL_UNIFORM_BUFFER,
            0,
            filterColor.buffer().getBufferId(),
            filterColor.offset(),
            filterColor.size()
        );

        // 绑定输入纹理
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTexture);

        // 绘制全屏四边形
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // 清理状态
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private record FilterColor(float red, float green, float blue,
                               float alpha) implements DynamicUniformStorage.DynamicUniform {

        private FilterColor(Color color) {
            this(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(red, green, blue, alpha);
        }

    }

}
