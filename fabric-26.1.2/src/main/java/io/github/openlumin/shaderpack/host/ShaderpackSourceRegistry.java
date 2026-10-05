package io.github.openlumin.shaderpack.host;

import com.mojang.blaze3d.shaders.ShaderType;
import io.github.openlumin.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenLumin 着色器源注入（WP-2 平台层）：
 * 26.1.2 的 {@code ShaderManager} 在资源重载期**一次性收集**着色器源
 * （{@code ResourceManager.listResources("shaders", ...)}），自定义命名空间资源在该路径下
 * 不可达（1.21.10 为惰性读取故无此问题——1.21.10 实例日志对照 0 错误 vs 26.1.2 全错误）。
 *
 * <p>本注册表 + {@code ShaderManagerSourceMixin} 为 {@code openlumin} 命名空间提供兜底源：
 * 引擎自带着色器经类加载器直接读 mod 资源；shaderpack 生成源由宿主在编译后注册。</p>
 */
public final class ShaderpackSourceRegistry {

    private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();
    /** {@code #moj_import <namespace:path>} 指令行。 */
    private static final Pattern MOJ_IMPORT = Pattern.compile(
            "(?m)^[ \\t]*#moj_import\\s*<([^>]+)>[ \\t]*$");
    /** include 内嵌的 {@code #version} 行（内联时须剥离，否则主源 #version 之后出现 → 编译错误）。 */
    private static final Pattern NESTED_VERSION = Pattern.compile(
            "(?m)^[ \\t]*#version[^\\n]*\\n?");
    private static final int MAX_IMPORT_DEPTH = 16;

    private ShaderpackSourceRegistry() {
    }

    /** 注册（覆盖）一批着色器源；键 = {@code shaders/<path><ext>}（与部署表一致）。 */
    public static void register(Map<String, String> resources) {
        SOURCES.putAll(resources);
    }

    public static void clear() {
        SOURCES.clear();
    }

    /**
     * 解析着色器源：注册表优先 → 类加载器读 mod 自带资源 → null（交给原版路径）。
     */
    public static String resolve(Identifier id, ShaderType type) {
        String extension = extensionOf(type);
        if (extension == null) {
            return null;
        }
        String key = "shaders/" + id.getPath() + extension;
        String registered = SOURCES.get(key);
        if (registered != null) {
            return expandImports(registered, 0);
        }
        try (InputStream stream = ShaderpackSourceRegistry.class
                .getResourceAsStream("/assets/openlumin/" + key)) {
            if (stream == null) {
                return null;
            }
            return expandImports(new String(stream.readAllBytes(), StandardCharsets.UTF_8), 0);
        } catch (IOException failure) {
            Constants.LOGGER.warn("[OpenLumin-Shaderpack] failed to read built-in shader {}",
                    key, failure);
            return null;
        }
    }

    /**
     * 展开 {@code #moj_import}：注入源绕过了 MC 的资源重载期预处理，此处自实现等价展开
     * （递归内联；找不到的 include 保持原样以便编译期显式报错）。
     */
    static String expandImports(String source, int depth) {
        if (depth >= MAX_IMPORT_DEPTH || !source.contains("#moj_import")) {
            return source;
        }
        Matcher matcher = MOJ_IMPORT.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String include = readInclude(matcher.group(1).trim());
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    include == null ? matcher.group(0) : NESTED_VERSION.matcher(include).replaceAll("")));
        }
        matcher.appendTail(out);
        return expandImports(out.toString(), depth + 1);
    }

    private static String readInclude(String reference) {
        int colon = reference.indexOf(':');
        String namespace = colon > 0 ? reference.substring(0, colon) : "minecraft";
        String path = colon > 0 ? reference.substring(colon + 1) : reference;
        String resourcePath = "shaders/include/" + path;
        String registered = SOURCES.get(resourcePath);
        if (registered != null) {
            return registered;
        }
        try (InputStream stream = ShaderpackSourceRegistry.class
                .getResourceAsStream("/assets/" + namespace + "/" + resourcePath)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // 继续尝试资源管理器
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                var resource = minecraft.getResourceManager().getResource(
                        Identifier.fromNamespaceAndPath(namespace, resourcePath));
                if (resource.isPresent()) {
                    try (BufferedReader reader = resource.get().openAsReader()) {
                        StringBuilder text = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            text.append(line).append('\n');
                        }
                        return text.toString();
                    }
                }
            }
        } catch (Throwable ignored) {
            // 资源管理器不可用时按缺失处理
        }
        return null;
    }

    /** ShaderType → 扩展名（26.1.2 仅 VERTEX/FRAGMENT）。 */
    public static String extensionOf(ShaderType type) {
        if (type == ShaderType.VERTEX) {
            return ".vsh";
        }
        if (type == ShaderType.FRAGMENT) {
            return ".fsh";
        }
        return null;
    }
}
