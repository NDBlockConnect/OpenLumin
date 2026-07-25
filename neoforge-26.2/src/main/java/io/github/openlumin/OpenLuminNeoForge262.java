package io.github.openlumin;

import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.renderers.RoundRectRenderer;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import io.github.openlumin.text.ttf.TtfFontLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.nio.file.Path;

/**
 * neoforge-26.2 渲染管线验证入口。
 *
 * 2D 层 (RenderGuiEvent.Post)：
 *   LuminImmediateRenderer / RoundRectRenderer / Render2DScheduler / TtfTextRenderer
 * 3D 层 (RenderLevelStageEvent.AFTER_LEVEL 或 LevelRendererMixin)：
 *   Render3DScheduler — 玩家轮廓框 + 填充半透明盒 + 坐标轴
 *
 * 帧生命周期由钩子统一管理：
 *   HUD 开始 → beginRenderFrame()
 *   HUD 结束 (finally) → endDynamicUniformFrame()  ← 确保每帧重置 UBO 写入指针
 */
@Mod(value = "openlumin", dist = Dist.CLIENT)
public class OpenLuminNeoForge262 {

    // GPU 对象懒加载：必须在 GpuDevice 就绪后（即首帧渲染时）才能创建
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

    // =========================================================================
    // 模组初始化
    // =========================================================================

    public OpenLuminNeoForge262(IEventBus modEventBus, Dist dist) {
        if (dist.isClient()) {
            // 注册客户端事件到游戏事件总线
            NeoForge.EVENT_BUS.register(new ClientEvents());
        }
    }

    // =========================================================================
    // 客户端事件处理器
    // =========================================================================

    public static class ClientEvents {

        /**
         * 2D HUD 渲染钩子（RenderGuiEvent.Post 在所有 GUI 层渲染之后触发）
         * beginRenderFrame / endDynamicUniformFrame 在此统一管理，
         * 业务层（Renderer / Scheduler）无需关心帧生命周期。
         */
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
                // 重置所有 DynamicUniformStorage 写入指针，下帧复用槽位
                LuminRenderSystem.endDynamicUniformFrame();
            }
        }

        /**
         * 3D 世界渲染钩子（备用方案）
         * 主要通过 LevelRendererMixin 实现，此事件处理器作为备用。
         *
         * 注意：RenderLevelStageEvent 在 NeoForge 26.2 中可能不存在或签名不同。
         * 如果编译失败，删除此方法即可（Mixin 已足够）。
         */
        // @SubscribeEvent
        // public void onRenderLevel(RenderLevelStageEvent event) {
        //     if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
        //         // 通过 LevelRendererMixin 处理，此处留空
        //     }
        // }
    }

    // =========================================================================
    // 2D HUD 渲染测试
    // =========================================================================

    private static void render2D(int screenW, int screenH) {
        LuminRenderSystem.applyOrthoProjection();
        Matrix4f matrix = RenderSystem.getModelViewMatrix();
        float x = screenW - 220f;
        float y = 10f;

        // 1-3. LuminImmediateRenderer — PosColor 矩形（蓝/绿/半透明白）
        fillRect(matrix, x, y,        200f, 40f, 0xFF4488FF);
        fillRect(matrix, x, y + 50f,  200f, 40f, 0xFF44CC55);
        fillRect(matrix, x, y + 100f, 200f, 40f, 0x8DFFFFFF);

        // 4-6. RoundRectRenderer — 圆角矩形 / 渐变 / 独立圆角
        roundRects().addRoundRect(x, y + 155f, 200f, 40f, 10f, new Color(0xFF, 0x88, 0x22));
        roundRects().addVerticalGradient(x, y + 205f, 200f, 40f, 6f,
                new Color(0xAA, 0x44, 0xFF), new Color(0x22, 0xCC, 0xFF));
        roundRects().addRoundRect(x, y + 255f, 200f, 40f, 14f, 0f, 14f, 0f,
                new Color(0x55, 0xFF, 0x88));
        roundRects().draw();
        roundRects().clear();

        // 7-11. Render2DScheduler — RoundRect / Ellipse / Rect / 文字
        var layer = scheduler().layer(0);
        layer.addRoundRect(x, y + 310f, 200f, 40f, 8f,  new Color(0xDD, 0x33, 0x33));
        layer.addEllipse(x,  y + 360f, 200f, 40f,       new Color(0xFF, 0xCC, 0x00));
        layer.addRect(x,     y + 410f, 200f, 40f,       new Color(0x33, 0xAA, 0xFF, 0xCC));
        TtfFontLoader.beginRenderFrame();
        layer.addText("OpenLumin 26.2", x, y + 460f, 1.0f, Color.WHITE, testFont());
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
