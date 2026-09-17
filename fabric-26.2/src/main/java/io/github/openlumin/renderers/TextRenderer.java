package io.github.openlumin.renderers;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.text.ITextRenderer;
import io.github.openlumin.text.StaticFontLoader;
import io.github.openlumin.text.ttf.TtfFontLoader;
import io.github.openlumin.text.ttf.TtfTextRenderer;
import io.github.openlumin.holders.RendererHolder;
import com.mojang.blaze3d.systems.RenderPass;

import java.awt.*;

public class TextRenderer implements IRenderer {

    private final ITextRenderer textRenderer;
    private boolean registered = true;

    private TextRenderer(long bufferSize) {
        textRenderer = new TtfTextRenderer(bufferSize);
    }

    private TextRenderer() {
        textRenderer = new TtfTextRenderer();
    }

    public static TextRenderer create(long bufferSize) {
        return RendererHolder.INSTANCE.register(new TextRenderer(bufferSize));
    }

    public static TextRenderer create() {
        return RendererHolder.INSTANCE.register(new TextRenderer());
    }

    public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        ensureRegistered();
        if (fontLoader != null) {
            textRenderer.addText(text, x, y, scale, color, fontLoader);
        }
    }

    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor, TtfFontLoader fontLoader) {
        ensureRegistered();
        if (fontLoader != null) {
            textRenderer.addGradientText(text, x, y, scale, startColor, endColor, fontLoader);
        }
    }

    public void addRotatedText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
        ensureRegistered();
        if (fontLoader != null) {
            textRenderer.addRotatedText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees);
        }
    }

    public void addText(String text, float x, float y, float scale, Color color) {
        if (fontUnavailable()) return;
        textRenderer.addText(text, x, y, scale, color, StaticFontLoader.defaultFont());
    }

    public void addRotatedText(String text, float x, float y, float scale, Color color, float originX, float originY, float rotationDegrees) {
        if (fontUnavailable()) return;
        textRenderer.addRotatedText(text, x, y, scale, color, StaticFontLoader.defaultFont(), originX, originY, rotationDegrees);
    }

    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor) {
        if (fontUnavailable()) return;
        textRenderer.addGradientText(text, x, y, scale, startColor, endColor, StaticFontLoader.defaultFont());
    }

    public void addText(String text, float x, float y, Color color, TtfFontLoader fontLoader) {
        if (fontUnavailable() || fontLoader == null) return;
        textRenderer.addText(text, x, y, 1.0f, color, fontLoader);
    }

    public void addText(String text, float x, float y, Color color) {
        if (fontUnavailable()) return;
        textRenderer.addText(text, x, y, 1.0f, color, StaticFontLoader.defaultFont());
    }

    public float getHeight(float scale) {
        return fontUnavailable() ? 0f : textRenderer.getHeight(scale, StaticFontLoader.defaultFont());
    }

    public float getHeight(float scale, TtfFontLoader fontLoader) {
        return (fontUnavailable() || fontLoader == null) ? 0f : textRenderer.getHeight(scale, fontLoader);
    }

    public float getWidth(String text, float scale) {
        return fontUnavailable() ? 0f : textRenderer.getWidth(text, scale, StaticFontLoader.defaultFont());
    }

    public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
        return (fontUnavailable() || fontLoader == null) ? 0f : textRenderer.getWidth(text, scale, fontLoader);
    }

    /**
     * 缺字体时整条文本路径静默降级为无操作：调用方不崩、不绘制、宽度按 0 计。
     * 正常安装（字体资源在位）时永远为 false，零开销。
     */
    private static boolean fontUnavailable() {
        return StaticFontLoader.defaultFont() == null;
    }

    public void setScissor(int x, int y, int width, int height) {
        textRenderer.setScissor(x, y, width, height);
    }

    public void clearScissor() {
        textRenderer.clearScissor();
    }

    @Override
    public void draw() {
        LuminRenderSystem.applyOrthoProjection();
        textRenderer.draw();
    }

    @Override
    public boolean prepareSharedDraw() {
        return textRenderer.prepareSharedDraw();
    }

    @Override
    public void draw(RenderPass pass) {
        textRenderer.draw(pass);
    }

    @Override
    public void clear() {
        textRenderer.clear();
    }

    @Override
    public void close() {
        textRenderer.close();
        if (registered) {
            RendererHolder.INSTANCE.unregister(this);
            registered = false;
        }
    }

    private void ensureRegistered() {
        if (!registered) {
            RendererHolder.INSTANCE.register(this);
            registered = true;
        }
    }

}
