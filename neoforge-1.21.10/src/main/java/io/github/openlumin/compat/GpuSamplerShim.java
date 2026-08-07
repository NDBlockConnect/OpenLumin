/**
 * 1.21.10 兼容性桩：GpuSampler 在 1.21.10 中已移除或不同。
 * 避免在 com.mojang.* 包中创建类导致模块冲突。
 */
package io.github.openlumin.compat;

/**
 * fabric-1.21.10 shim：GpuSampler 在 1.21.10 中已移除。
 * 保留此类仅用于编译通过，实际代码已改用 RenderPass.bindSampler()。
 */
public class GpuSamplerShim implements AutoCloseable {
    @Override
    public void close() {}
}
