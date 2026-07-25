package com.mojang.blaze3d.platform;

/**
 * fabric-1.21.10 shim：GpuSampler 在 1.21.10 中已移除。
 * 保留此类仅用于编译通过，实际代码已改用 RenderPass.bindSampler()。
 */
public class GpuSampler implements AutoCloseable {
    @Override
    public void close() {}
}
