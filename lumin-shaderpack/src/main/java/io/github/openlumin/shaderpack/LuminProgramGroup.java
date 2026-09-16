package io.github.openlumin.shaderpack;

/**
 * 程序组（pass 家族）。与 OptiFine/Iris 的程序组划分一致。
 *
 * <p>组的语义（决定在帧序中的位置与是否可编号）：</p>
 * <ul>
 *   <li>{@link #SETUP}：仅计算，尺寸变化时执行一次；</li>
 *   <li>{@link #BEGIN} / {@link #PREPARE}：主渲染前后的合成组；</li>
 *   <li>{@link #SHADOW}：阴影几何渲染；{@link #SHADOWCOMP}：阴影合成；</li>
 *   <li>{@link #GBUFFERS}：世界几何渲染（按渲染阶段选程序）；</li>
 *   <li>{@link #DEFERRED} / {@link #COMPOSITE}：后处理链（可编号）；</li>
 *   <li>{@link #FINAL}：最终 pass（单程序）。</li>
 * </ul>
 */
public enum LuminProgramGroup {
    SETUP("setup", true, false),
    BEGIN("begin", true, false),
    SHADOW("shadow", false, false),
    SHADOWCOMP("shadowcomp", true, false),
    PREPARE("prepare", true, false),
    GBUFFERS("gbuffers", false, false),
    DEFERRED("deferred", true, true),
    COMPOSITE("composite", true, true),
    FINAL("final", false, false);

    private final String identifier;
    private final boolean composite;
    private final boolean numbered;

    LuminProgramGroup(String identifier, boolean composite, boolean numbered) {
        this.identifier = identifier;
        this.composite = composite;
        this.numbered = numbered;
    }

    /** 目录/文件名前缀标识。 */
    public String identifier() {
        return identifier;
    }

    /** 是否为合成组（可含多个程序、按序执行）。 */
    public boolean isComposite() {
        return composite;
    }

    /** 是否允许编号变体（如 deferred1、composite7）。 */
    public boolean isNumbered() {
        return numbered;
    }
}
