package io.github.openlumin.rhi;

/**
 * 26.1.2 上的 LuminRHI 接口——目前仅接口，无 GL 后端实现
 *
 * 为什么这里没有 gl/ 子包：
 *  - MC 26.1.2 的 GpuDevice 是 26.1.2 早期版本，不含 GpuFormat / LightweightRenderTarget /
 *    GpuTexture.getColorTextureView 等现代 RHI 抽象。
 *  - 26.1.2 上做完整 RHI 后端需要更大重写（替换为 26.1.2 原生 API 风格），属 Alpha 2.2 范围。
 *  - 当前 26.1.2 平台走原 Fabric2612Platform（LuminPlatform 旧实现），
 *    在 Alpha 2.2 统一前作为过渡。
 *
 * Alpha 2 范围内：
 *  - 26.1.2：仅接口（不实现 GL 后端，业务层继续走 LuminPlatform）
 *  - 26.2  ：完整接口 + LuminRHI_GL 后端（现代 API）
 *
 * 26.2 RHI 后端示例（参考 fabric-26.2/src/main/java/io/github/openlumin/rhi/gl/）：
 *  - LuminRHI_GL：顶层 LuminRHI 实现
 *  - LuminRHIResources：资源包装（buffer/texture/sampler/shader/pipeline）
 *  - LuminRHICommands：命令录制 + 提交
 *  - LuminRHIEncoder：命令编码器 + 渲染通道门面
 */
final class RhiGlBackendStatus {
    private RhiGlBackendStatus() {}
}
