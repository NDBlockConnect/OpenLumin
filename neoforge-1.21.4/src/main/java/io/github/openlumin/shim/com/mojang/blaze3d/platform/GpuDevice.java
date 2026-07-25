package io.github.openlumin.shim.com.mojang.blaze3d.platform;

import io.github.openlumin.shim.com.mojang.blaze3d.platform.CommandEncoder;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuBuffer;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTexture;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuTextureView;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.GpuSampler;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.FilterMode;
import io.github.openlumin.shim.com.mojang.blaze3d.platform.AddressMode;

import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * NeoForge适配层：模拟Fabric的GpuDevice API
 */
public class GpuDevice {

    public io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBuffer createBuffer(Supplier<String> name, int usage, long size) {
        // NeoForge使用不同的API
        return new io.github.openlumin.shim.com.mojang.blaze3d.buffers.GpuBuffer((int) size, usage);
    }

    public GpuTexture createTexture(String name, int usage, TextureFormat format, int width, int height, int depth, int mipLevels) {
        // NeoForge使用不同的API
        return new GpuTexture(width, height, usage);
    }

    /** TtfGlyphAtlas使用Supplier<String>作为label */
    public GpuTexture createTexture(java.util.function.Supplier<String> name, int usage, TextureFormat format, int width, int height, int depth, int mipLevels) {
        // NeoForge使用不同的API
        return new GpuTexture(width, height, usage);
    }

    public GpuTextureView createTextureView(GpuTexture texture) {
        // NeoForge使用不同的API
        return new GpuTextureView();
    }

    public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy, OptionalDouble lodBias) {
        // NeoForge使用不同的API
        return new GpuSampler();
    }

    public CommandEncoder createCommandEncoder() {
        // NeoForge使用不同的API
        return new CommandEncoder();
    }
}
