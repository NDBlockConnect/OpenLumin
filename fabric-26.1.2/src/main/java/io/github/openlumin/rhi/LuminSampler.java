package io.github.openlumin.rhi;

/** 采样器 */
public interface LuminSampler {
    LuminFilter minFilter();
    LuminFilter magFilter();
    LuminAddressMode addressU();
    LuminAddressMode addressV();
    LuminAddressMode addressW();
}
