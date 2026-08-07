package io.github.openlumin.holders;

import io.github.openlumin.renderers.IRenderer;

import java.util.ArrayList;
import java.util.List;

public class RendererHolder {

    public static final RendererHolder INSTANCE = new RendererHolder();

    private final List<IRenderer> renderers = new ArrayList<>();

    private RendererHolder() {
    }

    public <T extends IRenderer> T register(T renderer) {
        renderers.add(renderer);
        return renderer;
    }

    public void unregister(IRenderer renderer) {
        renderers.remove(renderer);
    }

    public void destroyAll() {
        for (IRenderer renderer : renderers) {
            renderer.close();
        }
        renderers.clear();
    }

}
