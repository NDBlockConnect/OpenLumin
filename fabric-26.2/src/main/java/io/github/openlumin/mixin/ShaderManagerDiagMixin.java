package io.github.openlumin.mixin;

import io.github.openlumin.Constants;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bring-up diagnostic for the 26.2 shader pipeline: dumps the shader-source keys that
 * {@code ShaderManager.apply} received after the resource reload, so a missing
 * {@code openlumin:*} entry can be told apart from a lookup failure.
 *
 * <p>Enabled with {@code -Dopenlumin.diag.shaderdump=true}; inert otherwise.
 *
 * <p>The injection is marked {@code require = 0}: this is an opt-in diagnostic, so a future
 * change to the target signature must degrade to "no dump" rather than fail mod loading.</p>
 *
 * <p>GitHub@NDBlockConnect | BlockConnect@StarsailsClover
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerDiagMixin {

    @Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;"
            + "Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"),
            require = 0)
    private void openlumin$dumpShaderSources(ShaderManager.Configs configs,
                                             net.minecraft.server.packs.resources.ResourceManager resources,
                                             net.minecraft.util.profiling.ProfilerFiller profiler,
                                             CallbackInfo ci) {
        if (!Boolean.getBoolean("openlumin.diag.shaderdump")) {
            return;
        }
        try {
            java.util.Map<?, ?> sources = (java.util.Map<?, ?>) (Object) configs.shaderSources();
            Constants.LOGGER.info("[OpenLumin-Diag] shaderSources size={}", sources.size());
            int shown = 0;
            for (Object key : sources.keySet()) {
                if (shown++ >= 200) {
                    Constants.LOGGER.info("[OpenLumin-Diag] ... (truncated)");
                    break;
                }
                Constants.LOGGER.info("[OpenLumin-Diag] key={}", key);
            }
            boolean hasRR = sources.keySet().stream().map(String::valueOf)
                    .anyMatch(k -> k.contains("round_rectangle"));
            Constants.LOGGER.info("[OpenLumin-Diag] contains round_rectangle={}", hasRR);
        } catch (Throwable t) {
            Constants.LOGGER.error("[OpenLumin-Diag] dump failed", t);
        }
    }
}
