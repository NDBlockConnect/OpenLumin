package net.minecraft.client.renderer.state;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * NeoForge适配层：模拟Fabric的WindowRenderState API
 */
public class WindowRenderState {

    private final double guiScale;
    private final int scaledWidth;
    private final int scaledHeight;
    private final int framebufferWidth;
    private final int framebufferHeight;

    public WindowRenderState() {
        var window = Minecraft.getInstance().getWindow();
        this.guiScale = window.getGuiScale();
        this.scaledWidth = window.getGuiScaledWidth();
        this.scaledHeight = window.getGuiScaledHeight();
        this.framebufferWidth = window.getWidth();
        this.framebufferHeight = window.getHeight();
    }

    public static WindowRenderState fromWindow() {
        return new WindowRenderState();
    }

    public double guiScale() {
        return guiScale;
    }

    public int scaledWidth() {
        return scaledWidth;
    }

    public int scaledHeight() {
        return scaledHeight;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }
}
