package io.github.openlumin.compat;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.DynamicUniforms;

/**
 * RenderSystem 的兼容性封装，避免与 Mojang 包命名冲突。
 * 直接委托到原生 Minecraft 1.21.10 RenderSystem API。
 */
public class RenderSystemShim {

    private RenderSystemShim() {}

    /** 返回原生 GpuDevice */
    public static GpuDevice getDevice() {
        return RenderSystem.getDevice();
    }

    /** 返回原生 DynamicUniforms */
    public static DynamicUniforms getDynamicUniforms() {
        return RenderSystem.getDynamicUniforms();
    }
}
