package io.github.openlumin.immediate;

import io.github.openlumin.LuminPipeline;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.buffer.LuminRingBuffer;
import io.github.openlumin.shaders.ShaderProgram;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.openlumin.shim.net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteOrder;
import net.minecraft.client.Minecraft;

public final class LuminImmediateRenderer {

    private static final Logger LOGGER = LogManager.getLogger("OpenLumin");

    private static final long DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final Channel POS_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS);
    private static final Channel POS_COLOR_TRIANGLE_STRIP = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP);
    private static final Channel POS_COLOR_TRIANGLE_FAN = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN);
    private static final Channel POS_TEX_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
    // NeoForge没有POSITION_COLOR_NORMAL_LINE_WIDTH，使用POSITION_COLOR代替
    private static final Channel POS_COLOR_NORMAL_LINE_WIDTH_LINES = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.LINES);

    private LuminImmediateRenderer() {
    }

    public static PosColorQuads beginPosColorQuads(LuminPipeline pipeline) {
        return new PosColorQuads(POS_COLOR_QUADS.begin(pipeline, null));
    }

    public static PosColorTriangleStrip beginPosColorTriangleStrip(LuminPipeline pipeline) {
        return new PosColorTriangleStrip(POS_COLOR_TRIANGLE_STRIP.begin(pipeline, null));
    }

    public static PosColorTriangleFan beginPosColorTriangleFan(LuminPipeline pipeline) {
        return new PosColorTriangleFan(POS_COLOR_TRIANGLE_FAN.begin(pipeline, null));
    }

    public static PosTexColorQuads beginPosTexColorQuads(LuminPipeline pipeline, ResourceLocation texture) {
        return new PosTexColorQuads(POS_TEX_COLOR_QUADS.begin(pipeline, texture));
    }

    public static Lines beginLines(LuminPipeline pipeline) {
        return new Lines(POS_COLOR_NORMAL_LINE_WIDTH_LINES.begin(pipeline, null));
    }

    public static void endFrame() {
        POS_COLOR_QUADS.endFrame();
        POS_COLOR_TRIANGLE_STRIP.endFrame();
        POS_COLOR_TRIANGLE_FAN.endFrame();
        POS_TEX_COLOR_QUADS.endFrame();
        POS_COLOR_NORMAL_LINE_WIDTH_LINES.endFrame();
    }

    public static final class PosColorQuads {

        private final Channel channel;

        private PosColorQuads(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    public static final class PosColorTriangleStrip {

        private final Channel channel;

        private PosColorTriangleStrip(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }

    }

    public static final class PosColorTriangleFan {

        private final Channel channel;

        private PosColorTriangleFan(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }

    }

    public static final class PosTexColorQuads {

        private final Channel channel;

        private PosTexColorQuads(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, float u, float v, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putUv(u, v);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    public static final class Lines {

        private final Channel channel;
        private final Vector3f normalTmp = new Vector3f();

        private Lines(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz, float width) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);

            pose.transformNormal(nx, ny, nz, this.normalTmp).normalize();
            this.channel.putNormal(this.normalTmp.x, this.normalTmp.y, this.normalTmp.z);
            this.channel.putLineWidth(width);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    private static final class Channel {

        private final LuminRingBuffer ringBuffer;
        private final VertexFormat format;
        private final VertexFormat.Mode mode;
        private final int stride;

        private final int positionOffset;
        private final int colorOffset;
        private final int uvOffset;
        private final int normalOffset;
        private final int lineWidthOffset;

        private final Vector3f posTmp = new Vector3f();

        private boolean building;
        private long currentOffset;
        private long frameOffset;
        private long batchStartOffset;
        private int vertexCount;
        private boolean frameUsed;

        private long vertexBaseAddr;

        private LuminPipeline pipeline;
        @Nullable
        private ResourceLocation texture;

        private Channel(VertexFormat format, VertexFormat.Mode mode) {
            this.ringBuffer = new LuminRingBuffer(DEFAULT_BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);
            this.format = format;
            this.mode = mode;
            this.stride = format.getVertexSize();

            this.positionOffset = resolveOffset(format, VertexFormatElement.POSITION);
            this.colorOffset = resolveOffset(format, VertexFormatElement.COLOR);
            this.uvOffset = resolveOffset(format, VertexFormatElement.UV0);
            this.normalOffset = resolveOffset(format, VertexFormatElement.NORMAL);
            // NeoForge没有LINE_WIDTH元素，设为-1
            this.lineWidthOffset = -1; // resolveOffset(format, VertexFormatElement.LINE_WIDTH);
        }

        private static int resolveOffset(VertexFormat format, VertexFormatElement element) {
            return format.contains(element) ? format.getOffset(element) : -1;
        }

        private Channel begin(LuminPipeline pipeline, @Nullable ResourceLocation texture) {
            if (this.building) {
                throw new IllegalStateException("Immediate channel is already building");
            }
            this.building = true;
            this.currentOffset = this.frameOffset;
            this.batchStartOffset = this.frameOffset;
            this.vertexCount = 0;
            this.pipeline = pipeline;
            this.texture = texture;

            this.ringBuffer.tryMap();
            return this;
        }

        private void putPosition(Matrix4f matrix, float x, float y, float z) {
            if (this.positionOffset < 0 || !ensureCapacity()) {
                return;
            }
            matrix.transformPosition(x, y, z, this.posTmp);
            long p = this.vertexBaseAddr + this.positionOffset;
            MemoryUtil.memPutFloat(p, this.posTmp.x);
            MemoryUtil.memPutFloat(p + 4L, this.posTmp.y);
            MemoryUtil.memPutFloat(p + 8L, this.posTmp.z);
        }

        private void putColor(int color) {
            if (this.colorOffset < 0 || !ensureCapacity()) {
                return;
            }
            int abgr = ARGB.toABGR(color);
            long p = this.vertexBaseAddr + this.colorOffset;
            MemoryUtil.memPutInt(p, LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
        }

        private void putUv(float u, float v) {
            if (this.uvOffset < 0 || !ensureCapacity()) {
                return;
            }
            long p = this.vertexBaseAddr + this.uvOffset;
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }

        private void putNormal(float nx, float ny, float nz) {
            if (this.normalOffset < 0 || !ensureCapacity()) {
                return;
            }
            long p = this.vertexBaseAddr + this.normalOffset;
            MemoryUtil.memPutByte(p, packNormal(nx));
            MemoryUtil.memPutByte(p + 1L, packNormal(ny));
            MemoryUtil.memPutByte(p + 2L, packNormal(nz));
        }

        private void putLineWidth(float width) {
            if (this.lineWidthOffset < 0 || !ensureCapacity()) {
                return;
            }
            MemoryUtil.memPutFloat(this.vertexBaseAddr + this.lineWidthOffset, width);
        }

        private void finishVertex() {
            if (!this.building || this.vertexBaseAddr == 0L) {
                return;
            }
            long completedVertexBaseAddr = this.vertexBaseAddr;
            this.currentOffset += this.stride;
            this.vertexCount++;

            if (this.mode == VertexFormat.Mode.LINES) {
                long duplicateVertexBaseAddr = MemoryUtil.memAddress(this.ringBuffer.getMappedBuffer()) + this.currentOffset;
                MemoryUtil.memCopy(completedVertexBaseAddr, duplicateVertexBaseAddr, this.stride);
                this.currentOffset += this.stride;
                this.vertexCount++;
            }

            this.vertexBaseAddr = 0L;
        }

        private boolean ensureCapacity() {
            if (!this.building) {
                return false;
            }
            if (this.vertexBaseAddr != 0L) {
                return true;
            }
            long requiredBytes = this.mode == VertexFormat.Mode.LINES ? this.stride * 2L : this.stride;
            this.ringBuffer.ensureCapacity(this.currentOffset + requiredBytes);
            if (!this.ringBuffer.isMapped()) {
                this.ringBuffer.tryMap();
            }
            this.vertexBaseAddr = MemoryUtil.memAddress(this.ringBuffer.getMappedBuffer()) + this.currentOffset;
            return true;
        }

        private void drawAndReset() {
            boolean submittedDraw = false;
            long completedOffset = this.currentOffset;

            try {
                if (this.vertexCount <= 0) {
                    return;
                }

                if (this.ringBuffer.isMapped()) {
                    this.ringBuffer.unmap();
                }

                // NeoForge 1.21.4: OpenGL 直接绘制路径不依赖 colorView/depthView，直接绘制
                submittedDraw = drawWithOpenGL();

            } finally {
                if (this.ringBuffer.isMapped()) {
                    this.ringBuffer.unmap();
                }

                if (submittedDraw) {
                    this.frameUsed = true;
                    this.frameOffset = completedOffset;
                }

                this.building = false;
                this.currentOffset = this.frameOffset;
                this.batchStartOffset = this.frameOffset;
                this.vertexCount = 0;
                this.vertexBaseAddr = 0L;
                this.pipeline = null;
                this.texture = null;
            }
        }

        private boolean drawWithOpenGL() {
            // ── 1. Compile / fetch shader from pipeline ──────────────────────────
            io.github.openlumin.shaders.ShaderProgram program =
                LuminRenderSystem.getOrCompileShader(this.pipeline);
            if (program == null) {
                LOGGER.error("[OpenLumin] No shader for pipeline {} — draw skipped",
                    this.pipeline != null ? this.pipeline.getLocation() : "null");
                return false;
            }

            // ── 2. GL state ────────────────────────────────────────────────────────
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // ── 3. VAO setup ───────────────────────────────────────────────────────
            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.ringBuffer.getGpuBuffer().getBufferId());

            // Position (location 0)
            if (this.positionOffset >= 0) {
                GL20.glEnableVertexAttribArray(0);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, this.stride, this.positionOffset);
            }

            // UV (location 1) if present
            int colorAttribLoc;
            if (this.uvOffset >= 0) {
                GL20.glEnableVertexAttribArray(1);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, this.stride, this.uvOffset);
                colorAttribLoc = 2;
            } else {
                colorAttribLoc = 1;
            }

            // Color
            if (this.colorOffset >= 0) {
                GL20.glEnableVertexAttribArray(colorAttribLoc);
                GL20.glVertexAttribPointer(colorAttribLoc, 4, GL11.GL_UNSIGNED_BYTE, true,
                                           this.stride, this.colorOffset);
            }

            // ── 4. Bind shader ─────────────────────────────────────────────────────
            program.use();

            // ── 5. Set ModelViewMat uniform ────────────────────────────────────────
            // 顶点数据已在 CPU 端用 matrix.transformPosition 预变换（view-space），
            // shader 中不能再乘一次 MV，否则双重变换。传单位矩阵给 ModelViewMat。
            int mvLoc = program.getUniformLocation("ModelViewMat");
            if (mvLoc >= 0) {
                float[] identity = { 1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1 };
                GL20.glUniformMatrix4fv(mvLoc, false, identity);
            }

            // ── 6. Set ProjMat uniform ─────────────────────────────────────────────
            int projLoc = program.getUniformLocation("ProjMat");
            if (projLoc >= 0) {
                float[] projArr = new float[16];
                RenderSystem.getProjectionMatrix().get(projArr);
                GL20.glUniformMatrix4fv(projLoc, false, projArr);
            }

            // ── 7. Bind texture if needed ──────────────────────────────────────────
            if (this.texture != null) {
                AbstractTexture textureObject = Minecraft.getInstance().getTextureManager().getTexture(this.texture);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureObject.getId());
                program.setSamplerUnit("Sampler0", 0);
            }

            // ── 8. Draw ────────────────────────────────────────────────────────────
            boolean drawn = false;
            int firstVertex = Math.toIntExact(this.batchStartOffset / this.stride);

            switch (this.mode) {
                case QUADS -> {
                    int indexCount = this.mode.indexCount(this.vertexCount);
                    if (indexCount > 0) {
                        // batchStartOffset 是当前批次在 VBO 中的字节偏移。
                        // IBO 索引从 0 开始，必须用 glDrawElementsBaseVertex 把索引偏移到正确的顶点起点。
                        int baseVertex = Math.toIntExact(this.batchStartOffset / this.stride);
                        io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(indexCount);
                        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo.getBufferId());
                        GL32.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_SHORT, 0L, baseVertex);
                        drawn = true;
                    }
                }
                case LINES -> {
                    GL11.glDrawArrays(GL11.GL_LINES, firstVertex, this.vertexCount);
                    drawn = true;
                }
                case TRIANGLE_STRIP -> {
                    GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, firstVertex, this.vertexCount);
                    drawn = true;
                }
                case TRIANGLE_FAN -> {
                    GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, firstVertex, this.vertexCount);
                    drawn = true;
                }
                default -> {
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, firstVertex, this.vertexCount);
                    drawn = true;
                }
            }

            // ── 9. Cleanup ─────────────────────────────────────────────────────────
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL30.glDeleteVertexArrays(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            return drawn;
        }

        private void endFrame() {
            if (this.ringBuffer.isMapped()) {
                this.ringBuffer.unmap();
            }

            if (this.frameUsed) {
                this.ringBuffer.rotate();
            }

            this.frameUsed = false;
            this.frameOffset = 0L;
            this.currentOffset = 0L;
            this.batchStartOffset = 0L;
        }

        private static byte packNormal(float value) {
            float clamped = Mth.clamp(value, -1.0f, 1.0f);
            return (byte) ((int) (clamped * 127.0f) & 0xFF);
        }
    }

}
