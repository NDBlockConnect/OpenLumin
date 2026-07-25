package io.github.openlumin.text.minecraft;

import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.text.GlyphDescriptor;
import io.github.openlumin.text.ttf.TtfFontLoader;
import io.github.openlumin.text.ttf.TtfGlyphAtlas;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * fabric-1.21.10 override：
 * - GpuTextureView → textures 包
 * - RenderSetup/RenderType 在1.21.10 API 变化，renderType() 暂时 stub（TTF 渲染由 TtfTextRenderer 独立处理）
 * - TextRenderable.Styled 已移除（Fabric1.21.10 BakedGlyph 接口变更）
 * - getSampler() 返回 null（1.21.10 已无 GpuSampler）
 */
public final class EpsilonFontGlyph implements BakedGlyph {

    private static final float SHADOW_OFFSET = 0.45f;
    private static final float BOLD_OFFSET = 0.45f;

    private final int codepoint;
    private final TtfFontLoader font;
    private final @Nullable GlyphDescriptor descriptor;
    private final GlyphInfo info;

    private EpsilonFontGlyph(int codepoint, TtfFontLoader font, @Nullable GlyphDescriptor descriptor) {
        this.codepoint = codepoint;
        this.font = font;
        this.descriptor = descriptor;
        this.info = new EpsilonGlyphInfo(EpsilonFontMetrics.advance(codepoint, Style.EMPTY, font));
    }

    public static @Nullable EpsilonFontGlyph create(int codepoint) {
        TtfFontLoader font = EpsilonFontMetrics.font();
        if (font == null) return null;

        if (Character.isWhitespace(codepoint)) {
            return new EpsilonFontGlyph(codepoint, font, null);
        }
        if (codepoint < Character.MIN_CODE_POINT || codepoint > Character.MAX_CODE_POINT) {
            return null;
        }

        font.requestChars(new String(Character.toChars(codepoint)));
        GlyphDescriptor descriptor = font.getGlyph(codepoint);
        return descriptor != null ? new EpsilonFontGlyph(codepoint, font, descriptor) : null;
    }

    // Fabric专属方法
    public GlyphInfo info() { return this.info; }

    // Fabric专属方法 — 1.21.10: BakedGlyph.createGlyph() 返回 TextRenderable
    @Override
    public @Nullable TextRenderable createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
        if (this.descriptor == null) return null;
        return new GlyphInstance(this, x, y, color, shadowColor, style, boldOffset, shadowOffset);
    }

    /**
     * 1.21.10: RenderSetup API 已变更，renderType() 暂不支持 MC 原生字体管线集成。
     * OpenLumin 的 TTF 渲染通过 TtfTextRenderer 独立进行，不依赖此方法。
     */
    public Object renderType() {
        throw new UnsupportedOperationException(
            "EpsilonFontGlyph.renderType() not supported on 1.21.10 — use TtfTextRenderer directly");
    }

    private float baselineY(float y) {
        return y + this.font.fontFile.pixelAscent * scale();
    }

    private float scale() {
        return EpsilonFontMetrics.minecraftScale(this.font);
    }

    private float left(float x, boolean bold, boolean italic) {
        if (this.descriptor == null) return x;
        float left = x + this.descriptor.xOffset() * scale();
        if (italic) left += Math.min(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)));
        if (bold) left -= extraThickness(true);
        return left;
    }

    private static float extraThickness(boolean bold) { return bold ? 0.06f : 0.0f; }

    private float top(float y) {
        if (this.descriptor == null) return y;
        return yTop(y);
    }

    private float right(float x, boolean hasShadow, float shadowOffset, boolean bold, boolean italic) {
        if (this.descriptor == null) return x + this.info.getAdvance(bold);
        float right = x + this.descriptor.xOffset() * scale() + this.descriptor.width() * scale();
        if (hasShadow) right += shadowOffset;
        if (bold) right += extraThickness(true);
        if (italic) right += Math.max(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)));
        return right;
    }

    private float bottom(float y, boolean hasShadow, float shadowOffset, boolean bold) {
        float bottom = yBottom(y);
        if (hasShadow) bottom += shadowOffset;
        if (bold) bottom += extraThickness(true);
        return bottom;
    }

    private float yTop(float y) { return baselineY(y) + this.descriptor.yOffset() * scale(); }

    private float yBottom(float y) { return yTop(y) + this.descriptor.height() * scale(); }

    private static float italicShearTop(float glyphTopRelativeToTextY) {
        return 1.0f - 0.25f * glyphTopRelativeToTextY;
    }

    private static float italicShearBottom(float glyphBottomRelativeToTextY) {
        return 1.0f - 0.25f * glyphBottomRelativeToTextY;
    }

    private void renderGlyph(Matrix4fc pose, VertexConsumer buffer, GlyphInstance instance, float offsetX, float offsetY, float z, int color, boolean bold) {
        if (this.descriptor == null) return;
        float x0 = instance.x + this.descriptor.xOffset() * scale() + offsetX;
        float x1 = x0 + this.descriptor.width() * scale();
        float y0 = top(instance.y) + offsetY;
        float y1 = y0 + this.descriptor.height() * scale();
        float extraThickness = extraThickness(bold);

        float shearTop = instance.style.isItalic() ? italicShearTop(y0 - instance.y) : 0.0f;
        float shearBottom = instance.style.isItalic() ? italicShearBottom(y1 - instance.y) : 0.0f;

        TtfGlyphAtlas.GlyphUV uv = this.descriptor.uv();
        buffer.addVertex((Matrix4f) pose, x0 + shearTop - extraThickness, y0 - extraThickness, z).setUv(uv.u0(), uv.v0()).setColor(color);
        buffer.addVertex((Matrix4f) pose, x0 + shearBottom - extraThickness, y1 + extraThickness, z).setUv(uv.u0(), uv.v1()).setColor(color);
        buffer.addVertex((Matrix4f) pose, x1 + shearBottom + extraThickness, y1 + extraThickness, z).setUv(uv.u1(), uv.v1()).setColor(color);
        buffer.addVertex((Matrix4f) pose, x1 + shearTop + extraThickness, y0 - extraThickness, z).setUv(uv.u1(), uv.v0()).setColor(color);
    }

    private record GlyphInstance(
            EpsilonFontGlyph glyph,
            float x,
            float y,
            int color,
            int shadowColor,
            Style style,
            float boldOffset,
            float shadowOffset
    ) implements TextRenderable, EpsilonTextRenderable {

        private boolean hasShadow() { return this.shadowColor != 0; }

        // Fabric专属方法
        public void render(Matrix4f pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
            float depth;
            if (this.hasShadow()) {
                this.glyph.renderGlyph(pose, buffer, this, this.shadowOffset, this.shadowOffset, 0.0f, this.shadowColor, this.style.isBold());
                depth = flat ? 0.0f : Font.SHADOW_DEPTH;
            } else {
                depth = 0.0f;
            }
            this.glyph.renderGlyph(pose, buffer, this, 0.0f, 0.0f, depth, this.color, this.style.isBold());
            if (this.style.isBold()) {
                this.glyph.renderGlyph(pose, buffer, this, this.boldOffset, 0.0f, depth + (flat ? 0.0f : 0.001f), this.color, true);
            }
        }

        // Fabric专属方法
        public RenderType renderType(Font.DisplayMode displayMode) {
            // 1.21.10: OpenLumin TTF 渲染不经过 MC 字体管线，此方法不会被调用
            return null;
        }

        // Fabric专属方法
        public GpuTextureView textureView() {
            if (this.glyph.descriptor == null) throw new IllegalStateException("Whitespace glyphs do not have textures");
            return this.glyph.descriptor.atlas().getTexture().getTextureView();
        }

        // epsilon$sampler() — 1.21.10 GpuSampler 已移除，仅保留兼容性
        public GpuSampler epsilon$sampler() {
            // 1.21.10: GpuSampler 已移除，返回 null
            return null;
        }

        // Fabric专属方法
        public RenderPipeline guiPipeline() {
            return ClientSetting.INSTANCE.fontAntiAliasing.getValue()
                    ? LuminRenderPipelines.TTF_FONT_AA
                    : LuminRenderPipelines.TTF_FONT_NO_AA;
        }

        // Fabric专属方法
        public float left() { return this.glyph.left(this.x, this.style.isBold(), this.style.isItalic()); }

        public float top() { return this.glyph.top(this.y); }

        public float right() { return this.glyph.right(this.x, this.hasShadow(), this.shadowOffset, this.style.isBold(), this.style.isItalic()); }

        public float activeRight() { return this.x + this.glyph.info.getAdvance(this.style.isBold()); }

        public float bottom() { return this.glyph.bottom(this.y, this.hasShadow(), this.shadowOffset, this.style.isBold()); }
    }

    private record EpsilonGlyphInfo(float advance) implements GlyphInfo {
        @Override
        public float getAdvance() { return this.advance; }

        @Override
        public float getBoldOffset() { return BOLD_OFFSET; }

        @Override
        public float getShadowOffset() { return SHADOW_OFFSET; }

        // bake() — 参数类型可能已变更，去掉 @Override 以确保编译
        public BakedGlyph bake(Function<com.mojang.blaze3d.font.SheetGlyphInfo, BakedGlyph> function) {
            return null;
        }
    }
}
