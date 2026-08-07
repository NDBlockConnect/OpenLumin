package io.github.openlumin.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 LevelRenderer.render() 末尾（26.2 描述符：8 参，含 CameraRenderState 与 Matrix4fc），
 * 在所有世界内容渲染完毕后执行 Render3DScheduler。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderLevelReturn(
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        Render3DScheduler.INSTANCE.flush(new Matrix4f(modelViewMatrix), cameraState.pos);
    }
}
