package io.github.openlumin.mixin;

import io.github.openlumin.test.SelfTestRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自测钩子：在 GameRenderer.render 尾部叠加 OpenLumin 自测图元。
 * 仅在 -Dopenlumin.selftest=true 或 OPENLUMIN_SELFTEST=1 时生效，默认零开销。
 *
 * GitHub@NDBlockConnect | BlockConnect@StarsailsClover
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSelfTestMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void openlumin$selfTestOverlay(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        SelfTestRenderer.renderOverlay();
    }
}
