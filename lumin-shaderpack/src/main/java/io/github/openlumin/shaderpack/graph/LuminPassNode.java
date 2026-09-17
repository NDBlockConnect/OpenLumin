package io.github.openlumin.shaderpack.graph;

import io.github.openlumin.shaderpack.LuminProgramId;

import java.util.Set;

/**
 * 渲染图节点：一次 pass 执行。
 *
 * <p>写集来自 {@code DRAWBUFFERS/RENDERTARGETS}；读集来自着色器声明的采样器引用
 * 与特殊读取（如深度拷贝）。二者共同定义依赖边与后续的 hazard/屏障需求。</p>
 *
 * @param program     程序标识
 * @param sequence    帧内执行序号（解析期的稳定序，不表示依赖关系）
 * @param reads       读取资源集
 * @param writes      写入资源集
 * @param viewport    视口规格
 * @param compute     是否计算 pass（无几何阶段）
 */
public record LuminPassNode(
        LuminProgramId program,
        int sequence,
        Set<LuminResourceId> reads,
        Set<LuminResourceId> writes,
        ViewportSpec viewport,
        boolean compute) {

    /**
     * 视口规格。
     *
     * @param scale   相对主目标的缩放系数（{@code scale.<pass>}）
     * @param offsetX 偏移（像素）
     * @param offsetY 偏移（像素）
     */
    public record ViewportSpec(float scale, float offsetX, float offsetY) {
        public static ViewportSpec full() {
            return new ViewportSpec(1f, 0f, 0f);
        }
    }

    public LuminPassNode {
        if (program == null) {
            throw new NullPointerException("program");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0, got " + sequence);
        }
        reads = Set.copyOf(reads);
        writes = Set.copyOf(writes);
        viewport = viewport == null ? ViewportSpec.full() : viewport;
    }

    /** 是否读写同一资源（自反馈：可能导致同 pass 内的 hazard）。 */
    public boolean isSelfFeedback() {
        for (LuminResourceId resource : reads) {
            if (writes.contains(resource)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return program + "[" + sequence + "] reads=" + reads + " writes=" + writes;
    }
}
