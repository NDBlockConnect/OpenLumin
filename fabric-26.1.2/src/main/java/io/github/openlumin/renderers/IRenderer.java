package io.github.openlumin.renderers;

import com.mojang.blaze3d.systems.RenderPass;

public interface IRenderer {

    void draw();

    /**
     * 准备共享绘制，返回是否准备成功
     */
    default boolean prepareSharedDraw() {
        return false;
    }

    /**
     * 使用共享RenderPass进行绘制
     */
    default void draw(RenderPass pass) {
        // 默认实现为空
    }

    /**
     * 一帧内 在 clear() 之后 不能再进行 draw() / drawAndClear()
     */
    void clear();

    /**
     * 一帧内 在 drawAndClear() 之后 不能再进行 draw() / drawAndClear()
     */
    default void drawAndClear() {
        draw();
        clear();
    }

    void close();

}
