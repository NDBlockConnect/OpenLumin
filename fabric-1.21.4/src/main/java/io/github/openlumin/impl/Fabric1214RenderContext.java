package io.github.openlumin.impl;

import io.github.openlumin.api.RenderContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Fabric 1.21.4 渲染上下文实现。
 * <p>
 * MC 1.21.4 尚未引入 WindowRenderState，使用 Window 直接读取 GUI 缩放。
 */
public class Fabric1214RenderContext implements RenderContext {

    @Override
    public double getGuiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    public float getScaledWidth() {
        return (float) Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public float getScaledHeight() {
        return (float) Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public int getFramebufferWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    @Override
    public int getFramebufferHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    @Override
    public void pushMatrix() {
        RenderSystem.getModelViewStack().pushMatrix();
    }

    @Override
    public void popMatrix() {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        RenderSystem.getModelViewStack().translate((float) x, (float) y, (float) z);
    }

    @Override
    public void scale(double x, double y, double z) {
        RenderSystem.getModelViewStack().scale((float) x, (float) y, (float) z);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        // Matrix4fStack.rotate() 接受弧度制
        RenderSystem.getModelViewStack().rotate(
                (float) Math.toRadians(angle), x, y, z
        );
    }
}
