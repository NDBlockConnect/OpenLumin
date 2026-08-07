package io.github.openlumin.test;

import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.renderers.RoundRectRenderer;
import org.joml.Matrix4f;

/**
 * 测试渲染工具类 - 提供跨模块共享的测试辅助方法
 */
public class TestRenderUtils {

    private static RoundRectRenderer roundRectRenderer;

    /**
     * 获取共享的 RoundRectRenderer 实例
     */
    public static RoundRectRenderer getRoundRects() {
        if (roundRectRenderer == null) {
            roundRectRenderer = RoundRectRenderer.create();
        }
        return roundRectRenderer;
    }

    /**
     * 绘制纯色填充矩形
     */
    public static void fillRect(Matrix4f matrix, float x, float y, float w, float h, int color) {
        LuminImmediateRenderer.PosColorQuads builder =
                LuminImmediateRenderer.beginPosColorQuads(LuminRenderPipelines.RECTANGLE);
        builder.vertex(matrix, x,     y,     0f, color);
        builder.vertex(matrix, x,     y + h, 0f, color);
        builder.vertex(matrix, x + w, y + h, 0f, color);
        builder.vertex(matrix, x + w, y,     0f, color);
        builder.end();
    }
}
