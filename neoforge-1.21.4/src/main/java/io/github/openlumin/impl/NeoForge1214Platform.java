package io.github.openlumin.impl;

import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.api.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * NeoForge 1.21.4 平台实现。
 * <p>
 * 在模组初始化时调用 {@link #initialize(IEventBus)} 以注册平台特定的 API 实现
 * 并挂载游戏生命周期事件（客户端退出清理）。
 */
public class NeoForge1214Platform {

    public static void initialize(IEventBus modEventBus) {
        // 注册 LuminApi 实现
        LuminApi.initialize(
                new NeoForge1214RenderContext(),
                new NeoForge1214GpuBuffer(),
                new NeoForge1214VertexFormat(),
                new NeoForge1214RenderPipeline(),
                new NeoForge1214Texture()
        );

        // 客户端初始化完成后的回调（可用于加载默认字体等资源）
        modEventBus.addListener(NeoForge1214Platform::onClientSetup);

        // 游戏关闭时释放所有 GPU 资源
        NeoForge.EVENT_BUS.addListener(NeoForge1214Platform::onGameShuttingDown);
    }

    // ────────────────────────────────────────────────────────────────

    private static void onClientSetup(FMLClientSetupEvent event) {
        // 客户端完成加载后执行的初始化（目前为空，保留扩展点）
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event) {
        // 释放 LuminRenderSystem 持有的所有 GPU 资源（管线、UBO、渲染目标、渲染器等）
        LuminRenderSystem.destroyAll();
    }
}
