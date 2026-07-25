package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.platform.CommandEncoder;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.GpuSampler;
import com.mojang.blaze3d.platform.FilterMode;
import com.mojang.blaze3d.platform.AddressMode;
import com.mojang.blaze3d.platform.GpuDevice;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.state.WindowRenderState;

import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * NeoForge适配层：扩展RenderSystem的方法
 * 这些方法在Fabric 1.21.4存在，但在NeoForge 1.21.4不存在
 */
public class RenderSystemExtensions {

    private static final GpuDevice DUMMY_DEVICE = new GpuDevice();
    private static final DynamicUniformStorage DUMMY_UNIFORMS = new DynamicUniformStorage("dummy", 256, 16);
    private static final WindowRenderState DUMMY_WINDOW_STATE = new WindowRenderState();

    public static GpuDevice getDevice() {
        return DUMMY_DEVICE;
    }

    public static DynamicUniformStorage getDynamicUniforms() {
        return DUMMY_UNIFORMS;
    }

    public static WindowRenderState getWindowRenderState() {
        return DUMMY_WINDOW_STATE;
    }
}
