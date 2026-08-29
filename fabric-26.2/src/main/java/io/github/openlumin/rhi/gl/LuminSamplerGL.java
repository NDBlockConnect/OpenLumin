package io.github.openlumin.rhi.gl;

import com.mojang.blaze3d.textures.GpuSampler;
import io.github.openlumin.rhi.LuminAddressMode;
import io.github.openlumin.rhi.LuminFilter;
import io.github.openlumin.rhi.LuminSampler;

/** LuminSampler → 包装 GpuSampler */
public final class LuminSamplerGL implements LuminSampler {
    public final GpuSampler gsamp;
    private final LuminFilter min, mag;
    private final LuminAddressMode u, v, w;
    public LuminSamplerGL(GpuSampler gsamp, LuminFilter min, LuminFilter mag,
                          LuminAddressMode u, LuminAddressMode v, LuminAddressMode w) {
        this.gsamp = gsamp; this.min = min; this.mag = mag; this.u = u; this.v = v; this.w = w;
    }
    @Override public LuminFilter minFilter() { return min; }
    @Override public LuminFilter magFilter() { return mag; }
    @Override public LuminAddressMode addressU() { return u; }
    @Override public LuminAddressMode addressV() { return v; }
    @Override public LuminAddressMode addressW() { return w; }
}
