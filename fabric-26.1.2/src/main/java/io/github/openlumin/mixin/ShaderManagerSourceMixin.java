package io.github.openlumin.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import io.github.openlumin.shaderpack.host.ShaderpackSourceRegistry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 着色器源兜底注入：为 {@code openlumin} 命名空间提供源（26.1.2 的资源收集路径
 * 对自定义命名空间不可达——见 {@link ShaderpackSourceRegistry} 说明）。
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class ShaderManagerSourceMixin {

    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true)
    private void openlumin$provideSource(Identifier id, ShaderType type,
                                         CallbackInfoReturnable<String> cir) {
        if (!"openlumin".equals(id.getNamespace())) {
            return;
        }
        String source = ShaderpackSourceRegistry.resolve(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }
}
