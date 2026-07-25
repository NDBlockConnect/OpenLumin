package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.DynamicUniforms;

/**
 * fabric-1.21.10 实现：直接委托到原生 1.21.10 RenderSystem API。
 * 替代 neoforge-1.21.4/src/stubs/java 中的同名桩类。
 *
 * <p>getDevice() 和 getDynamicUniforms() 在 1.21.6+ 均是 RenderSystem 的原生静态方法。
 * 无需任何适配器，直接转发即可。</p>
 */
public class RenderSystemExtensions {

    private RenderSystemExtensions() {}

    /** 返回原生 GpuDevice — 与 1.21.4 桩类接口完全兼容。 */
    public static GpuDevice getDevice() {
        return RenderSystem.getDevice();
    }

    /**
     * 返回原生 DynamicUniforms。
     * <p>注意：原生 {@code DynamicUniforms.writeTransform()} 比 1.21.4 桩多一个
     * {@code float lineWidth} 参数，调用方需补上 {@code 1.0f}。</p>
     */
    public static DynamicUniforms getDynamicUniforms() {
        return RenderSystem.getDynamicUniforms();
    }
}
