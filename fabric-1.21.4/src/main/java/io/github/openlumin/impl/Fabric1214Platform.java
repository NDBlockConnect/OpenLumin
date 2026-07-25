package io.github.openlumin.impl;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.api.LuminApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Fabric 1.21.4 平台实现入口。
 * <p>
 * 由 fabric.mod.json 的 "client" entrypoint 触发，在客户端加载时：
 * <ol>
 *   <li>注册 LuminApi 的平台特定实现</li>
 *   <li>挂载客户端停止事件，退出时释放 GPU 资源</li>
 * </ol>
 */
public class Fabric1214Platform implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 注册 LuminApi 实现
        LuminApi.initialize(
                new Fabric1214RenderContext(),
                new Fabric1214GpuBuffer(),
                new Fabric1214VertexFormat(),
                new Fabric1214RenderPipeline(),
                new Fabric1214Texture()
        );

        // 客户端停止时释放所有 GPU 资源
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LuminRenderSystem.destroyAll());
    }
}
