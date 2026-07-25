package io.github.openlumin.schedulers.render2d;

import io.github.openlumin.text.ttf.TtfFontLoader;

import java.awt.*;

sealed interface Render2DCommand permits Render2DCommand.Shadow, Render2DCommand.RoundRect,
        Render2DCommand.RoundRectOutline, Render2DCommand.Ellipse, Render2DCommand.Arc, Render2DCommand.Rect, Render2DCommand.Triangle,
        Render2DCommand.FreeTriangle, Render2DCommand.Texture, Render2DCommand.Text {

    int layer();

    long sequence();

    Render2DCommandKind kind();

    Render2DBounds bounds();

    Render2DScissor scissor();

    default Render2DBounds orderingBounds() {
        return bounds();
    }

    record Shadow(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                  float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                  float blurRadius, Color color) implements Render2DCommand {
        @Override
        public Render2DBounds orderingBounds() {
            float pad = Math.max(0.0f, blurRadius);
            return Render2DBounds.of(bounds.x() - pad, bounds.y() - pad,
                    bounds.width() + pad * 2.0f, bounds.height() + pad * 2.0f);
        }

        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.SHADOW;
        }
    }

    record RoundRect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                     float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                     Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ROUND_RECT;
        }
    }

    record RoundRectOutline(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth,
                            Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DBounds orderingBounds() {
            float pad = Math.max(0.0f, outlineWidth * 0.5f);
            return Render2DBounds.of(bounds.x() - pad, bounds.y() - pad,
                    bounds.width() + pad * 2.0f, bounds.height() + pad * 2.0f);
        }

        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ROUND_RECT_OUTLINE;
        }
    }

    /**
     * 椭圆（含圆）。bounds 即椭圆的轴对齐包围盒，半轴 a=width/2、b=height/2。
     * 使用真椭圆 SDF 渲染，四角颜色映射到包围盒四角实现渐变。
     */
    record Ellipse(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                   Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ELLIPSE;
        }
    }

    /**
     * 弧/环段/扇形。bounds 即外椭圆包围盒。
     * startAngle/endAngle 为弧度制角度范围（数学坐标系，atan2 约定）；
     * innerRatio ∈ [0,1]：0 = 实心扇形(pie)，>0 = 环段（内半径 = 外半径 * innerRatio）。
     */
    record Arc(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
               float startAngle, float endAngle, float innerRatio,
               Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ARC;
        }
    }

    record Rect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.RECT;
        }
    }

    record Triangle(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                    float centerX, float centerY, float size, float progress, Color color) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TRIANGLE;
        }
    }

    record Texture(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                   Render2DTexture texture, float radiusTopLeft, float radiusTopRight,
                   float radiusBottomRight, float radiusBottomLeft, float u0, float v0, float u1, float v1,
                   Color color, float originX, float originY, float rotationDegrees) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TEXTURE;
        }
    }

    record Text(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                String text, float x, float y, float scale, Color color,
                Color colorEnd, // null = 纯色，非null = 水平渐变
                TtfFontLoader fontLoader, float originX, float originY,
                float rotationDegrees) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TEXT;
        }
    }

    /**
     * 自由三角形：由三个顶点坐标直接定义，纯色填充。
     * 与 {@link Triangle}（chevron动画三角）互补。
     */
    record FreeTriangle(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                        float x1, float y1, float x2, float y2, float x3, float y3,
                        Color color) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TRIANGLE;
        }
    }

}
