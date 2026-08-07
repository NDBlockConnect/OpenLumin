package io.github.openlumin.utils;

import net.minecraft.resources.Identifier;
import java.nio.ByteBuffer;

/**
 * NeoForge适配层：资源加载工具
 */
public class ResourceLocationUtils {

    public static ByteBuffer loadResource(Identifier location) {
        // NeoForge使用不同的资源加载API
        // 这里返回一个占位值
        return ByteBuffer.allocate(0);
    }
}
