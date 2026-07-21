package io.github.openlumin.impl;

import io.github.openlumin.api.*;

/**
 * NeoForge 1.21.4 平台实现
 * 在模组初始化时调用以注册平台特定的 API 实现
 */
public class NeoForge1214Platform {

    public static void initialize() {
        LuminApi.initialize(
            new NeoForge1214RenderContext(),
            new NeoForge1214GpuBuffer(),
            new NeoForge1214VertexFormat(),
            new NeoForge1214RenderPipeline(),
            new NeoForge1214Texture()
        );
    }
}
