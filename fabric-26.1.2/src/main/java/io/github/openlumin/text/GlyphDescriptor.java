package io.github.openlumin.text;

import io.github.openlumin.text.ttf.TtfGlyphAtlas;

public record GlyphDescriptor(
        TtfGlyphAtlas atlas,
        TtfGlyphAtlas.GlyphUV uv,
        int width,
        int height,
        int xOffset,
        int yOffset,
        int advance
) {
}