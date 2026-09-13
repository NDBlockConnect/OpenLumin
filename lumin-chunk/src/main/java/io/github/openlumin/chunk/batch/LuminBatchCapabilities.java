package io.github.openlumin.chunk.batch;

/**
 * 设备批量提交能力（原理参照：按能力选择最优批量路径）。
 *
 * <p>字段语义与 MC 26.x `DeviceFeatures` 对应（该 record 为公开 API，可用作能力来源）：</p>
 * <ul>
 *   <li>{@code multiDrawDirectInterleaved}：多 draw 的<b>交错</b>直传形态
 *       （26.2 `multiDrawIndexed(IntBuffer,int,int,int)`）；GL 后端不支持（会抛
 *       UnsupportedOperationException），仅 Vulkan 可用；</li>
 *   <li>{@code multiDrawDirectSeparate}：多 draw 的<b>分离缓冲</b>形态
 *       （26.2 `multiDrawIndexed(PointerBuffer,IntBuffer,IntBuffer,int)`：偏移/计数/baseVertex
 *       三个数组 + 统一实例数），GL 亦可用；</li>
 *   <li>{@code multiDrawIndirect}：间接多 draw（命令缓冲驱动）；</li>
 *   <li>{@code drawIndirect}：间接单 draw；</li>
 *   <li>{@code persistentMapping}：持久映射缓冲（staging 环前提）。</li>
 * </ul>
 *
 * <p>另含一个与 MC 无关但恒可用的基线能力：{@code drawMultipleIndexed} 聚合通路
 * （26.1.2/26.2 双基线双后端均具备，见 M3a）。{@link #baseline()} 为"仅有聚合通路"的保守集。</p>
 */
public record LuminBatchCapabilities(
        boolean drawMultipleIndexed,
        boolean multiDrawDirectInterleaved,
        boolean multiDrawDirectSeparate,
        boolean multiDrawIndirect,
        boolean drawIndirect,
        boolean persistentMapping) {

    /** 保守基线：仅聚合通路（任何 26.x 后端都具备）。 */
    public static LuminBatchCapabilities baseline() {
        return new LuminBatchCapabilities(true, false, false, false, false, false);
    }

    /** 全能力（理论最大；测试与未来后端用）。 */
    public static LuminBatchCapabilities full() {
        return new LuminBatchCapabilities(true, true, true, true, true, true);
    }
}
