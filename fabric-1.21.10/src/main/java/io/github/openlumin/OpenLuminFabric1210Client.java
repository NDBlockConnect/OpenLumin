package io.github.openlumin;

import io.github.openlumin.platform.Fabric1210Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.fabricmc.api.ClientModInitializer;

/**
 * OpenLumin Fabric 1.21.10 入口
 *
 * OpenLumin 是一个渲染库，不包含内置测试用例。
 * 如需测试，请使用 openlumin-testmod。
 *
 * 帧生命周期由 RenderSystemMixin 统一管理：
 *   flipFrame 后 → LuminImmediateRenderer.endFrame()
 *               → LuminRenderSystem.endDynamicUniformFrame()
 *               → LuminRenderSystem.beginRenderFrame()
 */
public class OpenLuminFabric1210Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 注册平台实现（必须最先执行）
        PlatformRegistry.register(new Fabric1210Platform());

        System.out.println("[OpenLumin] fabric-1.21.10 library initialized");
        System.out.println("[OpenLumin] Use openlumin-testmod for testing");
    }
}
