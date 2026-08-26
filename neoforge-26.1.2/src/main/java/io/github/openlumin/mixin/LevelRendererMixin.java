package io.github.openlumin.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 LevelRenderer.renderLevel() 末尾，在所有世界内容渲染完毕后
 * 执行 Render3DScheduler（轮廓框、填充盒、坐标轴等3D图元）。
 *
 * 26.1.2 适配：renderLevel 描述符变更 —— 相机信息并入 CameraRenderState
 * （位置为 cameraState.pos），矩阵为 Matrix4fc，且新增 ChunkSectionsToRender 参数。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelReturn(
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci
    ) {
        Render3DScheduler.INSTANCE.flush(new Matrix4f(modelViewMatrix), cameraState.pos);
    }
}
