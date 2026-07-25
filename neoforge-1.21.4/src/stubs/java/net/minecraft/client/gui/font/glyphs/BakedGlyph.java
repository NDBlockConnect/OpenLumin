package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fc;

/**
 * NeoForge适配层：字形渲染接口
 */
public interface BakedGlyph extends TextRenderable {

    default void render(boolean italic, float x, float y, Matrix4fc matrix, VertexConsumer buffer,
                float red, float green, float blue, float alpha, int light) {
        // NeoForge使用不同的API - default 以便实现类无需强制覆盖
    }

    default void render(float x, float y, Matrix4fc matrix, VertexConsumer buffer,
                       float red, float green, float blue, float alpha, int light) {
        render(false, x, y, matrix, buffer, red, green, blue, alpha, light);
    }

    default RenderType renderType() {
        // NeoForge使用不同的RenderType API
        return null;
    }
}
