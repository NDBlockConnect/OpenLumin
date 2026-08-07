package io.github.openlumin.mixin;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.immediate.LuminImmediateRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2：flipFrame 已移除，帧生命周期钩子改注入 GameRenderer.render 末尾。
 */
@Mixin(GameRenderer.class)
public abstract class RenderSystemMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void onFrameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        LuminImmediateRenderer.endFrame();
        LuminRenderSystem.endDynamicUniformFrame();
    }
}
