package io.github.openlumin.mixin;

import io.github.openlumin.shaderpack.host.ShaderpackHost;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaderpack 帧钩子（NeoForge 副本，延续"neoforge 自持 mixin 副本"约定）：
 * GameRenderer.render RETURN；仅当 -Dopenlumin.shaderpack=<目录> 时生效，默认零开销。
 */
@Mixin(GameRenderer.class)
public abstract class ShaderpackFrameMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void openlumin$shaderpackFrame(DeltaTracker deltaTracker, boolean renderLevel,
                                           CallbackInfo ci) {
        ShaderpackHost.get().onFrameEnd(deltaTracker);
    }
}
