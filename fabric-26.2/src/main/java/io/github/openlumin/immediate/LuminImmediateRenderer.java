package io.github.openlumin.immediate;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.LuminVertexFormats;
import io.github.openlumin.buffer.LuminRingBuffer;
import io.github.openlumin.platform.PlatformRegistry;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.Optional;

import net.minecraft.client.Minecraft;

/**
 * 闂佸憡顨夊▍鏇烆渻閸屾鐔煎灳瀹曞洨顢呭┑鐐存尭瀵爼鎮＄€ｎ喖闂?- OpenLumin 闂佸搫绉堕…鍫㈢紦閸撗呯＜闁告洦浜濋浠嬫煥?6.2 Vulkan 闂佺硶鏅涢幖顐よ姳閹惰姤鏅璺虹墐閸?
 *
 * 婵?26.1 闂佺硶鏅炲▍锝夊吹鎼淬劍鍎嶉柛鏇ㄥ亝閳绘洜鈧鍠栭崑濠勬?
 * - 闂佹悶鍎查崕鎶藉储閹惧墎灏甸悹鍥皺閳ь剛鍏橀幃?PrimitiveTopology闂佹寧绋掔粙鎾诲磼閵娿儺鍤曢柡鍥╁Ь椤箓鏌涢妸銉剳闁轰降鍊栭妵鍕崉閾忚鍕?IndexType
 * - setVertexBuffer 缂傚倷鐒﹂崹鐢告偩妤ｅ啫鏋侀悗闈涙憸婢跺嫰鏌?slice闂佹寧绋掗惌鐒wIndexed 婵?5 闂佸憡鐟ラ崑濠勬濞嗘垹绠旀い鎴ｆ硶鐎瑰鏌?instanceCount闂佹寧绋戦惉鑲┾偓鐟扮－閳ь剝顫夐惌顔炬嫻?1闂佹寧绋戦懟顖滄閻斿憡浜ら柟閭﹀灱閺€浠嬫煛閸愩劎鍩ｉ柣娑欑懅閹风姵绗熸繝鍕槴
 * - 闂佸搫瀚慨鎾儍閻樼數纾?GpuBuffer.map闂佹寧绋掔粙鎺旂博閼姐倓娌煫鍥ㄤ緱閺夊綊鏌ょ涵鍛处閻?Vector4fc
 */
public final class LuminImmediateRenderer {

    private static boolean loggedDrawDiag;

    private static final long DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final Channel POS_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.QUADS);
    private static final Channel POS_COLOR_TRIANGLE_STRIP = new Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_STRIP);
    private static final Channel POS_COLOR_TRIANGLE_FAN = new Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_FAN);
    private static final Channel POS_TEX_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS);
    private static final Channel POS_COLOR_NORMAL_LINES = new Channel(LuminVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, PrimitiveTopology.LINES);

    private LuminImmediateRenderer() {}

    public static PosColorQuads beginPosColorQuads(RenderPipeline pipeline, Matrix4f dynamicModelView) {
        return new PosColorQuads(POS_COLOR_QUADS.begin(pipeline, null, dynamicModelView));
    }

    public static PosColorQuads beginPosColorQuads(RenderPipeline pipeline) {
        return new PosColorQuads(POS_COLOR_QUADS.begin(pipeline, null));
    }

    public static PosColorTriangleStrip beginPosColorTriangleStrip(RenderPipeline pipeline) {
        return new PosColorTriangleStrip(POS_COLOR_TRIANGLE_STRIP.begin(pipeline, null));
    }

    public static PosColorTriangleFan beginPosColorTriangleFan(RenderPipeline pipeline) {
        return new PosColorTriangleFan(POS_COLOR_TRIANGLE_FAN.begin(pipeline, null));
    }

    public static PosTexColorQuads beginPosTexColorQuads(RenderPipeline pipeline, Identifier texture) {
        return new PosTexColorQuads(POS_TEX_COLOR_QUADS.begin(pipeline, texture));
    }

    public static Lines beginLines(RenderPipeline pipeline) {
        return beginLines(pipeline, LuminRenderSystem.getModelViewMatrix());
    }

    public static Lines beginLines(RenderPipeline pipeline, Matrix4f dynamicModelView) {
        return beginLines(pipeline, dynamicModelView, 1.0f);
    }

    public static Lines beginLines(RenderPipeline pipeline, Matrix4f dynamicModelView, float lineWidth) {
        return new Lines(POS_COLOR_NORMAL_LINES.begin(pipeline, null, dynamicModelView, lineWidth));
    }

    public static void endFrame() {
        POS_COLOR_QUADS.endFrame();
        POS_COLOR_TRIANGLE_STRIP.endFrame();
        POS_COLOR_TRIANGLE_FAN.endFrame();
        POS_TEX_COLOR_QUADS.endFrame();
        POS_COLOR_NORMAL_LINES.endFrame();
    }

    public static final class PosColorQuads {
        private final Channel channel;
        private PosColorQuads(Channel channel) { this.channel = channel; }
        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }
        public void end() { this.channel.drawAndReset(); }
    }

    public static final class PosColorTriangleStrip {
        private final Channel channel;
        private PosColorTriangleStrip(Channel channel) { this.channel = channel; }
        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }
        public void end() { this.channel.drawAndReset(); }
    }

    public static final class PosColorTriangleFan {
        private final Channel channel;
        private PosColorTriangleFan(Channel channel) { this.channel = channel; }
        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }
        public void end() { this.channel.drawAndReset(); }
    }

    public static final class PosTexColorQuads {
        private final Channel channel;
        private PosTexColorQuads(Channel channel) { this.channel = channel; }
        public void vertex(Matrix4f matrix, float x, float y, float z, float u, float v, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putUv(u, v);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }
        public void end() { this.channel.drawAndReset(); }
    }

    public static final class Lines {
        private final Channel channel;
        private final Vector3f normalTmp = new Vector3f();
        private Lines(Channel channel) { this.channel = channel; }
        public void vertex(Matrix4f matrix, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            pose.transformNormal(nx, ny, nz, this.normalTmp).normalize();
            this.channel.putNormal(this.normalTmp.x, this.normalTmp.y, this.normalTmp.z);
            this.channel.putLineWidth();
            this.channel.finishVertex();
        }
        public void end() { this.channel.drawAndReset(); }
    }

    private static final class Channel {

        private final LuminRingBuffer ringBuffer;
        private final VertexFormat format;
        private final PrimitiveTopology topology;
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

        private Matrix4f dynamicModelView = new Matrix4f();
        private float lineWidth = 1.0f;
        private RenderPipeline pipeline;
        @Nullable
        private Identifier texture;

        private Channel(VertexFormat format, PrimitiveTopology topology) {
            this.ringBuffer = new LuminRingBuffer(DEFAULT_BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);
            this.format = format;
            this.topology = topology;
            this.stride = format.getVertexSize();

            this.positionOffset = resolveOffset(format, "Position");
            this.colorOffset = resolveOffset(format, "Color");
            this.uvOffset = resolveOffset(format, "UV0");
            this.normalOffset = resolveOffset(format, "Normal");
            this.lineWidthOffset = resolveOffset(format, "LineWidth");
        }

        private static int resolveOffset(VertexFormat format, String semantic) {
            return format.contains(semantic) ? format.getElement(semantic).offset() : -1;
        }

        private Channel begin(RenderPipeline pipeline, @Nullable Identifier texture) {
            return begin(pipeline, texture, LuminRenderSystem.getModelViewMatrix());
        }

        private Channel begin(RenderPipeline pipeline, @Nullable Identifier texture, Matrix4f dynamicModelView) {
            return begin(pipeline, texture, dynamicModelView, 1.0f);
        }

        private Channel begin(RenderPipeline pipeline, @Nullable Identifier texture, Matrix4f dynamicModelView, float lineWidth) {
            if (this.building) throw new IllegalStateException("Immediate channel is already building");
            this.building = true;
            this.currentOffset = this.frameOffset;
            this.batchStartOffset = this.frameOffset;
            this.vertexCount = 0;
            this.pipeline = pipeline;
            this.texture = texture;
            this.dynamicModelView.set(dynamicModelView);
            this.lineWidth = lineWidth;
            this.ringBuffer.tryMap();
            return this;
        }

        private void putPosition(Matrix4f matrix, float x, float y, float z) {
            if (this.positionOffset < 0 || !ensureCapacity()) return;
            matrix.transformPosition(x, y, z, this.posTmp);
            long p = this.vertexBaseAddr + this.positionOffset;
            MemoryUtil.memPutFloat(p, this.posTmp.x);
            MemoryUtil.memPutFloat(p + 4L, this.posTmp.y);
            MemoryUtil.memPutFloat(p + 8L, this.posTmp.z);
        }

        private void putColor(int color) {
            if (this.colorOffset < 0 || !ensureCapacity()) return;
            // 26.2 的 COLOR 元素为 RGBA8_UNORM，内存字节序须为 R,G,B,A；
            // vanilla 以 ARGB.toABGR 重排后经 memPutInt（小端）写入，本处保持同一约定。
            int abgr = net.minecraft.util.ARGB.toABGR(color);
            long p = this.vertexBaseAddr + this.colorOffset;
            MemoryUtil.memPutInt(p, LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
        }

        private void putUv(float u, float v) {
            if (this.uvOffset < 0 || !ensureCapacity()) return;
            long p = this.vertexBaseAddr + this.uvOffset;
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }

        private void putNormal(float nx, float ny, float nz) {
            if (this.normalOffset < 0 || !ensureCapacity()) return;
            long p = this.vertexBaseAddr + this.normalOffset;
            MemoryUtil.memPutByte(p, packNormal(nx));
            MemoryUtil.memPutByte(p + 1L, packNormal(ny));
            MemoryUtil.memPutByte(p + 2L, packNormal(nz));
            MemoryUtil.memPutByte(p + 3L, (byte) 0);
        }

        private void putLineWidth() {
            if (this.lineWidthOffset < 0 || !ensureCapacity()) return;
            long p = this.vertexBaseAddr + this.lineWidthOffset;
            MemoryUtil.memPutFloat(p, this.lineWidth);
        }

        private void finishVertex() {
            if (!this.building || this.vertexBaseAddr == 0L) return;
            long completedVertexBaseAddr = this.vertexBaseAddr;
            this.currentOffset += this.stride;
            this.vertexCount++;
            if (this.topology == PrimitiveTopology.LINES) {
                long duplicateAddr = MemoryUtil.memAddress(this.ringBuffer.getMappedBuffer()) + this.currentOffset;
                MemoryUtil.memCopy(completedVertexBaseAddr, duplicateAddr, this.stride);
                this.currentOffset += this.stride;
                this.vertexCount++;
            }
            this.vertexBaseAddr = 0L;
        }

        private boolean ensureCapacity() {
            if (!this.building) return false;
            if (this.vertexBaseAddr != 0L) return true;
            long requiredBytes = this.topology == PrimitiveTopology.LINES ? this.stride * 2L : this.stride;
            this.ringBuffer.ensureCapacity(this.currentOffset + requiredBytes);
            if (!this.ringBuffer.isMapped()) this.ringBuffer.tryMap();
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
                if (!loggedDrawDiag) {
                    loggedDrawDiag = true;
                    var projSlice = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer();
                    float f0 = Float.NaN, f1 = Float.NaN, f2 = Float.NaN, f3 = Float.NaN;
                    if (this.ringBuffer.isMapped()) {
                        var bb = this.ringBuffer.getMappedBuffer();
                        int p = bb.position();
                        f0 = bb.getFloat(p);
                        f1 = bb.getFloat(p + 4);
                        f2 = bb.getFloat(p + 8);
                        f3 = bb.getFloat(p + 12);
                    }
                    io.github.openlumin.Constants.LOGGER.info(
                        "[OpenLumin-SelfTest] draw diag: vtx={} stride={} colorView={} projSlice={} pipeline={} batchOff={} firstXYZ=({},{},{})",
                        this.vertexCount, this.stride,
                        LuminRenderSystem.resolveColorView() != null, projSlice != null, this.pipeline.getLocation(),
                        this.batchStartOffset, f0, f1, f2);
                }
                if (this.ringBuffer.isMapped()) this.ringBuffer.unmap();

                GpuTextureView colorView = LuminRenderSystem.resolveColorView();
                GpuTextureView depthView = LuminRenderSystem.resolveDepthView();
                if (colorView == null) {
                    return;
                }

                GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeTransform(
                        this.dynamicModelView,
                        new Vector4f(1, 1, 1, 1),
                        new Vector3f(0, 0, 0),
                        new Matrix4f()
                );

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Lumin Immediate Draw",
                        colorView, Optional.empty(),
                        depthView, OptionalDouble.empty())
                ) {
                    pass.setPipeline(this.pipeline);
                    LuminRenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicUniforms);
                    // 26.2闂佹寧绋掑畝鎼佸Υ婵犲洦鍊烽柛锔诲幘婢跺嫰鏌涢幇顖氱毢缂侇噯濡囬埀顒冾潐閻喚鎷?slice
                    pass.setVertexBuffer(0, this.ringBuffer.getGpuBuffer().slice());

                    if (this.texture != null) {
                        AbstractTexture textureObject = Minecraft.getInstance().getTextureManager().getTexture(this.texture);
                        if (textureObject != null) {
                            var texView = textureObject instanceof io.github.openlumin.LuminTexture lt
                                    ? lt.getTextureView() : null;
                            if (texView != null) {
                                pass.bindTexture("Sampler0", texView, textureObject.getSampler());
                            }
                        }
                    }

                    int baseVertex = Math.toIntExact(this.batchStartOffset / this.stride);
                    switch (this.topology) {
                        case LINES, QUADS -> {
                            int indexCount = this.topology.primitiveLength > 0
                                    ? this.vertexCount / this.topology.primitiveLength * (this.topology.primitiveLength == 4 ? 6 : this.topology.primitiveLength)
                                    : 0;
                            if (indexCount > 0) {
                                var autoIndices = RenderSystem.getSequentialBuffer(this.topology);
                                GpuBuffer ibo = autoIndices.getBuffer(indexCount);
                                pass.setIndexBuffer(ibo, autoIndices.type());
                                pass.drawIndexed(indexCount, 1, 0, baseVertex, 0);
                                submittedDraw = true;
                            }
                        }
                        default -> {
                            pass.draw(this.vertexCount, 1, baseVertex, 0);
                            submittedDraw = true;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[OpenLumin] LuminImmediateRenderer drawAndReset error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (this.ringBuffer.isMapped()) this.ringBuffer.unmap();
                if (submittedDraw) {
                    this.frameUsed = true;
                    this.frameOffset = completedOffset;
                }
                this.building = false;
                this.currentOffset = this.frameOffset;
                this.batchStartOffset = this.frameOffset;
                this.vertexCount = 0;
                this.vertexBaseAddr = 0L;
                this.dynamicModelView.identity();
                this.lineWidth = 1.0f;
                this.pipeline = null;
                this.texture = null;
            }
        }

        private void endFrame() {
            if (this.ringBuffer.isMapped()) this.ringBuffer.unmap();
            if (this.frameUsed) this.ringBuffer.rotate();
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
