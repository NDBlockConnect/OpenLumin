package io.github.openlumin.impl;

import io.github.openlumin.api.RenderPipelineApi;
import io.github.openlumin.api.RenderPipelineHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * NeoForge 1.21.4 渲染管线实现
 */
public class NeoForge1214RenderPipeline implements RenderPipelineApi {

    @Override
    public RenderPipelineHandle createPipeline(PipelineDescriptor descriptor) {
        // NeoForge使用不同的API，返回占位符
        return new RenderPipelineHandle(new Object());
    }

    @Override
    public void usePipeline(RenderPipelineHandle handle) {
        // NeoForge使用不同的API
    }

    @Override
    public void setUniform(String name, Object value) {
        // NeoForge使用不同的API
    }

    @Override
    public void draw(DrawCommand command) {
        // NeoForge使用不同的API
        switch (command.primitiveType()) {
            case TRIANGLES -> GL11.glDrawArrays(GL11.GL_TRIANGLES, command.firstVertex(), command.vertexCount());
            case LINES -> GL11.glDrawArrays(GL11.GL_LINES, command.firstVertex(), command.vertexCount());
            case POINTS -> GL11.glDrawArrays(GL11.GL_POINTS, command.firstVertex(), command.vertexCount());
            default -> {}
        }
    }

    @Override
    public void deletePipeline(RenderPipelineHandle handle) {
        // NeoForge使用不同的API
    }
}
