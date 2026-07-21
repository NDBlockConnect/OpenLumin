package io.github.openlumin.api;

/**
 * 基础句柄接口，用于类型安全地引用平台特定资源
 */
public sealed interface Handle permits
    GpuBufferHandle,
    VertexFormatHandle,
    RenderPipelineHandle,
    ShaderHandle,
    TextureHandle {

    /**
     * 获取原生句柄对象（用于平台特定操作）
     * @return 平台相关的原生对象
     */
    Object nativeHandle();
}
