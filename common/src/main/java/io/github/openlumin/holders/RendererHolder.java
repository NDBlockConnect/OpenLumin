package io.github.openlumin.holders;

import io.github.openlumin.renderers.IRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer 生命周期管理器
 * 统一管理所有 OpenLumin renderer 的注册、注销和销毁
 */
public class RendererHolder {

    public static final RendererHolder INSTANCE = new RendererHolder();

    private final List<IRenderer> renderers = new ArrayList<>();

    private RendererHolder() {
    }

    /**
     * 注册一个 renderer 到全局管理器
     * @param renderer 要注册的 renderer
     * @return 注册的 renderer 本身（方便链式调用）
     */
    public <T extends IRenderer> T register(T renderer) {
        renderers.add(renderer);
        return renderer;
    }

    /**
     * 从全局管理器注销 renderer
     * @param renderer 要注销的 renderer
     */
    public void unregister(IRenderer renderer) {
        renderers.remove(renderer);
    }

    /**
     * 销毁所有已注册的 renderer，释放资源
     * 通常在游戏关闭或资源重载时调用
     */
    public void destroyAll() {
        for (IRenderer renderer : renderers) {
            try {
                renderer.close();
            } catch (Exception e) {
                // 忽略单个 renderer 关闭时的异常，继续清理其他 renderer
            }
        }
        renderers.clear();
    }

}
