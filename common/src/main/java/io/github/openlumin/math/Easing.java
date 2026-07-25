package io.github.openlumin.math;

/**
 * 常用缓动函数集合，所有方法接受归一化进度 t∈[0,1]，返回缓动后的值∈[0,1]。
 * <p>
 * 命名规则：{@code ease[In|Out|InOut]_类型}
 */
public final class Easing {

    private Easing() {}

    // ────────────────────────────── 线性 ──────────────────────────────

    public static float linear(float t) {
        return clamp01(t);
    }

    // ────────────────────────────── Sine ──────────────────────────────

    public static float easeInSine(float t) {
        t = clamp01(t);
        return 1.0f - (float) Math.cos(t * Math.PI * 0.5);
    }

    public static float easeOutSine(float t) {
        t = clamp01(t);
        return (float) Math.sin(t * Math.PI * 0.5);
    }

    public static float easeInOutSine(float t) {
        t = clamp01(t);
        return -(float) (Math.cos(Math.PI * t) - 1.0) * 0.5f;
    }

    // ────────────────────────────── Quad ──────────────────────────────

    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float easeOutQuad(float t) {
        t = clamp01(t);
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2.0f * t * t : 1.0f - (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) * 0.5f;
    }

    // ────────────────────────────── Cubic ──────────────────────────────

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f
                ? 4.0f * t * t * t
                : 1.0f - (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) * 0.5f;
    }

    // ────────────────────────────── Quart ──────────────────────────────

    public static float easeInQuart(float t) {
        t = clamp01(t);
        return t * t * t * t;
    }

    public static float easeOutQuart(float t) {
        t = clamp01(t);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv * inv;
    }

    public static float easeInOutQuart(float t) {
        t = clamp01(t);
        if (t < 0.5f) {
            float tt = t * t;
            return 8.0f * tt * tt;
        }
        float inv = -2.0f * t + 2.0f;
        return 1.0f - inv * inv * inv * inv * 0.5f;
    }

    // ────────────────────────────── Expo ──────────────────────────────

    public static float easeInExpo(float t) {
        t = clamp01(t);
        return t == 0.0f ? 0.0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
    }

    public static float easeOutExpo(float t) {
        t = clamp01(t);
        return t == 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    }

    public static float easeInOutExpo(float t) {
        t = clamp01(t);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return t < 0.5f
                ? (float) Math.pow(2.0, 20.0 * t - 10.0) * 0.5f
                : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) * 0.5f;
    }

    // ────────────────────────────── Back ──────────────────────────────

    private static final float C1 = 1.70158f;
    private static final float C2 = C1 * 1.525f;
    private static final float C3 = C1 + 1.0f;

    public static float easeInBack(float t) {
        t = clamp01(t);
        return C3 * t * t * t - C1 * t * t;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float inv = t - 1.0f;
        return 1.0f + C3 * inv * inv * inv + C1 * inv * inv;
    }

    public static float easeInOutBack(float t) {
        t = clamp01(t);
        if (t < 0.5f) {
            return (2.0f * t) * (2.0f * t) * ((C2 + 1.0f) * 2.0f * t - C2) * 0.5f;
        }
        float u = 2.0f * t - 2.0f;
        return (u * u * ((C2 + 1.0f) * u + C2) + 2.0f) * 0.5f;
    }

    // ────────────────────────────── Elastic ──────────────────────────────

    private static final float C4 = (float) (2.0 * Math.PI / 3.0);
    private static final float C5 = (float) (2.0 * Math.PI / 4.5);

    public static float easeInElastic(float t) {
        t = clamp01(t);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return -(float) Math.pow(2.0, 10.0 * t - 10.0) * (float) Math.sin((t * 10.0 - 10.75) * C4);
    }

    public static float easeOutElastic(float t) {
        t = clamp01(t);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return (float) Math.pow(2.0, -10.0 * t) * (float) Math.sin((t * 10.0 - 0.75) * C4) + 1.0f;
    }

    public static float easeInOutElastic(float t) {
        t = clamp01(t);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        if (t < 0.5f) {
            return -((float) Math.pow(2.0, 20.0 * t - 10.0) * (float) Math.sin((20.0 * t - 11.125) * C5)) * 0.5f;
        }
        return (float) Math.pow(2.0, -20.0 * t + 10.0) * (float) Math.sin((20.0 * t - 11.125) * C5) * 0.5f + 1.0f;
    }

    // ────────────────────────────── Bounce ──────────────────────────────

    public static float easeOutBounce(float t) {
        t = clamp01(t);
        final float n1 = 7.5625f;
        final float d1 = 2.75f;
        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    }

    public static float easeInBounce(float t) {
        return 1.0f - easeOutBounce(1.0f - t);
    }

    public static float easeInOutBounce(float t) {
        t = clamp01(t);
        return t < 0.5f
                ? (1.0f - easeOutBounce(1.0f - 2.0f * t)) * 0.5f
                : (1.0f + easeOutBounce(2.0f * t - 1.0f)) * 0.5f;
    }

    // ────────────────────────────── Spring ──────────────────────────────

    /**
     * 弹簧缓动，模拟阻尼弹簧运动。
     *
     * @param t       进度 [0, 1]
     * @param damping 阻尼系数，0=无阻尼，1=临界阻尼，>1=过阻尼（推荐 0.5~0.8）
     * @param speed   角频率，控制弹簧速度（推荐 10~20）
     */
    public static float spring(float t, float damping, float speed) {
        t = clamp01(t);
        return 1.0f - (float) (Math.exp(-damping * speed * t)
                * Math.cos(speed * Math.sqrt(1.0 - damping * damping) * t));
    }

    // ────────────────────────────── 工具 ──────────────────────────────

    /** 把任意缓动函数的结果映射到 [from, to] 范围。 */
    public static float interpolate(float from, float to, float easedT) {
        return from + (to - from) * easedT;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
