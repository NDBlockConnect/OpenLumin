package io.github.openlumin.mixin;

import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.immediate.LuminImmediateRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 适配：flipFrame 签名变为单参 (TracyFrameCapture)。
 * 在 DynamicUniforms.reset() 之后推进帧生命周期。
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {

    @Inject(
            method = "flipFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;reset()V",
                    shift = At.Shift.AFTER
            )
    )
    private static void onFrameEnd(TracyFrameCapture frameCapture, CallbackInfo ci) {
        LuminImmediateRenderer.endFrame();
        LuminRenderSystem.endDynamicUniformFrame();
    }
}
