package io.github.openlumin;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.renderers.RoundRectRenderer;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.text.ttf.TtfFontLoader;

import java.awt.Color;
import java.nio.file.Path;

/**
 * forge-1.21.10 渲染管线入口（OpenGL stubs 路径，编译占位）。
 * 注意：forge-1.21.10 使用旧版 Forge API，现代 GPU 路径通过 stubs 确保编译通过。
 */
@Mod("openlumin")
public class OpenLuminForge2110 {

    private static RoundRectRenderer roundRectRenderer;
    private static Render2DScheduler scheduler;
    private static TtfFontLoader testFont;

    private static RoundRectRenderer roundRects() {
        if (roundRectRenderer == null) roundRectRenderer = RoundRectRenderer.create();
        return roundRectRenderer;
    }

    private static Render2DScheduler scheduler() {
        if (scheduler == null) scheduler = new Render2DScheduler();
        return scheduler;
    }

    private static TtfFontLoader testFont() {
        if (testFont == null) {
            testFont = new TtfFontLoader(Path.of("C:\\Windows\\Fonts\\arial.ttf"));
        }
        return testFont;
    }

    public OpenLuminForge2110(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    public static class ClientEvents {

        @SubscribeEvent
        public void onRenderGui(RenderGuiEvent.Post event) {
            LuminRenderSystem.beginRenderFrame();
            try {
                Minecraft mc = Minecraft.getInstance();
                render2D(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } catch (Throwable e) {
                System.err.println("[OpenLumin] 2D render error: " + e);
                e.printStackTrace();
            } finally {
                LuminRenderSystem.endDynamicUniformFrame();
            }
        }
    }

    private static void render2D(int screenW, int screenH) {
        LuminRenderSystem.applyOrthoProjection();
        Matrix4f matrix = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
        float x = screenW - 220f;
        float y = 10f;

        fillRect(matrix, x, y,        200f, 40f, 0xFF4488FF);
        fillRect(matrix, x, y + 50f,  200f, 40f, 0xFF44CC55);

        roundRects().addRoundRect(x, y + 105f, 200f, 40f, 10f, new Color(0xFF, 0x88, 0x22));
        roundRects().draw();
        roundRects().clear();

        var layer = scheduler().layer(0);
        layer.addRoundRect(x, y + 155f, 200f, 40f, 8f, new Color(0xDD, 0x33, 0x33));
        TtfFontLoader.beginRenderFrame();
        layer.addText("OpenLumin Forge 1.21.10", x, y + 205f, 1.0f, Color.WHITE, testFont());
        scheduler().flushAndClear();
    }

    private static void fillRect(Matrix4f matrix, float x, float y, float w, float h, int color) {
        LuminImmediateRenderer.PosColorQuads builder =
                LuminImmediateRenderer.beginPosColorQuads(LuminRenderPipelines.RECTANGLE);
        builder.vertex(matrix, x,     y,     0f, color);
        builder.vertex(matrix, x,     y + h, 0f, color);
        builder.vertex(matrix, x + w, y + h, 0f, color);
        builder.vertex(matrix, x + w, y,     0f, color);
        builder.end();
    }
}
