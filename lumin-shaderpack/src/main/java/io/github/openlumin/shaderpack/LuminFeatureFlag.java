package io.github.openlumin.shaderpack;

/**
 * 能力旗标（与 Iris 的 FeatureFlags 对齐，作为兼容基线；另有 OpenLumin 自有扩展）。
 *
 * <p>协商语义（WP-2 设计 §5）：</p>
 * <ul>
 *   <li>包声明为 <b>required</b> 而引擎不具备 → **明确拒绝该 pack** 并给出原因
 *       （不静默降级）；</li>
 *   <li>包声明为 <b>optional</b> 而引擎不具备 → 以 {@code false} 提供给包，由包自行分支。</li>
 * </ul>
 */
public enum LuminFeatureFlag {
    /** 阴影颜色缓冲可超过 OptiFine 兼容的 2 个（上限 8）。 */
    HIGHER_SHADOWCOLOR,
    /** 采样器可独立于纹理绑定（硬件采样器分离）。 */
    SEPARATE_HARDWARE_SAMPLERS,
    /** 支持 image load/store（{@code image.*} 指令）。 */
    CUSTOM_IMAGES,
    /** 支持逐缓冲混合（{@code blend.<pass>.<buffer>}）。 */
    PER_BUFFER_BLENDING,
    /** 支持计算着色器与 compute 合成组。 */
    COMPUTE_SHADERS,
    /** 支持曲面细分阶段（.tcs/.tes）。 */
    TESSELLATION_SHADERS,
    /** 支持半透明实体程序（entities_translucent）。 */
    ENTITY_TRANSLUCENT,
    /** 支持反向剔除语义（reversed culling）。 */
    REVERSED_CULLING,
    /** 支持方块发光属性（block emission）。 */
    BLOCK_EMISSION_ATTRIBUTE,
    /** 可禁用原版天气渲染。 */
    CAN_DISABLE_WEATHER,
    /** 支持 chunk fade 变量。 */
    FADE_VARIABLE,
    /** 支持纹理过滤模式声明。 */
    TEXTURE_FILTERING,
    /** 支持 SSBO（{@code bufferObject.*} + indirect 派发）。 */
    SSBO,
    /** OpenLumin 扩展：渲染图为声明式（允许包声明额外 pass 依赖）。 */
    LUMIN_RENDER_GRAPH,
    /** OpenLumin 扩展：引擎保证产出运动向量（WP-4/WP-5 前提）。 */
    LUMIN_MOTION_VECTORS,
    /** OpenLumin 扩展：引擎保证抖动已注入投影矩阵。 */
    LUMIN_PROJECTION_JITTER
}
