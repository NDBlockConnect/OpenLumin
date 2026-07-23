package io.github.openlumin.shaders;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.world.phys.AABB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;

public class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec3()
            .putVec4()
            .putVec4()
            .get();

    private static final int BOX_UNIFORMS_SIZE = new Std140SizeCalculator()
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
                // 加载 blur shader
                ResourceLocation vertexShader = ResourceLocation.fromNamespaceAndPath("openlumin", "shaders/blur.vsh");
                ResourceLocation fragmentShader = ResourceLocation.fromNamespaceAndPath("openlumin", "shaders/blur.fsh");

                this.shaderProgram = ShaderProgram.load(
                    vertexShader,
                    fragmentShader,
                    Minecraft.getInstance().getResourceManager()
                );

                // 绑定 uniform block 和采样器
                shaderProgram.bindUniformBlock("BlurUniforms", 0);
                shaderProgram.setSamplerUnit("InputSampler", 0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load blur shader", e);
            }
        }
    }

    private void ensureInputFBO(int fbWidth, int fbHeight) {
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
                throw new RuntimeException("Failed to create blur input FBO");
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength) {
        if (Minecraft.getInstance().screen != null) return;

        this.ensureProgram();

        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();
        int targetWidth = activeTarget == null ? target.width : activeTarget.width();
        int targetHeight = activeTarget == null ? target.height : activeTarget.height();
        int targetFBO = activeTarget == null ? target.frameBufferId : 0; // TODO: 获取 activeTarget 的 FBO

        if (targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

        this.ensureInputFBO(targetWidth, targetHeight);

        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(x, y, width, height);
        if (!ScissorUtils.isVisible(scissor)) {
            return;
        }

        float scale = (float) LuminRenderSystem.getGuiScale();
        float pxX = x * scale;
        float pxY = targetHeight - (y + height) * scale;
        float pxW = width * scale;
        float pxH = height * scale;

        float rTLPx = Math.max(0.0f, rTL * scale);
        float rTRPx = Math.max(0.0f, rTR * scale);
        float rBRPx = Math.max(0.0f, rBR * scale);
        float rBLPx = Math.max(0.0f, rBL * scale);

        float quality = Math.max(0.0f, blurStrength);

        // 1. 复制 framebuffer 到 inputFBO
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, targetFBO);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, inputFBO);
        GL30.glBlitFramebuffer(
            0, 0, targetWidth, targetHeight,
            0, 0, targetWidth, targetHeight,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
        );

        // 2. 准备 uniform 数据
        GpuBufferSlice blurUniforms = LuminRenderSystem.writeDynamicUniform(
            "blur_uniforms",
            "Lumin Blur UBO",
            UNIFORMS_SIZE,
            16,
            new BlurUniforms(targetWidth, targetHeight, quality, pxW, pxH, pxX, pxY, rTLPx, rTRPx, rBRPx, rBLPx)
        );

        // 3. 绑定到 target 进行渲染
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFBO);
        GL11.glViewport(0, 0, targetWidth, targetHeight);

        // 启用 scissor test
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());

        // 使用 shader
        shaderProgram.use();

        // 绑定 UBO
        GL31.glBindBufferRange(
            GL31.GL_UNIFORM_BUFFER,
            0,
            blurUniforms.buffer().getBufferId(),
            blurUniforms.offset(),
            blurUniforms.size()
        );

        // 绑定输入纹理
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTexture);

        // 启用混合
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 绘制全屏四边形
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // 清理状态
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength);
    }

    public void render3DBox(AABB box, double blurStrength) {
        // 3D box blur 暂不实现，需要顶点缓冲区和索引缓冲区管理
        // TODO: 实现 3D box blur
    }

    private record BlurUniforms(
            float width,
            float height,
            float quality,
            float rectWidth,
            float rectHeight,
            float rectX,
            float rectY,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec3(width, height, quality)
                    .putVec4(rectWidth, rectHeight, rectX, rectY)
                    .putVec4(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        }

    }

    private record BoxBlurUniforms(float width, float height,
                                   float quality) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(width, height, quality, 0.0f);
        }

    }

}
