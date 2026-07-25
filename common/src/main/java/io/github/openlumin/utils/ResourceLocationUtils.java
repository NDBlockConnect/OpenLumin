package io.github.openlumin.utils;

import net.minecraft.resources.ResourceLocation;
import java.nio.ByteBuffer;

/**
 * 跨平台资源加载工具。
 * 各平台模块（neoforge / fabric / forge）可通过注入自己的实现来覆盖此桩。
 */
public class ResourceLocationUtils {

    private static Loader loader = location -> ByteBuffer.allocate(0);

    public static void setLoader(Loader impl) {
        loader = impl;
    }

    public static ByteBuffer loadResource(ResourceLocation location) {
        return loader.load(location);
    }

    @FunctionalInterface
    public interface Loader {
        ByteBuffer load(ResourceLocation location);
    }
}
