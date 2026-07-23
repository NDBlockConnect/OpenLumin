package io.github.openlumin.shaders;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

public class GlslSandBox implements AutoCloseable {

    public static final GlslSandBox INSTANCE = new GlslSandBox();

    public static final ResourceLocation SEA_LEVEL = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/sea_level");
    public static final ResourceLocation CLOUDS = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/clouds");
    public static final ResourceLocation ALIEN_TERRAIN = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/alien_terrain");
    public static final ResourceLocation INFERNO = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/inferno");
    public static final ResourceLocation PLANET = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/planet");
    public static final ResourceLocation BLACK_HOLE = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/black_hole");
    public static final ResourceLocation MINECRAFT = ResourceLocation.fromNamespaceAndPath("openlumin", "menu/minecraft");

    private static final int SANDBOX_INFO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private final Map<ResourceLocation, ShaderProgram> pipelines = new HashMap<>();

    private long initTime = System.currentTimeMillis();

    private ShaderProgram getOrCreateShaderProgram(ResourceLocation fragmentShader) {
        return pipelines.computeIfAbsent(fragmentShader, shader -> {
            try {
                // 使用 Minecraft 内置的 screenquad.vsh 和用户提供的片段着色器
                ResourceLocation vertexShader = ResourceLocation.withDefaultNamespace("shaders/core/screenquad.vsh");
                ResourceLocation fragmentPath = ResourceLocation.fromNamespaceAndPath(
                    shader.getNamespace(),
                    "shaders/" + shader.getPath() + ".fsh"
                );

                ShaderProgram program = ShaderProgram.load(
                    vertexShader,
                    fragmentPath,
                    Minecraft.getInstance().getResourceManager()
                );

                // 绑定 uniform block
                program.bindUniformBlock("GlslSandboxInfo", 0);
                return program;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load GLSL sandbox shader: " + shader, e);
            }
        });
    }

    public void resetTime() {
        initTime = System.currentTimeMillis();
    }

    public void render(ResourceLocation fragmentShader, double mouseX, double mouseY) {
        render(fragmentShader, mouseX, mouseY, initTime);
    }

    public void render(ResourceLocation fragmentShader, double mouseX, double mouseY, long startTimeMs) {
        final var activeTarget = LuminRenderSystem.getActiveTarget();
        final int targetWidth = activeTarget != null ? activeTarget.width() : Minecraft.getInstance().getMainRenderTarget().width;
        final int targetHeight = activeTarget != null ? activeTarget.height() : Minecraft.getInstance().getMainRenderTarget().height;
        final int targetFBO = activeTarget == null ? Minecraft.getInstance().getMainRenderTarget().frameBufferId : 0;

        if (targetWidth <= 0 || targetHeight <= 0) return;

        float scaleX = targetWidth / LuminRenderSystem.getScaledWidth();
        float scaleY = targetHeight / LuminRenderSystem.getScaledHeight();

        float mousePxX = (float) mouseX * scaleX;
        float mousePxY = (float) mouseY * scaleY;
        float mouseUvX = mousePxX / targetWidth;
        float mouseUvY = (targetHeight - 1.0f - mousePxY) / targetHeight;
        float elapsedTime = (System.currentTimeMillis() - startTimeMs) / 1000.0f;

        GpuBufferSlice sandboxInfo = LuminRenderSystem.writeDynamicUniform(
                "glsl_sandbox_info",
                "Lumin GLSL Sandbox UBO",
                SANDBOX_INFO_SIZE,
                4,
                new SandboxInfo(targetWidth, targetHeight, elapsedTime, mouseUvX, mouseUvY, mousePxX, mousePxY)
        );

        // 绑定到 framebuffer 进行渲染
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFBO);
        GL11.glViewport(0, 0, targetWidth, targetHeight);

        // 使用 shader
        ShaderProgram program = getOrCreateShaderProgram(fragmentShader);
        program.use();

        // 绑定 UBO
        GL31.glBindBufferRange(
            GL31.GL_UNIFORM_BUFFER,
            0,
            sandboxInfo.buffer().getBufferId(),
            sandboxInfo.offset(),
            sandboxInfo.size()
        );

        // 绘制全屏四边形
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // 清理状态
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void close() {
        pipelines.values().forEach(ShaderProgram::close);
        pipelines.clear();
    }

    private record SandboxInfo(
            float width,
            float height,
            float elapsedTime,
            float mouseUvX,
            float mouseUvY,
            float mousePxX,
            float mousePxY
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(width, height, elapsedTime, 0.0f)
                    .putVec4(mouseUvX, mouseUvY, mousePxX, mousePxY);
        }

    }

}
