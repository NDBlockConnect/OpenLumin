package io.github.openlumin.shaderpack.graph;

import io.github.openlumin.shaderpack.LuminTargetId;

/**
 * 渲染图资源标识：着色器可读写的目标。
 *
 * <p>与 {@link LuminTargetId} 的区别：后者是"pack 声明层面的目标"，
 * 本类型是"图层面的资源实例"——含 main/alt 双实例（乒乓写）语义。</p>
 *
 * @param target 目标（colortex/depthtex/shadowtex/shadowcolor）
 * @param copy   实例：{@link Copy#MAIN} 主副本 / {@link Copy#ALT} 乒乓副本
 */
public record LuminResourceId(LuminTargetId target, Copy copy) {

    /** 乒乓实例。 */
    public enum Copy {
        MAIN,
        ALT
    }

    public LuminResourceId {
        if (target == null) {
            throw new NullPointerException("target");
        }
        if (copy == null) {
            throw new NullPointerException("copy");
        }
    }

    public static LuminResourceId main(LuminTargetId target) {
        return new LuminResourceId(target, Copy.MAIN);
    }

    public static LuminResourceId alt(LuminTargetId target) {
        return new LuminResourceId(target, Copy.ALT);
    }

    /** 同目标的另一副本（乒乓交换用）。 */
    public LuminResourceId flipped() {
        return new LuminResourceId(target, copy == Copy.MAIN ? Copy.ALT : Copy.MAIN);
    }

    @Override
    public String toString() {
        return target.canonicalName() + (copy == Copy.ALT ? ".alt" : "");
    }
}
