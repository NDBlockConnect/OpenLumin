package com.mojang.blaze3d.platform;

/**
 * NeoForge适配层：模拟Fabric的GpuSampler API
 */
public class GpuSampler {

    public static final int FILTER_NEAREST = 0;
    public static final int FILTER_LINEAR = 1;
    public static final int WRAP_CLAMP_TO_EDGE = 0;
    public static final int WRAP_REPEAT = 1;
    public static final int WRAP_MIRRORED_REPEAT = 2;

    private final int minFilter;
    private final int magFilter;
    private final int wrapS;
    private final int wrapT;

    public GpuSampler(int minFilter, int magFilter, int wrapS, int wrapT) {
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.wrapS = wrapS;
        this.wrapT = wrapT;
    }

    public GpuSampler() {
        this(FILTER_NEAREST, FILTER_NEAREST, WRAP_CLAMP_TO_EDGE, WRAP_CLAMP_TO_EDGE);
    }

    public int minFilter() {
        return minFilter;
    }

    public int magFilter() {
        return magFilter;
    }

    public int wrapS() {
        return wrapS;
    }

    public int wrapT() {
        return wrapT;
    }

    public void close() {
        // NeoForge使用不同的API
    }
}
