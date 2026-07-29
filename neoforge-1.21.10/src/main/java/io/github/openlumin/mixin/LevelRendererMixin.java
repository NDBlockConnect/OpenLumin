package io.github.openlumin.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 LevelRenderer.renderLevel() 末尾，在所有世界内容渲染完毕后
 * 执行 Render3DScheduler（轮廓框、填充盒、坐标轴等3D图元）。
 *
 * 1.21.10 的 renderLevel 已不含 PoseStack 参数，因此将本次世界渲染使用的
 * modelViewMatrix 与相机位置显式传给 Render3DScheduler，避免 RETURN 时读取到已恢复的 HUD 矩阵。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelReturn(
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            GpuBufferSlice gbufferSlice,
            Vector4f fogColor,
            boolean isFoggy,
            CallbackInfo ci
    ) {
        if (!Render3DScheduler.INSTANCE.isEmpty()) {
            Render3DScheduler.INSTANCE.flush(modelViewMatrix, camera.getPosition());
        }
    }
}
