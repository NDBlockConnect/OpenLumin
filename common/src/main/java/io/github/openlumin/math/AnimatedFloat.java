package io.github.openlumin.math;

/**
 * 带目标值的动画浮点数，每帧通过 {@link #tick(float)} 推进，自动向目标值平滑插值。
 * <p>
 * 使用示例：
 * <pre>{@code
 * AnimatedFloat alpha = AnimatedFloat.of(0f).withSpeed(8f);
 * // 每帧调用（deltaTime 单位秒）
 * alpha.tick(deltaTime);
 * // 读取当前值
 * float a = alpha.get();
 * // 修改目标值
 * alpha.setTarget(1f);
 * }</pre>
 */
public final class AnimatedFloat {

    private float value;
    private float target;
    private float speed;       // 指数衰减速度，>0；值越大收敛越快
    private float snapThreshold; // 当差值小于此阈值时直接 snap，避免无限逼近

    private AnimatedFloat(float initial) {
        this.value = initial;
        this.target = initial;
        this.speed = 10.0f;
        this.snapThreshold = 0.001f;
    }

    /** 创建初始值为 {@code initial} 的动画浮点数，默认速度=10，snap阈值=0.001。 */
    public static AnimatedFloat of(float initial) {
        return new AnimatedFloat(initial);
    }

    /** 创建初始值为 0 的动画浮点数。 */
    public static AnimatedFloat zero() {
        return new AnimatedFloat(0.0f);
    }

    // ────────────────────────────── 配置 ──────────────────────────────

    /**
     * 设置平滑速度（指数衰减系数）。值越大收敛越快。
     * 推荐范围：3（慢）~20（快）。默认10。
     *
     * @param speed 速度系数，必须大于0
     */
    public AnimatedFloat withSpeed(float speed) {
        if (speed <= 0f) throw new IllegalArgumentException("speed must be > 0");
        this.speed = speed;
        return this;
    }

    /**
     * 设置 snap 阈值。当 |value - target| < threshold 时直接设为目标值。
     * 可避免无限渐近导致的浮点噪声。默认 0.001。
     */
    public AnimatedFloat withSnapThreshold(float threshold) {
        this.snapThreshold = Math.max(0f, threshold);
        return this;
    }

    // ────────────────────────────── 控制 ──────────────────────────────

    /** 设置目标值，动画会平滑向此值收敛。 */
    public AnimatedFloat setTarget(float target) {
        this.target = target;
        return this;
    }

    /** 设置目标值范围 [min, max] 内的某个比例 t∈[0,1]。 */
    public AnimatedFloat setTargetLerp(float min, float max, float t) {
        return setTarget(min + (max - min) * clamp01(t));
    }

    /** 立即设置当前值并同步目标值，跳过动画。 */
    public AnimatedFloat snap(float value) {
        this.value = value;
        this.target = value;
        return this;
    }

    /** 立即跳到当前目标值，取消剩余动画。 */
    public AnimatedFloat snapToTarget() {
        this.value = this.target;
        return this;
    }

    // ────────────────────────────── 推进 ──────────────────────────────

    /**
     * 推进动画，使用指数衰减平滑（帧率无关）。
     * 每帧调用一次，传入距上一帧的时间差（秒）。
     *
     * @param deltaSeconds 帧间时间，秒
     */
    public void tick(float deltaSeconds) {
        if (deltaSeconds <= 0f) return;
        float diff = target - value;
        if (Math.abs(diff) < snapThreshold) {
            value = target;
            return;
        }
        // 指数衰减：v += (target - v) * (1 - e^(-speed * dt))
        float factor = 1.0f - (float) Math.exp(-speed * deltaSeconds);
        value += diff * factor;
    }

    /**
     * 推进动画，使用线性插值（每帧固定步长，适合以游戏 tick 计时的场景）。
     *
     * @param factor 每次调用前进比例，0=不动，1=立即到达（推荐 0.05~0.3）
     */
    public void tickLinear(float factor) {
        float diff = target - value;
        if (Math.abs(diff) < snapThreshold) {
            value = target;
            return;
        }
        value += diff * clamp01(factor);
    }

    /**
     * 推进动画，使用缓动函数（单次完整动画，需外部维护归一化进度 t）。
     * 与 {@link Easing} 配合使用：
     * <pre>{@code animFloat.tickEased(Easing.easeOutCubic(progress));}</pre>
     *
     * @param easedT 已经过缓动函数映射的进度 [0,1]
     */
    public void tickEased(float easedT) {
        // 此方法不保留内部状态，只是便捷的值映射
        // 调用方负责维护 from / to / t
    }

    // ────────────────────────────── 读取 ──────────────────────────────

    /** 返回当前动画值。 */
    public float get() {
        return value;
    }

    /** 返回目标值。 */
    public float getTarget() {
        return target;
    }

    /** 当前值是否已到达目标（在 snap 阈值内）。 */
    public boolean isSettled() {
        return Math.abs(target - value) < snapThreshold;
    }

    /**
     * 将当前值映射到 [0,1] 范围（基于 min/max 区间）。
     * 可直接用于 alpha、进度条等。
     */
    public float getNormalized(float min, float max) {
        if (max == min) return 0f;
        return clamp01((value - min) / (max - min));
    }

    // ────────────────────────────── 工具 ──────────────────────────────

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    @Override
    public String toString() {
        return "AnimatedFloat{value=" + value + ", target=" + target + ", speed=" + speed + "}";
    }
}
