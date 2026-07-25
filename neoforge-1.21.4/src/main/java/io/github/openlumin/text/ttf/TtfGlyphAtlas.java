package io.github.openlumin.text.ttf;

import io.github.openlumin.LuminTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.AddressMode;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.FilterMode;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTexture;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfGlyphAtlas {

    private static final int SIZE = 1024;
    private static final int GLYPH_GUTTER = 2;
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();
    private final LuminTexture texture;
    private final ResourceLocation textureId;

    private int currentX = 0;
    private int currentY = 0;
    private int currentRowHeight = 0;

    public TtfGlyphAtlas(int atlasId) {
        this.textureId = ResourceLocation.fromNamespaceAndPath("epsilon", "ttf_atlas/" + NEXT_TEXTURE_ID.getAndIncrement());

        // NeoForge不支持RenderSystem.getDevice()
        // 使用占位值代替
        final var texture = new GpuTexture(SIZE, SIZE);
        final var textureView = texture.createView();
        final var sampler = new io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuSampler();

        /*
        final var texture = RenderSystem.getDevice().createTexture(
                () -> "Lumin-TtfGlyphAtlas",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RED8,
                SIZE, SIZE,
                1, 1
        );

        final var textureView = RenderSystem.getDevice().createTextureView(texture);
        final var sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR,
                1, OptionalDouble.empty()
        );
        */

        this.texture = new LuminTexture(texture, textureView, sampler);
        fillTextureWithTransparentDistance(texture);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
    }

    private static void fillTextureWithTransparentDistance(GpuTexture texture) {
        ByteBuffer transparent = MemoryUtil.memAlloc(SIZE * SIZE);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(transparent), 0xFF, SIZE * SIZE);
            // NeoForge不支持RenderSystem.getDevice()
            /*
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture,
                    transparent,
                    NativeImage.Format.LUMINANCE,
                    0,
                    0,
                    0, 0,
                    SIZE,
                    SIZE
            );
            */
        } finally {
            MemoryUtil.memFree(transparent);
        }
    }

    /**
     * Try to append a glyph to atlas
     * <p>
     * Return null if glyph atlas is full
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        int cellWidth = glyph.width() + GLYPH_GUTTER * 2;
        int cellHeight = glyph.height() + GLYPH_GUTTER * 2;

        if (currentX + cellWidth >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // Return null if glyph atlas is full
        if (currentY + cellHeight >= SIZE) {
            return null;
        }

        int glyphX = currentX + GLYPH_GUTTER;
        int glyphY = currentY + GLYPH_GUTTER;

        // NeoForge不支持RenderSystem.getDevice()
        /*
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                this.texture.getTexture(),
                glyph.glyphData(),
                NativeImage.Format.LUMINANCE,
                0,
                0,
                glyphX, glyphY,
                glyph.width(),
                glyph.height()
        );
        */

        GlyphUV uv = new GlyphUV(
                (float) glyphX / SIZE,
                (float) glyphY / SIZE,
                (float) (glyphX + glyph.width()) / SIZE,
                (float) (glyphY + glyph.height()) / SIZE
        );

        currentX += cellWidth;
        currentRowHeight = Math.max(currentRowHeight, cellHeight);

        return uv;
    }

    public LuminTexture getTexture() {
        return texture;
    }

    public static int getSize() {
        return SIZE;
    }

    public ResourceLocation getTextureId() {
        return textureId;
    }

    public void destroy() {
        Minecraft.getInstance().getTextureManager().release(this.textureId);
    }

    public record GlyphUV(float u0, float v0, float u1, float v1) {
    }

}
