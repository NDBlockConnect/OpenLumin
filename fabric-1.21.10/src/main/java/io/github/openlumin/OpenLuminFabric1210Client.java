package io.github.openlumin;

import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.renderers.RoundRectRenderer;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import io.github.openlumin.text.ttf.TtfFontLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.nio.file.Path;

/**
 * fabric-1.21.10 渲染管线验证入口。
 *
 * 2D 层 (HudRenderCallback)：
 *   LuminImmediateRenderer / RoundRectRenderer / Render2DScheduler / TtfTextRenderer
 * 3D 层 (LevelRendererMixin)：
 *   Render3DScheduler — 玩家轮廓框 + 填充半透明盒 + 坐标轴
 *
 * 帧生命周期由 RenderSystemMixin 统一管理：
 *   flipFrame 后 → LuminImmediateRenderer.endFrame()
 *               → LuminRenderSystem.endDynamicUniformFrame()
 *               → LuminRenderSystem.beginRenderFrame()
 */
public class OpenLuminFabric1210Client implements ClientModInitializer {

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

    @Override
    public void onInitializeClient() {
        System.out.println("[OpenLumin] fabric-1.21.10 mod initialized");

        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                render2D(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } catch (Throwable e) {
                System.err.println("[OpenLumin] 2D render error: " + e);
                e.printStackTrace();
            }
        });
    }

    private static void render2D(int screenW, int screenH) {
        LuminRenderSystem.applyOrthoProjection();
        Matrix4f matrix = RenderSystem.getModelViewMatrix();
        float x = screenW - 220f;
        float y = 10f;

        fillRect(matrix, x, y,        200f, 40f, 0xFF4488FF);
        fillRect(matrix, x, y + 50f,  200f, 40f, 0xFF44CC55);
        fillRect(matrix, x, y + 100f, 200f, 40f, 0x8DFFFFFF);

        roundRects().addRoundRect(x, y + 155f, 200f, 40f, 10f, new Color(0xFF, 0x88, 0x22));
        roundRects().addVerticalGradient(x, y + 205f, 200f, 40f, 6f,
                new Color(0xAA, 0x44, 0xFF), new Color(0x22, 0xCC, 0xFF));
        roundRects().addRoundRect(x, y + 255f, 200f, 40f, 14f, 0f, 14f, 0f,
                new Color(0x55, 0xFF, 0x88));
        roundRects().draw();
        roundRects().clear();

        var layer = scheduler().layer(0);
        layer.addRoundRect(x, y + 310f, 200f, 40f, 8f,  new Color(0xDD, 0x33, 0x33));
        layer.addEllipse(x,  y + 360f, 200f, 40f,       new Color(0xFF, 0xCC, 0x00));
        layer.addRect(x,     y + 410f, 200f, 40f,       new Color(0x33, 0xAA, 0xFF, 0xCC));
        TtfFontLoader.beginRenderFrame();
        layer.addText("OpenLumin 1.21.10", x, y + 460f, 1.0f, Color.WHITE, testFont());
        layer.addGradientText("Gradient Text", x, y + 490f, 1.0f,
                new Color(0xFF, 0x88, 0x00), new Color(0x00, 0xCC, 0xFF), testFont());
        scheduler().flushAndClear();

        // 12-14. Render3DScheduler — 在此 schedule，由 LevelRendererMixin 在 renderLevel 末尾 flush
        Minecraft mc3d = Minecraft.getInstance();
        if (mc3d.player != null) {
            AABB playerBox = mc3d.player.getBoundingBox().inflate(0.3);
            Render3DScheduler.INSTANCE.addOutlineBox(playerBox, 0xFFFF3333, 2.0f);
            Render3DScheduler.INSTANCE.addFilledBox(playerBox, new Color(0xFF, 0x33, 0x33, 0x44));
            Vec3 center = mc3d.player.position().add(0, mc3d.player.getBbHeight() / 2.0, 0);
            Render3DScheduler.INSTANCE.addLine(center, center.add(3, 0, 0), new Color(0xFF, 0x44, 0x44), 1.5f);
            Render3DScheduler.INSTANCE.addLine(center, center.add(0, 3, 0), new Color(0x44, 0xFF, 0x44), 1.5f);
            Render3DScheduler.INSTANCE.addLine(center, center.add(0, 0, 3), new Color(0x44, 0x44, 0xFF), 1.5f);
        }
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
