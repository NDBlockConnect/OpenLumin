package io.github.openlumin.renderers;

import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.buffer.LuminRingBuffer;
import io.github.openlumin.holders.RendererHolder;
import io.github.openlumin.utils.render.ScissorUtils;
import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.systems.RenderPass;
import io.github.openlumin.shim.com.mojang.blaze3d.systems.RenderSystemExtensions;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 椭圆渲染器。
 * <p>
 * 复用 ROUND_RECT 顶点格式（Position + Color + InnerRect + Radius），
 * 但走 ELLIPSE 管线，片段着色器用真椭圆 SDF 求值。
 * 半轴由 InnerRect 包围盒推导，Radius 字段在纯椭圆中填 0（留给 Arc 扩展）。
 */
public class EllipseRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 48;
    private static final long RECT_BYTES = STRIDE * 4L;
    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private EllipseRenderer() {
    }

    public static EllipseRenderer create() {
        return RendererHolder.INSTANCE.register(new EllipseRenderer());
    }

    public void addEllipse(float x, float y, float width, float height, Color color) {
        addEllipseGradient(x, y, width, height, color, color, color, color);
    }

    public void addCircle(float centerX, float centerY, float radius, Color color) {
        addEllipse(centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f, color);
    }

    /**
     * 颜色顺序对应包围盒四角顶点：左上、左下、右下、右上 (TL, BL, BR, TR)
     */
    public void addEllipseGradient(float x, float y, float width, float height, Color cTL, Color cBL, Color cBR, Color cTR) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES);
        buffer.tryMap();
        float x2 = x + width, y2 = y + height;
        int argbTL = ARGB.toABGR(cTL.getRGB());
        int argbBL = ARGB.toABGR(cBL.getRGB());
        int argbBR = ARGB.toABGR(cBR.getRGB());
        int argbTR = ARGB.toABGR(cTR.getRGB());

        addVertex(x, y, x, y, x2, y2, argbTL);
        addVertex(x, y2, x, y, x2, y2, argbBL);
        addVertex(x2, y2, x, y, x2, y2, argbBR);
        addVertex(x2, y, x, y, x2, y2, argbTR);
    }

    private void addVertex(float vx, float vy, float rx1, float ry1, float rx2, float ry2, int color) {
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
        // Radius 字段：纯椭圆填 0，留给 Arc 扩展（起止角/内径比）
        MemoryUtil.memPutFloat(p + 32, 0.0f);
        MemoryUtil.memPutFloat(p + 36, 0.0f);
        MemoryUtil.memPutFloat(p + 40, 0.0f);
        MemoryUtil.memPutFloat(p + 44, 0.0f);
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

        try (RenderPass pass = RenderSystemExtensions.getDevice().createCommandEncoder().createRenderPass(
                () -> "Ellipse Draw", info.colorView(), OptionalInt.empty(),
                info.depthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(null); // NeoForge 1.21.4: RenderPass 为 stub，此调用无效，LuminPipeline 不兼容旧 RenderPipeline 类型
            if (scissorEnabled) ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            pass.setUniform("DynamicTransforms", info.dynamicUniforms());
            drawPrepared(pass, info);
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
