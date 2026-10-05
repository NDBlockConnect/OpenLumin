package io.github.openlumin.shaderpack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 包级指令（{@code shaders.properties} 与源内 const 的解析结果汇总）。
 *
 * <p>采用<b>保序</b>容器：OptiFine 的"首声明优先"语义要求顺序可复现
 * （与 Iris 的 OrderBackedProperties 同语义，见 WP-2 设计 §2.4）。</p>
 */
public final class LuminPackDirectives {

    /** 逐 pass 视口缩放（{@code scale.<pass> = <s> [offX offY]}）。 */
    public record ViewportScale(float scale, float offsetX, float offsetY) {
        public ViewportScale {
            if (scale <= 0) {
                throw new IllegalArgumentException("scale must be > 0, got " + scale);
            }
        }

        public static ViewportScale of(float scale) {
            return new ViewportScale(scale, 0f, 0f);
        }
    }

    /** 逐 pass 混合覆盖（{@code blend.<pass>[.<buffer>]}）。 */
    public record BlendOverride(boolean enabled, int sourceFactor, int destinationFactor) {
    }

    /**
     * 缓冲尺寸覆盖（{@code size.buffer.<buffer> = <w> <h>}）。
     *
     * <p><b>相对/绝对判定与 Iris 一致</b>（取证：Iris {@code TextureScaleOverride}）：
     * 数值**含小数点则为相对**（屏幕尺寸的倍数），不含小数点则为**绝对像素**；
     * X/Y 两轴可各自独立。</p>
     */
    public record BufferSize(float width, float height,
                             boolean widthRelative, boolean heightRelative) {
        public BufferSize {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException(
                        "buffer size must be >= 0, got " + width + "x" + height);
            }
        }

        /** 解析后宽度（基准 = 主目标宽）。 */
        public int resolveWidth(int baseWidth) {
            return Math.round(widthRelative ? baseWidth * width : width);
        }

        /** 解析后高度（基准 = 主目标高）。 */
        public int resolveHeight(int baseHeight) {
            return Math.round(heightRelative ? baseHeight * height : height);
        }
    }

    /** 阴影相关指令。 */
    public record ShadowDirectives(int resolution, boolean culling, float distance,
                                   boolean terrain, boolean entities, boolean player,
                                   boolean blockEntities, boolean translucent, float intervalSize) {
        public static ShadowDirectives defaults() {
            return new ShadowDirectives(1024, true, 160f, true, true, true, true, true, 2f);
        }
    }

    private final Map<String, Boolean> switches;
    private final Map<LuminProgramId, ViewportScale> viewportScales;
    private final Map<LuminProgramId, BlendOverride> blendOverrides;
    private final Map<LuminProgramId, Float> alphaTests;
    private final Map<String, BufferSize> bufferSizes;
    private final ShadowDirectives shadow;
    private final Map<String, String> samplerTextures;

    public LuminPackDirectives(Map<String, Boolean> switches,
                               Map<LuminProgramId, ViewportScale> viewportScales,
                               Map<LuminProgramId, BlendOverride> blendOverrides,
                               Map<LuminProgramId, Float> alphaTests,
                               Map<String, BufferSize> bufferSizes,
                               ShadowDirectives shadow,
                               Map<String, String> samplerTextures) {
        this.switches = Map.copyOf(switches);
        this.viewportScales = Map.copyOf(viewportScales);
        this.blendOverrides = Map.copyOf(blendOverrides);
        this.alphaTests = Map.copyOf(alphaTests);
        this.bufferSizes = Map.copyOf(bufferSizes);
        this.shadow = shadow == null ? ShadowDirectives.defaults() : shadow;
        this.samplerTextures = Map.copyOf(samplerTextures);
    }

    public static LuminPackDirectives empty() {
        return new LuminPackDirectives(new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                ShadowDirectives.defaults(), new LinkedHashMap<>());
    }

    /** 开关指令（如 {@code clouds}、{@code shadow.enabled}）；缺省视为 false。 */
    public boolean switchEnabled(String key) {
        return switches.getOrDefault(key, Boolean.FALSE);
    }

    public Set<String> switchKeys() {
        return switches.keySet();
    }

    public ViewportScale viewportScale(LuminProgramId program) {
        return viewportScales.get(program);
    }

    public BlendOverride blendOverride(LuminProgramId program) {
        return blendOverrides.get(program);
    }

    public Float alphaTest(LuminProgramId program) {
        return alphaTests.get(program);
    }

    /** 缓冲尺寸覆盖（{@code size.buffer.<buffer>}）；未声明返回 null。 */
    public BufferSize bufferSize(String buffer) {
        return bufferSizes.get(buffer);
    }

    public ShadowDirectives shadow() {
        return shadow;
    }

    /** 采样器绑定（{@code texture.<stage>.<sampler>}）。 */
    public Map<String, String> samplerTextures() {
        return samplerTextures;
    }

    public Map<LuminProgramId, ViewportScale> viewportScales() {
        return viewportScales;
    }

    public Map<LuminProgramId, BlendOverride> blendOverrides() {
        return blendOverrides;
    }

    public Map<LuminProgramId, Float> alphaTests() {
        return alphaTests;
    }

    public Map<String, BufferSize> bufferSizes() {
        return bufferSizes;
    }

    public Map<String, Boolean> switches() {
        return switches;
    }
}
