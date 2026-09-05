package io.github.openlumin.chunk.cull;

/**
 * Section 可见性图（消费方桥接接口）：方块遮挡数据的游戏侧抽象。
 * <p>实现要求：线程安全（遍历器可能在非渲染线程调用）；
 * 对不存在/越界坐标 {@link #exists} 返回 false。</p>
 */
public interface LuminSectionVisibilityGraph {

    boolean exists(LuminSectionPos pos);

    /**
     * 遍历位掩码（6 位，{@link LuminVisibilityEncoding#traversalBit}）：
     * bit d 置位 = 沿方向 d 的邻接 section 连通（面未全遮挡，可通行）。
     * 仅对 {@link #exists} 为 true 的坐标有意义。
     */
    long visibilityBits(LuminSectionPos pos);
}
