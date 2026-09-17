package io.github.openlumin.utils;

import io.github.openlumin.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * 资源加载工具（26.2 适配版）。
 *
 * 之前的实现返回空 ByteBuffer（NeoForge 适配未完成），会把 0 字节缓冲交给
 * STBTruetype.stbtt_InitFont 造成 JVM 原生崩溃（EXCEPTION_ACCESS_VIOLATION）。
 * 现在通过客户端 ResourceManager 正确读取 mod 资源。
 *
 * GitHub@NDBlockConnect | BlockConnect@StarsailsClover
 */
public class ResourceLocationUtils {

    private ResourceLocationUtils() {
    }

    /**
     * 通过客户端 {@code ResourceManager} 读取资源为 {@code ByteBuffer}（native 友好）。
     *
     * @throws IOException 资源不存在或读取失败（调用方必须处理，绝不能把空缓冲交给 native）
     */
    public static ByteBuffer loadResource(Identifier location) throws IOException {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourceManager() == null) {
            throw new IOException("ResourceManager not available for " + location);
        }
        Optional<Resource> resource = client.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("Resource not found: " + location);
        }
        try (InputStream stream = resource.get().open()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                out.write(chunk, 0, read);
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                throw new IOException("Resource is empty: " + location);
            }
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to read resource {}", location, e);
            throw new IOException("Failed to read resource: " + location, e);
        }
    }
}
