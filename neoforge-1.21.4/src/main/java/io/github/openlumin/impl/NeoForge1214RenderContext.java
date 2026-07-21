package io.github.openlumin.impl;

import io.github.openlumin.api.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.WindowRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/**
 * NeoForge 1.21.4 渲染上下文实现
 * 使用 WindowRenderState (1.21.4+ 特有)
 */
public class NeoForge1214RenderContext implements RenderContext {

    @Override
    public double getGuiScale() {
        // NeoForge不支持RenderSystem.getWindowRenderState()
        // WindowRenderState windowState = RenderSystem.getWindowRenderState();
        // return windowState != null ? windowState.guiScale() : 1.0;
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    public float getScaledWidth() {
        // NeoForge不支持RenderSystem.getWindowRenderState()
        // WindowRenderState windowState = RenderSystem.getWindowRenderState();
        // return windowState != null ? (float) windowState.scaledWidth() :
        //        Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return (float) Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public float getScaledHeight() {
        // NeoForge不支持RenderSystem.getWindowRenderState()
        // WindowRenderState windowState = RenderSystem.getWindowRenderState();
        // return windowState != null ? (float) windowState.scaledHeight() :
        //        Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
        // NeoForge不支持RenderSystem.getModelViewStack()
        // RenderSystem.getModelViewStack().mulPose(
        //     new org.joml.Quaternionf().rotateAxis(
        //         (float) Math.toRadians(angle), x, y, z
        //     )
        // );
    }
}
