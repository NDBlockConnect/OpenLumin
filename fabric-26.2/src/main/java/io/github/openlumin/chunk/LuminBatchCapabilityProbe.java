package io.github.openlumin.chunk;

import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.openlumin.chunk.batch.LuminBatchCapabilities;

/**
 * 26.2 基线的批量能力探测（把 MC 的公开 {@link DeviceFeatures} 映射为 LuminBatchCapabilities）。
 *
 * <p>26.2 的 {@code GpuDevice.getDeviceInfo()} 暴露 {@code DeviceFeatures}，其字段与
 * 26.x 的批量 API 面一一对应（javap 实证）：{@code multiDrawDirectInterleaved} 对应
 * 单 IntBuffer 形态（GL 后端不支持、仅 Vulkan），{@code multiDrawDirectSeparate} 对应
 * 三缓冲形态（GL/Vulkan 皆可），{@code multiDrawIndirect}/{@code drawIndirect} 对应间接路径。</p>
 *
 * <p>注意：26.1.2 <b>没有</b> DeviceFeatures（逐版本核对结论），该基线应使用
 * {@link LuminBatchCapabilities#baseline()} 或由消费方注入。</p>
 */
public final class LuminBatchCapabilityProbe {

    private LuminBatchCapabilityProbe() {
    }

    /** 从当前设备读取能力（26.2 基线）。 */
    public static LuminBatchCapabilities detect() {
        DeviceFeatures features = RenderSystem.getDevice().getDeviceInfo().features();
        return new LuminBatchCapabilities(
                true,
                features.multiDrawDirectInterleaved(),
                features.multiDrawDirectSeparate(),
                features.multiDrawIndirect(),
                features.drawIndirect(),
                features.persistentMapping());
    }
}
