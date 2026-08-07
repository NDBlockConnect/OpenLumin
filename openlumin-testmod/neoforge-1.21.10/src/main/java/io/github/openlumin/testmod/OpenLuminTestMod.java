package io.github.openlumin.testmod;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import io.github.openlumin.text.ttf.TtfFontLoader;
import io.github.openlumin.test.TestRenderUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.nio.file.Path;

import static io.github.openlumin.test.TestRenderUtils.*;

/**
 * OpenLumin 测试模组 - NeoForge 1.21.10
 *
 * 验证 OpenLumin 渲染功能：
 * - 2D 渲染：矩形、圆角矩形、椭圆、文字
 * - 3D 渲染：线框盒子、填充盒子、坐标轴
 */
@Mod("openlumin_testmod")
public class OpenLuminTestMod {

    private static Render2DScheduler scheduler;
    private static TtfFontLoader testFont;

    public OpenLuminTestMod() {
        System.out.println("[OpenLumin-TestMod] Initializing test cases for neoforge-1.21.10");
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

    @EventBusSubscriber(modid = "openlumin_testmod", value = Dist.CLIENT)
    public static class ClientEvents {

        private static int dbgFrameCount = 0;

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            dbgFrameCount++;
            if (dbgFrameCount <= 5 || dbgFrameCount % 200 == 0) {
                System.out.println("[OpenLumin-TestMod] onRenderGui fired frame=" + dbgFrameCount);
            }
            try {
                Minecraft mc = Minecraft.getInstance();
                render2D(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } catch (Throwable e) {
                System.err.println("[OpenLumin-TestMod] 2D render error: " + e);
                e.printStackTrace();
            }
        }
    }

    private static void render2D(int screenW, int screenH) {
        LuminRenderSystem.applyOrthoProjection();
        Matrix4f matrix = RenderSystem.getModelViewMatrix();
        float x = screenW - 220f;
        float y = 10f;

        fillRect(matrix, x, y,        200f, 40f, 0xFF4488FF);
        fillRect(matrix, x, y + 50f,  200f, 40f, 0xFF44CC55);
        fillRect(matrix, x, y + 100f, 200f, 40f, 0x8DFFFFFF);

        getRoundRects().addRoundRect(x, y + 155f, 200f, 40f, 10f, new Color(0xFF, 0x88, 0x22));
        getRoundRects().addVerticalGradient(x, y + 205f, 200f, 40f, 6f,
                new Color(0xAA, 0x44, 0xFF), new Color(0x22, 0xCC, 0xFF));
        getRoundRects().addRoundRect(x, y + 255f, 200f, 40f, 14f, 0f, 14f, 0f,
                new Color(0x55, 0xFF, 0x88));
        getRoundRects().draw();
        getRoundRects().clear();

        var layer = scheduler().layer(0);
        layer.addRoundRect(x, y + 310f, 200f, 40f, 8f,  new Color(0xDD, 0x33, 0x33));
        layer.addEllipse(x,  y + 360f, 200f, 40f,       new Color(0xFF, 0xCC, 0x00));
        layer.addRect(x,     y + 410f, 200f, 40f,       new Color(0x33, 0xAA, 0xFF, 0xCC));

        TtfFontLoader.beginRenderFrame();
        layer.addText("OpenLumin 1.21.10", x, y + 460f, 1.0f, Color.WHITE, testFont());
        layer.addGradientText("Gradient Text", x, y + 490f, 1.0f,
                new Color(0xFF, 0x88, 0x00), new Color(0x00, 0xCC, 0xFF), testFont());
        scheduler().flushAndClear();

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
}
