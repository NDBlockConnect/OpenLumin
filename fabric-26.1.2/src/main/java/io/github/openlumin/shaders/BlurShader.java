package io.github.openlumin.shaders;

import net.minecraft.resources.Identifier;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.client.Minecraft;

/**
 * fabric-1.21.10 override：
 * - GpuTexture/GpuTextureView 改用 textures 包
 * - bindTexture(name, view, sampler) → bindSampler(name, view)
 * - createSampler() 已移除
 * - AutoStorageIndexBuffer 改用 RenderSystem.getSequentialBuffer()
 * - TextureTransform 改用 new Matrix4f()（单位矩阵）
 * - 移除 ColorTargetState/DepthStencilState 的 pipeline builder 调用（POST_PROCESSING_SNIPPET 已配置混合）
 */
public class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final Identifier BLUR_PATH = Identifier.fromNamespaceAndPath("openlumin","blur");
    private static final Identifier BLUR_3D_BOX_PATH = Identifier.fromNamespaceAndPath("openlumin","blur_3d_box");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec3()
            .putVec4()
            .putVec4()
            .get();

    private static final int BOX_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderPipeline boxPipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipeline/blur"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_PATH)
                    .withUniform("BlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureBoxProgram() {
        if (this.boxPipeline == null) {
            // blur_3d_box.vsh 依赖 DynamicTransforms(ModelViewMat) 与 Projection(ProjMat)，
            // 盒体几何经 POSITION_COLOR 网格 drawIndexed 提交；
            // POST_PROCESSING_SNIPPET 仅含 EMPTY 顶点格式且不声明任何 UBO，必须显式补齐。
            this.boxPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipeline/blur_3d_box"))
                    .withVertexShader(BLUR_3D_BOX_PATH)
                    .withFragmentShader(BLUR_3D_BOX_PATH)
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("BoxBlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false)
                    .build();
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
        GpuTexture targetTexture = activeTarget == null ? target.getColorTexture() : activeTarget.colorTexture();
        GpuTextureView targetView = activeTarget == null ? target.getColorTextureView() : activeTarget.colorView();
        int targetWidth = activeTarget == null ? target.width : activeTarget.width();
        int targetHeight = activeTarget == null ? target.height : activeTarget.height();

        if (targetWidth <= 0 || targetHeight <= 0 || targetTexture == null || targetView == null) {
            return;
        }

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", targetWidth, targetHeight, false);
        }

        if (this.input.width != targetWidth || this.input.height != targetHeight) {
            this.input.resize(targetWidth, targetHeight);
        }

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

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

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                targetTexture,
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                targetWidth, targetHeight
        );

        GpuBufferSlice blurUniforms = LuminRenderSystem.writeDynamicUniform(
                "blur_uniforms",
                "Lumin Blur UBO",
                UNIFORMS_SIZE,
                16,
                new BlurUniforms(targetWidth, targetHeight, quality, pxW, pxH, pxX, pxY, rTLPx, rTRPx, rBRPx, rBLPx)
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin Blur",
                targetView,
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            ScissorUtils.enableScissor(renderPass, scissor);
            renderPass.setUniform("BlurUniforms", blurUniforms);
            // 1.21.10: bindSampler(name, view) — 无需 GpuSampler 对象
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 3);
        }
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength);
    }

    public void render3DBox(AABB box, double blurStrength) {
        this.ensureBoxProgram();

        RenderTarget fb = Minecraft.getInstance().getMainRenderTarget();
        if (fb.width <= 0 || fb.height <= 0) return;
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", fb.width, fb.height, false);
        }

        if (this.input.width != fb.width || this.input.height != fb.height) {
            this.input.resize(fb.width, fb.height);
        }

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                fb.width, fb.height
        );

        float quality = Math.max(0.0f, (float) blurStrength);
        GpuBufferSlice boxBlurUniforms = LuminRenderSystem.writeDynamicUniform(
                "box_blur_uniforms",
                "Lumin 3D Box Blur UBO",
                BOX_UNIFORMS_SIZE,
                16,
                new BoxBlurUniforms(fb.width, fb.height, quality)
        );

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addBoxVertices(buffer, box);
        MeshData mesh = buffer.buildOrThrow();

        GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                () -> "BlurShader vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer().remaining());
        // 1.21.10: RenderSystem.getSequentialBuffer() 替代 new AutoStorageIndexBuffer(mode)
        var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
        GpuBuffer indices = autoIndices.getBuffer(mesh.drawState().indexCount());
        VertexFormat.IndexType indexType = autoIndices.type();

        // 1.21.10: writeTransform 需要第5参数 lineWidth=1.0f，TextureTransform 改用单位矩阵
        GpuBufferSlice dynamicTransforms = LuminRenderSystem.writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f(),
                new Matrix4f()
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin 3D Box Blur",
                fb.getColorTextureView(), OptionalInt.empty(),
                fb.useDepth ? fb.getDepthTextureView() : null, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(this.boxPipeline);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("BoxBlurUniforms", boxBlurUniforms);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
        }

        mesh.close();
    }

    private void addBoxVertices(BufferBuilder buffer, AABB box) {
        Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.position();

        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = RenderSystem.getModelViewMatrix();

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, minX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, minZ);

        vertex(buffer, matrix, minX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, minX, maxY, maxZ);

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, minX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, minY, minZ);

        vertex(buffer, matrix, maxX, minY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);

        vertex(buffer, matrix, minX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, minX, maxY, maxZ);

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, maxX, minY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, minX, maxY, minZ);
    }

    private void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z) {
        buffer.addVertex(matrix, x, y, z).setColor(0x00000000);
    }

    private record BlurUniforms(
            float targetWidth, float targetHeight, float quality,
            float pxW, float pxH, float pxX, float pxY,
            float rTL, float rTR, float rBR, float rBL
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec3(targetWidth, targetHeight, quality)
                    .putVec4(pxW, pxH, pxX, pxY)
                    .putVec4(rTL, rTR, rBR, rBL);
        }
    }

    private record BoxBlurUniforms(float width, float height, float quality) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(width, height, quality, 0.0f);
        }
    }
}
