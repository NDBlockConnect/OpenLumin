package io.github.openlumin.mixin;

import io.github.openlumin.Constants;
import io.github.openlumin.test.SelfTestRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自测钩子（26.2 诊断版）：注入 GameRenderer.render RETURN（blit 之前），
 * 绘制应随 blitFromTexture 一起合成到窗口表面。
 * 仅在 -Dopenlumin.selftest.disabled=true 或 OPENLUMIN_SELFTEST=0 时关闭。
 *
 * GitHub@NDBlockConnect | BlockConnect@StarsailsClover
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSelfTestMixin {

    static {
        Constants.LOGGER.info("[OpenLumin-SelfTest] mixin class constructed");
    }

    private static boolean loggedFirstFire;

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At("RETURN")
    )
    private void openlumin$selfTestOverlay(net.minecraft.client.DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!loggedFirstFire) {
            loggedFirstFire = true;
            Constants.LOGGER.info("[OpenLumin-SelfTest] GameRenderer.render hook fired");
        }
        SelfTestRenderer.renderOverlay();
    }
}
