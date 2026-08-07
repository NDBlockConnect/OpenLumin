package io.github.openlumin.holders;

import io.github.openlumin.LuminRenderSystem.LuminRenderTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * RenderTarget持有者，管理所有LuminRenderTarget实例
 */
public class RenderTargetHolder {

    public static final RenderTargetHolder INSTANCE = new RenderTargetHolder();

    private final List<LuminRenderTarget> targets = new ArrayList<>();

    private RenderTargetHolder() {
    }

    public LuminRenderTarget register(LuminRenderTarget target) {
        targets.add(target);
        return target;
    }

    public void add(LuminRenderTarget target) {
        targets.add(target);
    }

    public void unregister(LuminRenderTarget target) {
        targets.remove(target);
    }

    public void remove(LuminRenderTarget target) {
        targets.remove(target);
    }

    public void destroyAll() {
        for (LuminRenderTarget target : targets) {
            // target.destroy();  // NeoForge: LuminRenderTarget没有destroy方法，改用close
            target.close();
        }
        targets.clear();
    }
}
