package io.github.openlumin.rhi;

/** 混合状态 */
public record LuminBlendState(
        boolean enabled,
        LuminBlendFactor srcColor, LuminBlendFactor dstColor,
        LuminBlendFactor srcAlpha, LuminBlendFactor dstAlpha
) {
    public static final LuminBlendState OFF = new LuminBlendState(false, LuminBlendFactor.ONE, LuminBlendFactor.ZERO, LuminBlendFactor.ONE, LuminBlendFactor.ZERO);
    public static final LuminBlendState SRC_ALPHA = new LuminBlendState(true, LuminBlendFactor.SRC_ALPHA, LuminBlendFactor.ONE_MINUS_SRC_ALPHA, LuminBlendFactor.ONE, LuminBlendFactor.ONE_MINUS_SRC_ALPHA);
}
