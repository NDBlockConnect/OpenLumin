package io.github.openlumin.test;

import io.github.openlumin.Constants;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * 自测渲染器：仅在 -Dopenlumin.selftest=true 或环境变量 OPENLUMIN_SELFTEST=1 时启用。
 *
 * 在 GameRenderer.render 尾部（GUI 之后、present 之前）叠加 2D 测试图元，
 * 覆盖立即模式管线、调度器合批路径与 SDF 形状族；
 * 进入世界后提交玩家轮廓盒与 RGB 坐标轴，由 LevelRendererMixin 于世界渲染末尾 flush。
 *
 * GitHub@NDBlockConnect | BlockConnect@StarsailsClover
 */
public final class SelfTestRenderer {

    private static final boolean ENABLED =
            !Boolean.getBoolean("openlumin.selftest.disabled")
                    && !"0".equals(System.getenv("OPENLUMIN_SELFTEST"));

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final long REPORT_INTERVAL_MS = 5000L;

    private static Render2DScheduler scheduler;
    private static long lastReportMs;

    private SelfTestRenderer() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void renderOverlay() {
        if (!ENABLED) {
            return;
        }
        render2D();
        submit3D(Minecraft.getInstance());
    }

    private static void render2D() {
        // GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        try {
            LuminRenderSystem.applyOrthoProjection();

            float w = LuminRenderSystem.getScaledWidth();
            float h = LuminRenderSystem.getScaledHeight();
            if (w <= 0.0f || h <= 0.0f) {
                return;
            }

            TestRenderUtils.fillRect(IDENTITY, 10, 10, 60, 20, 0xFF4488FF);
            TestRenderUtils.fillRect(IDENTITY, 10, 34, 60, 20, 0xFF44CC55);
            TestRenderUtils.fillRect(IDENTITY, 10, 58, 60, 20, 0x8DFFFFFF);

            if (scheduler == null) {
                scheduler = new Render2DScheduler();
            }
            var layer = scheduler.layer(0);
            layer.addRoundRect(80, 10, 60, 68, 8, new Color(0xFFE07A30));
            layer.addRoundRectGradient(150, 10, 60, 68, 8, 8, 8, 8,
                    new Color(0xFF9B59F0), new Color(0xFF40C4E0),
                    new Color(0xFF40C4E0), new Color(0xFF9B59F0));
            layer.addOutline(220, 10, 60, 68, 8, 2, Color.WHITE);
            layer.addEllipse(80 + 14, 88, 32, 18, new Color(0xFFFFE14D));
            layer.addRect(130, 90, 50, 16, new Color(0x803355FF));
            layer.addTriangle(200, 108, 214, 84, 228, 108, new Color(0xFF50E080));
            scheduler.flushAndClear();
        } catch (Throwable t) {
            report(t, "2D");
        }
    }

    private static void submit3D(Minecraft mc) {
        try {
            if (mc.level == null || mc.player == null) {
                return;
            }
            Render3DScheduler r3 = Render3DScheduler.INSTANCE;
            AABB box = mc.player.getBoundingBox();
            r3.addOutlineBox(box, 0xFFFF3333, 2.0f);
            r3.addFilledBox(box, new Color(0x28FF3333));
            Vec3 c = mc.player.position();
            r3.addLine(c, c.add(3, 0, 0), Color.RED, 1.5f);
            r3.addLine(c, c.add(0, 3, 0), Color.GREEN, 1.5f);
            r3.addLine(c, c.add(0, 0, 3), Color.BLUE, 1.5f);
        } catch (Throwable t) {
            report(t, "3D");
        }
    }

    private static void report(Throwable t, String phase) {
        long now = System.currentTimeMillis();
        if (now - lastReportMs < REPORT_INTERVAL_MS) {
            return;
        }
        lastReportMs = now;
        Constants.LOGGER.error("[OpenLumin-SelfTest] {} render error", phase, t);
    }
}
