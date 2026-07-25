package io.github.openlumin.renderers;

import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.buffer.LuminRingBuffer;
import io.github.openlumin.holders.RendererHolder;
import io.github.openlumin.shaders.ShaderProgram;
import io.github.openlumin.utils.render.ScissorUtils;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.IOException;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RoundRectRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 48;
    private static final long RECT_BYTES = STRIDE * 4L;
    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private static ShaderProgram roundRectShader = null;

    private RoundRectRenderer() {
    }

    private static ShaderProgram getRoundRectShader() {
        if (roundRectShader == null) {
            try {
                ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                roundRectShader = ShaderProgram.load(
                    ResourceLocation.fromNamespaceAndPath("openlumin", "shaders/round_rectangle.vsh"),
                    ResourceLocation.fromNamespaceAndPath("openlumin", "shaders/round_rectangle.fsh"),
                    resourceManager
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to load round rectangle shader", e);
            }
        }
        return roundRectShader;
    }

    public static RoundRectRenderer create() {
        return RendererHolder.INSTANCE.register(new RoundRectRenderer());
    }

    public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
        addRoundRect(x, y, width, height, radius, radius, radius, radius, color);
    }

    public void addRoundRect(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color color) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, color, color, color, color);
    }

    public void addVerticalGradient(float x, float y, float width, float height, float radius, Color top, Color bottom) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, top, bottom, bottom, top);
    }

    public void addVerticalGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color top, Color bottom) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, top, bottom, bottom, top);
    }

    public void addHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, left, left, right, right);
    }

    public void addHorizontalGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color left, Color right) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, left, left, right, right);
    }

    /**
     * 颜色顺序对应四个角顶点：左上、左下、右下、右上 (TL, BL, BR, TR)
     */
    public void addRoundRectGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color cTL, Color cBL, Color cBR, Color cTR) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES);
        buffer.tryMap();
        float x2 = x + width, y2 = y + height;
        int argbTL = ARGB.toABGR(cTL.getRGB());
        int argbBL = ARGB.toABGR(cBL.getRGB());
        int argbBR = ARGB.toABGR(cBR.getRGB());
        int argbTR = ARGB.toABGR(cTR.getRGB());

        addVertex(x, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTL);
        addVertex(x, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBL);
        addVertex(x2, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBR);
        addVertex(x2, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTR);
    }

    private void addVertex(float vx, float vy, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;
        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutInt(p + 12, color);
        MemoryUtil.memPutFloat(p + 16, rx1);
        MemoryUtil.memPutFloat(p + 20, ry1);
        MemoryUtil.memPutFloat(p + 24, rx2);
        MemoryUtil.memPutFloat(p + 28, ry2);
        MemoryUtil.memPutFloat(p + 32, r1);
        MemoryUtil.memPutFloat(p + 36, r2);
        MemoryUtil.memPutFloat(p + 40, r3);
        MemoryUtil.memPutFloat(p + 44, r4);
        currentOffset += STRIDE;
        vertexCount++;
    }

    @Override
    public void draw() {
        if (vertexCount == 0) return;
        if (buffer.isMapped()) buffer.unmap();

        LuminRenderSystem.QuadRenderingInfo info = LuminRenderSystem.prepareQuadRendering(vertexCount);
        if (info == null || info.colorView() == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        // NeoForge实现：使用直接OpenGL调用
        drawWithOpenGL(info);
    }

    private void drawWithOpenGL(LuminRenderSystem.QuadRenderingInfo info) {
        ShaderProgram shader = getRoundRectShader();
        shader.use();

        // 上传 ProjMat uniform（正交投影矩阵）
        int projLoc = shader.getUniformLocation("ProjMat");
        if (projLoc >= 0) {
            float[] projArr = new float[16];
            RenderSystem.getProjectionMatrix().get(projArr);
            GL20.glUniformMatrix4fv(projLoc, false, projArr);
        }

        // 上传 ModelViewMat uniform（模型视图矩阵）
        int mvLoc = shader.getUniformLocation("ModelViewMat");
        if (mvLoc >= 0) {
            float[] mvArr = new float[16];
            RenderSystem.getModelViewMatrix().get(mvArr);
            GL20.glUniformMatrix4fv(mvLoc, false, mvArr);
        }

        // 启用混合模式
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 设置scissor test
        if (scissorEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        // 绑定UBO (DynamicTransforms)
        if (info.dynamicUniforms() != null && info.dynamicUniforms().buffer() != null) {
            GL31.glBindBufferRange(
                GL31.GL_UNIFORM_BUFFER,
                0,
                info.dynamicUniforms().buffer().getBufferId(),
                info.dynamicUniforms().offset(),
                info.dynamicUniforms().size()
            );
        }

        // 创建并配置VAO
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // 绑定vertex buffer
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer.getGpuBuffer().getBufferId());

        // 顶点格式：Position(vec3) + Color(vec4) + InnerRect(vec4) + Radius(vec4)
        // Position: location 0, offset 0, 3 floats
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0);

        // Color: location 1, offset 12, 4 unsigned bytes
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 12);

        // InnerRect: location 2, offset 16, 4 floats
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, STRIDE, 16);

        // Radius: location 3, offset 32, 4 floats
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, STRIDE, 32);

        // 绑定index buffer
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, info.ibo().getBufferId());

        // 执行绘制
        int indexType = info.indexType() == com.mojang.blaze3d.vertex.VertexFormat.IndexType.INT
            ? GL11.GL_UNSIGNED_INT : GL11.GL_UNSIGNED_SHORT;
        GL11.glDrawElements(GL11.GL_TRIANGLES, info.indexCount(), indexType, 0);

        // 清理
        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        if (scissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedInfo = null;
        if (vertexCount == 0) return false;
        if (buffer.isMapped()) buffer.unmap();
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedInfo = LuminRenderSystem.prepareQuadRendering(vertexCount, false);
        return sharedInfo != null && sharedInfo.colorView() != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedInfo == null) return;
        pass.setUniform("DynamicTransforms", sharedInfo.dynamicUniforms());
        drawPrepared(pass, sharedInfo);
    }

    private void drawPrepared(RenderPass pass, LuminRenderSystem.QuadRenderingInfo info) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        pass.setVertexBuffer(0, buffer.getGpuBuffer());
        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount()), LuminRenderSystem.getQuadIndexType());
        pass.drawIndexed(0, 0, info.indexCount(), 1);
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap();
            buffer.rotate();
        }
        vertexCount = 0;
        currentOffset = 0;
        sharedInfo = null;
    }

    @Override
    public void close() {
        buffer.close();
        RendererHolder.INSTANCE.unregister(this);
    }

    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    public void clearScissor() {
        scissorEnabled = false;
    }

}
