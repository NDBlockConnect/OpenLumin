package io.github.openlumin.holders;

import io.github.openlumin.LuminRenderSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * LuminRenderTarget 生命周期管理器
 * 统一管理所有 OpenLumin render target 的注册、注销和销毁
 */
public class RenderTargetHolder {

    public static final RenderTargetHolder INSTANCE = new RenderTargetHolder();

    private final List<LuminRenderSystem.LuminRenderTarget> targets = new ArrayList<>();

    private RenderTargetHolder() {
    }

    public <T extends LuminRenderSystem.LuminRenderTarget> T register(T target) {
        targets.add(target);
        return target;
    }

    public void unregister(LuminRenderSystem.LuminRenderTarget target) {
        targets.remove(target);
    }

    public void destroyAll() {
        List<LuminRenderSystem.LuminRenderTarget> copy = new ArrayList<>(targets);
        targets.clear();
        for (LuminRenderSystem.LuminRenderTarget target : copy) {
            try {
                target.close();
            } catch (Exception e) {
                // 忽略单个 target 关闭时的异常
            }
        }
    }
}
