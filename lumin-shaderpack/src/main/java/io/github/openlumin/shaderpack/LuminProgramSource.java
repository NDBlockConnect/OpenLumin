package io.github.openlumin.shaderpack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单个程序的源文件集合（按着色器阶段）与其元数据。
 *
 * <p>源文本为**预处理前的原始文本**（include 尚未展开）——展开在编译期（第 5 层）前的
 * 预处理步骤完成，以便选项变换与 include 图都能参与（与 Iris 的管线顺序一致）。</p>
 */
public final class LuminProgramSource {

    private final LuminProgramId id;
    private final Map<LuminShaderKind, String> sources;
    private final Map<LuminShaderKind, String> paths;
    private final java.util.Set<LuminTargetId> drawTargets;
    private final boolean enabled;

    public LuminProgramSource(LuminProgramId id, Map<LuminShaderKind, String> sources,
                              java.util.Set<LuminTargetId> drawTargets, boolean enabled) {
        this(id, sources, Map.of(), drawTargets, enabled);
    }

    public LuminProgramSource(LuminProgramId id, Map<LuminShaderKind, String> sources,
                              Map<LuminShaderKind, String> paths,
                              java.util.Set<LuminTargetId> drawTargets, boolean enabled) {
        this.id = Objects.requireNonNull(id, "id");
        this.sources = Collections.unmodifiableMap(new EnumMap<>(sources));
        this.paths = Collections.unmodifiableMap(new EnumMap<>(paths));
        this.drawTargets = java.util.Set.copyOf(drawTargets);
        this.enabled = enabled;
    }

    public LuminProgramId id() {
        return id;
    }

    /** 阶段 → 源文本（只读）。 */
    public Map<LuminShaderKind, String> sources() {
        return sources;
    }

    public String source(LuminShaderKind kind) {
        return sources.get(kind);
    }

    public boolean has(LuminShaderKind kind) {
        return sources.containsKey(kind);
    }

    /** 阶段 → 源文件规范化路径（编译期 include 展开用；未记录时缺省）。 */
    public Map<LuminShaderKind, String> paths() {
        return paths;
    }

    /** 某阶段的源文件路径；未记录返回 null。 */
    public String path(LuminShaderKind kind) {
        return paths.get(kind);
    }

    /** 本程序写入的渲染目标（来自 DRAWBUFFERS/RENDERTARGETS）。 */
    public java.util.Set<LuminTargetId> drawTargets() {
        return drawTargets;
    }

    /** 是否启用（{@code program.<name>.enabled} 或 profile 判定）。 */
    public boolean enabled() {
        return enabled;
    }

    /** 是否含顶点+片段两阶段（图形程序的最低要求）。 */
    public boolean isCompleteGraphicsProgram() {
        return has(LuminShaderKind.VERTEX) && has(LuminShaderKind.FRAGMENT);
    }

    public LuminProgramSource withEnabled(boolean value) {
        return new LuminProgramSource(id, sources, paths, drawTargets, value);
    }

    public LuminProgramSource withDrawTargets(java.util.Set<LuminTargetId> value) {
        return new LuminProgramSource(id, sources, paths, value, enabled);
    }

    @Override
    public String toString() {
        return "LuminProgramSource{" + id + ", stages=" + sources.keySet()
                + ", targets=" + drawTargets + (enabled ? "" : ", disabled") + "}";
    }
}
