package io.github.openlumin.holders;

import io.github.openlumin.LuminTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public class TextureCacheHolder {

    public static final TextureCacheHolder INSTANCE = new TextureCacheHolder();

    // LRU 缓存最大容量，超出后淘汰最少使用的纹理
    private static final int MAX_CACHE_SIZE = 256;

    private TextureCacheHolder() {
    }

    // 使用 LinkedHashMap 实现 LRU 缓存，访问顺序模式
    public final Map<Identifier, LuminTexture> textureCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, LuminTexture> eldest) {
            if (size() > MAX_CACHE_SIZE) {
                // 关闭被淘汰的纹理，释放 GPU 资源
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    public void clearCache() {
        for (LuminTexture texture : textureCache.values()) {
            texture.close();
        }
        textureCache.clear();
    }

}
