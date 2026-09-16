package io.github.openlumin.shaderpack;

import java.util.Set;

/**
 * 程序标识：一个 shaderpack 程序（组 + 名称 + 可选编号）。
 *
 * <p>等价性基于 {@code (group, name, index)}；名称在 gbuffer 组内取
 * {@code gbuffers_<name>}，在 shadow 组内取 {@code shadow_<name>}。</p>
 */
public record LuminProgramId(LuminProgramGroup group, String name, int index) {

    /** gbuffer 程序名集合（与 OptiFine/Iris 对齐的兼容基线）。 */
    public static final Set<String> GBUFFER_NAMES = Set.of(
            "basic", "line", "textured", "textured_lit", "skybasic", "skytextured", "clouds",
            "terrain", "terrain_solid", "terrain_cutout", "damagedblock", "block", "block_translucent",
            "beaconbeam", "item", "entities", "entities_translucent", "entities_glowing", "lightning",
            "particles", "particles_translucent", "armor_glint", "spidereyes", "hand", "weather",
            "water", "hand_water");

    /** shadow 程序名集合（含主 shadow 程序与几何变体）。 */
    public static final Set<String> SHADOW_NAMES = Set.of(
            "shadow", "solid", "cutout", "water", "entities", "lightning", "block");

    /** 合成/计算组允许的最大编号（含 0 号主程序，编号 1..99）。 */
    public static final int MAX_INDEX = 99;

    public LuminProgramId {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("program name must not be empty");
        }
        if (index < 0 || index > MAX_INDEX) {
            throw new IllegalArgumentException("index must be in [0," + MAX_INDEX + "], got " + index);
        }
        if (index > 0 && !group.isNumbered()) {
            throw new IllegalArgumentException("group " + group + " does not allow numbered variants");
        }
    }

    /** 主程序（无编号）。 */
    public static LuminProgramId of(LuminProgramGroup group, String name) {
        return new LuminProgramId(group, name, 0);
    }

    /** 编号变体（如 deferred3）。 */
    public static LuminProgramId numbered(LuminProgramGroup group, int index) {
        return new LuminProgramId(group, group.identifier(), index);
    }

    /**
     * 源文件基名（不含扩展名）。
     * <ul>
     *   <li>gbuffers：{@code gbuffers_<name>}；</li>
     *   <li>shadow：{@code shadow_<name>}（主程序名为 {@code shadow}）；</li>
     *   <li>合成组：{@code <group>} 或 {@code <group><index>}；</li>
     *   <li>final：{@code final}。</li>
     * </ul>
     */
    public String sourceBaseName() {
        return switch (group) {
            case GBUFFERS -> "gbuffers_" + name;
            case SHADOW -> name.equals("shadow") ? "shadow" : "shadow_" + name;
            default -> index == 0 ? group.identifier() : group.identifier() + index;
        };
    }

    @Override
    public String toString() {
        return sourceBaseName();
    }
}
