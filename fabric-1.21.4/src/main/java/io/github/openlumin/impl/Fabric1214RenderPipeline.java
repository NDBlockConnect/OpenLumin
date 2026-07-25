package io.github.openlumin.impl;

import io.github.openlumin.api.RenderPipelineApi;
import io.github.openlumin.api.RenderPipelineHandle;
import org.lwjgl.opengl.GL11;

/**
 * Fabric 1.21.4 渲染管线实现。
 * <p>
 * 1.21.4 版本 Mojang 引入了新的 RenderPipeline API，用户可以直接使用 LuminRenderPipelines。
 * 这里提供最简 OpenGL 版本用于 API 完整性，实际内部渲染使用 Mojang 高层管线。
 */
public class Fabric1214RenderPipeline implements RenderPipelineApi {

    @Override
    public RenderPipelineHandle createPipeline(PipelineDescriptor descriptor) {
        // 通用 OpenGL 层无法直接对应 Mojang 高层管线，返回占位句柄
        return new RenderPipelineHandle(new Object());
    }

    @Override
    public void usePipeline(RenderPipelineHandle handle) {
        // 由 Mojang 内部处理，不作抽象层操作
    }

    @Override
    public void setUniform(String name, Object value) {
        // Uniform 通过 LuminRenderSystem.writeDynamicUniform 提交，不在此实现
    }

    @Override
    public void draw(DrawCommand command) {
        int mode = switch (command.primitiveType()) {
            case POINTS -> GL11.GL_POINTS;
            case LINES -> GL11.GL_LINES;
            case LINE_STRIP -> GL11.GL_LINE_STRIP;
            case TRIANGLES -> GL11.GL_TRIANGLES;
            case TRIANGLE_STRIP -> GL11.GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> GL11.GL_TRIANGLE_FAN;
        };
        GL11.glDrawArrays(mode, command.firstVertex(), command.vertexCount());
    }

    @Override
    public void deletePipeline(RenderPipelineHandle handle) {
        // 占位句柄不持有 GL 资源，无需释放
    }
}
