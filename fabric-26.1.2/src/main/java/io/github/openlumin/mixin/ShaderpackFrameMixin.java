package io.github.openlumin.mixin;

import io.github.openlumin.shaderpack.host.ShaderpackHost;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaderpack 帧钩子：GameRenderer.render RETURN（GUI 之后、present 之前）。
 *
 * <p>仅当 {@code -Dopenlumin.shaderpack=<目录>} 存在时生效（{@link ShaderpackHost#enabled()}），
 * 未启用时调用即返回（零开销）。过渡时序：世界+GUI 完成后合成并写回主目标；
 * 正式"世界后/GUI 前"时序随 M7 接线。</p>
 */
@Mixin(GameRenderer.class)
public abstract class ShaderpackFrameMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void openlumin$shaderpackFrame(DeltaTracker deltaTracker, boolean renderLevel,
                                           CallbackInfo ci) {
        ShaderpackHost.get().onFrameEnd(deltaTracker);
    }
}
