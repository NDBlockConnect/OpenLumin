package io.github.openlumin.renderers;

import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.LuminTexture;
import io.github.openlumin.buffer.LuminRingBuffer;
import io.github.openlumin.holders.RendererHolder;
import io.github.openlumin.holders.TextureCacheHolder;
import io.github.openlumin.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import io.github.openlumin.compat.RenderSystemShim;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.client.Minecraft;

/**
 * fabric-1.21.10 override：
 * - GpuTexture/GpuTextureView → textures 包
 * - createSampler() 已移除
 * - bindTexture(name, view, sampler) → bindSampler(name, view)
 * - LuminTexture 不再携带 GpuSampler
 * - AbstractTextureShim.getTextureView() → AbstractTexture.getColorTextureView()
 * - TextureFormat → textures.TextureFormat
 */
public class TextureRenderer implements IRenderer {

    private static final int STRIDE = 56;
    private static final long BUFFER_SIZE = 16 * 1024;
    private static final long QUAD_BYTES = STRIDE * 4L;

    private final Map<Object, Batch> batches = new LinkedHashMap<>();
    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private GpuBufferSlice sharedDynamicUniforms;
    private int sharedMaxIndexCount;

    private TextureRenderer() {}

    public static TextureRenderer create() {
        return RendererHolder.INSTANCE.register(new TextureRenderer());
    }

    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x(); scissorY = scissor.y();
        scissorW = scissor.width(); scissorH = scissor.height();
    }

    public void clearScissor() { scissorEnabled = false; }

    public void addQuadTexture(LuminTexture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, w, h, 0f, u0, v0, u1, v1, color);
    }

    public void addQuadTexture(ResourceLocation texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, w, h, 0f, u0, v0, u1, v1, color, false);
    }

    public void addQuadTexture(ResourceLocation texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color, boolean linear) {
        addRoundedTexture(texture, x, y, w, h, 0f, u0, v0, u1, v1, color, linear);
    }

    public void addRoundedTexture(ResourceLocation texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, w, h, r, u0, v0, u1, v1, color, false);
    }

    public void addRoundedTexture(ResourceLocation texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, Color color, boolean linear) {
        addRoundedTexture((Object) texture, x, y, w, h, r, r, r, r, u0, v0, u1, v1, color, linear);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, w, h, r, r, r, r, u0, v0, u1, v1, color, true);
    }

    public void addRoundedTexture(ResourceLocation texture, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float u0, float v0, float u1, float v1, Color color, boolean linear) {
        addRoundedTexture((Object) texture, x, y, w, h, rTL, rTR, rBR, rBL, u0, v0, u1, v1, color, linear);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, w, h, rTL, rTR, rBR, rBL, u0, v0, u1, v1, color, true);
    }

    public void addRotatedTexture(ResourceLocation texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color, float ox, float oy, float deg, boolean linear) {
        addRotatedTexture((Object) texture, x, y, w, h, u0, v0, u1, v1, color, ox, oy, deg, linear);
    }

    public void addRotatedTexture(LuminTexture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color, float ox, float oy, float deg) {
        addRotatedTexture(texture, x, y, w, h, u0, v0, u1, v1, color, ox, oy, deg, true);
    }

    public void addPlayerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
        addRoundedTexture(texture, x, y, size, size, radius, 8f/64f, 8f/64f, 16f/64f, 16f/64f, color);
        addRoundedTexture(texture, x, y, size, size, radius, 40f/64f, 8f/64f, 48f/64f, 16f/64f, color);
    }

    public void addPlayerHead(ResourceLocation texture, float x, float y, float size, float radius, Color color) {
        addRoundedTexture(texture, x, y, size, size, radius, 8f/64f, 8f/64f, 16f/64f, 16f/64f, color);
        addRoundedTexture(texture, x, y, size, size, radius, 40f/64f, 8f/64f, 48f/64f, 16f/64f, color);
    }

    private void addRoundedTexture(Object key, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float u0, float v0, float u1, float v1, Color color, boolean linear) {
        Batch batch = batches.computeIfAbsent(key, k -> { Batch b = new Batch(new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)); b.useLinearFilter = linear; return b; });
        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES);
        batch.buffer.tryMap();
        int argb = ARGB.toABGR(color.getRGB());
        float x2 = x + w, y2 = y + h;
        long base = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
        long p = base + batch.currentOffset;
        writeVertex(p,          x,  y,  u0, v0, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p+STRIDE,   x,  y2, u0, v1, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p+STRIDE*2L,x2, y2, u1, v1, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p+STRIDE*3L,x2, y,  u1, v0, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        batch.currentOffset += QUAD_BYTES;
        batch.vertexCount += 4;
    }

    private void addRotatedTexture(Object key, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color color, float ox, float oy, float deg, boolean linear) {
        Batch batch = batches.computeIfAbsent(key, k -> { Batch b = new Batch(new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)); b.useLinearFilter = linear; return b; });
        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES);
        batch.buffer.tryMap();
        int argb = ARGB.toABGR(color.getRGB());
        float x2 = x + w, y2 = y + h;
        float rad = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float rx1 = rotX(x, y, ox, oy, cos, sin), ry1 = rotY(x, y, ox, oy, cos, sin);
        float rx2 = rotX(x, y2, ox, oy, cos, sin), ry2 = rotY(x, y2, ox, oy, cos, sin);
        float rx3 = rotX(x2, y2, ox, oy, cos, sin), ry3 = rotY(x2, y2, ox, oy, cos, sin);
        float rx4 = rotX(x2, y, ox, oy, cos, sin), ry4 = rotY(x2, y, ox, oy, cos, sin);
        float mnX = Math.min(Math.min(rx1,rx2),Math.min(rx3,rx4)), mnY = Math.min(Math.min(ry1,ry2),Math.min(ry3,ry4));
        float mxX = Math.max(Math.max(rx1,rx2),Math.max(rx3,rx4)), mxY = Math.max(Math.max(ry1,ry2),Math.max(ry3,ry4));
        long base = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
        long p = base + batch.currentOffset;
        writeVertex(p,          rx1, ry1, u0, v0, argb, mnX, mnY, mxX, mxY, 0,0,0,0);
        writeVertex(p+STRIDE,   rx2, ry2, u0, v1, argb, mnX, mnY, mxX, mxY, 0,0,0,0);
        writeVertex(p+STRIDE*2L,rx3, ry3, u1, v1, argb, mnX, mnY, mxX, mxY, 0,0,0,0);
        writeVertex(p+STRIDE*3L,rx4, ry4, u1, v0, argb, mnX, mnY, mxX, mxY, 0,0,0,0);
        batch.currentOffset += QUAD_BYTES;
        batch.vertexCount += 4;
    }

    private static float rotX(float x, float y, float ox, float oy, float cos, float sin) { float dx=x-ox, dy=y-oy; return ox+dx*cos-dy*sin; }
    private static float rotY(float x, float y, float ox, float oy, float cos, float sin) { float dx=x-ox, dy=y-oy; return oy+dx*sin+dy*cos; }

    private void writeVertex(long addr, float x, float y, float u, float v, int color, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4) {
        MemoryUtil.memPutFloat(addr,    x);    MemoryUtil.memPutFloat(addr+4,  y);    MemoryUtil.memPutFloat(addr+8,  0f);
        MemoryUtil.memPutInt(addr+12,   color);
        MemoryUtil.memPutFloat(addr+16, u);    MemoryUtil.memPutFloat(addr+20, v);
        MemoryUtil.memPutFloat(addr+24, rx1);  MemoryUtil.memPutFloat(addr+28, ry1);
        MemoryUtil.memPutFloat(addr+32, rx2);  MemoryUtil.memPutFloat(addr+36, ry2);
        MemoryUtil.memPutFloat(addr+40, r1);   MemoryUtil.memPutFloat(addr+44, r2);
        MemoryUtil.memPutFloat(addr+48, r3);   MemoryUtil.memPutFloat(addr+52, r4);
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;
        LuminRenderSystem.applyOrthoProjection();
        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        int maxIndexCount = prepareTextureBatches();
        if (maxIndexCount == 0) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount);
        try (RenderPass pass = RenderSystemShim.getDevice().createCommandEncoder().createRenderPass(
                () -> "Rounded Texture Draws",
                colorView, OptionalInt.empty(), null, OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.TEXTURE);
            if (scissorEnabled) ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            pass.setUniform("DynamicTransforms", dynamicUniforms);
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());
            drawPrepared(pass);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedDynamicUniforms = null; sharedMaxIndexCount = 0;
        if (batches.isEmpty()) return false;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;
        sharedMaxIndexCount = prepareTextureBatches();
        if (sharedMaxIndexCount == 0) return false;
        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        return sharedDynamicUniforms != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return;
        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount), LuminRenderSystem.getQuadIndexType());
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms);
        drawPrepared(pass);
    }

    private int prepareTextureBatches() {
        int maxIndexCount = 0;
        for (Map.Entry<Object, Batch> entry : batches.entrySet()) {
            Batch batch = entry.getValue();
            batch.preparedTexture = null;
            if (batch.vertexCount == 0) continue;
            if (batch.buffer.isMapped()) batch.buffer.unmap();
            batch.preparedTexture = resolveTexture(entry.getKey(), batch.useLinearFilter);
            if (batch.preparedTexture == null) continue;
            maxIndexCount = Math.max(maxIndexCount, (batch.vertexCount / 4) * 6);
        }
        return maxIndexCount;
    }

    private LuminTexture resolveTexture(Object key, boolean linear) {
        if (key instanceof ResourceLocation id)
            return TextureCacheHolder.INSTANCE.textureCache.computeIfAbsent(id, k -> loadTexture(k, linear));
        if (key instanceof LuminTexture tex) return tex;
        return null;
    }

    private void drawPrepared(RenderPass pass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) return;
        } else {
            pass.disableScissor();
        }
        for (Batch batch : batches.values()) {
            if (batch.vertexCount == 0 || batch.preparedTexture == null) continue;
            int indexCount = (batch.vertexCount / 4) * 6;
            LuminTexture texture = batch.preparedTexture;
            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer());
            // 1.21.10：bindSampler(name, view) 替代 bindTexture(name, view, sampler)
            pass.bindSampler("Sampler0", texture.getTextureView());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    private LuminTexture loadTexture(ResourceLocation id, boolean linear) {
        // 先尝试从已注册的AbstractTexture获取GpuTextureView（1.21.10原生API）
        try {
            AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(id);
            if (abstractTexture != null) {
                // 1.21.10: getColorTextureView() 不在 AbstractTexture MC 基类上。
                // 对于 LuminTexture（我们自己上传的），直接取 textureView；否则 view=null，下面 if 过滤。
                GpuTextureView view = abstractTexture instanceof io.github.openlumin.LuminTexture lt
                        ? lt.getTextureView() : null;
                if (view != null) {
                    // 不拥有此纹理，close=false
                    return new LuminTexture(null, view, false);
                }
            }
        } catch (Exception ignored) {}

        // 回退：从资源管理器直接加载NativeImage并上传到GPU
        NativeImage image;
        try {
            var manager = Minecraft.getInstance().getResourceManager();
            var resource = manager.getResourceOrThrow(id);
            try (var stream = resource.open()) {
                image = NativeImage.read(stream);
            }
        } catch (IOException e) {
            image = MissingTextureAtlasSprite.generateMissingImage();
        }

        var device = RenderSystemShim.getDevice();
        GpuTexture texture = device.createTexture(
                id.toString(),
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                image.getWidth(), image.getHeight(), 1, 1
        );
        device.createCommandEncoder().writeToTexture(texture, image);
        GpuTextureView view = device.createTextureView(texture);
        image.close();
        return new LuminTexture(texture, view, true);
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.vertexCount > 0) {
                if (batch.buffer.isMapped()) batch.buffer.unmap();
                batch.buffer.rotate();
            }
            batch.currentOffset = 0; batch.vertexCount = 0; batch.preparedTexture = null;
        }
        sharedDynamicUniforms = null; sharedMaxIndexCount = 0;
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) batch.buffer.close();
        batches.clear();
        TextureCacheHolder.INSTANCE.clearCache();
        RendererHolder.INSTANCE.unregister(this);
    }

    private static final class Batch {
        final LuminRingBuffer buffer;
        long currentOffset = 0;
        int vertexCount = 0;
        boolean useLinearFilter;
        LuminTexture preparedTexture;
        private Batch(LuminRingBuffer buffer) { this.buffer = buffer; }
    }
}
