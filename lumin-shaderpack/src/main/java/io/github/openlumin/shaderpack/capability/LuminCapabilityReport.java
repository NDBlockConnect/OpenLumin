package io.github.openlumin.shaderpack.capability;

import io.github.openlumin.shaderpack.LuminFeatureFlag;

import java.util.Set;

/**
 * 能力协商结果（WP-2 M6）。
 *
 * <p>语义（见 WP-2 设计 §5）：</p>
 * <ul>
 *   <li>{@code missingRequired} 非空 → <b>拒绝该 pack</b>（不静默降级），附缺失旗标清单；</li>
 *   <li>optional 旗标按能力集拆分为 {@code providedOptional}（以 true 提供给包）与
 *       {@code unavailableOptional}（以 false 提供给包，由包自行分支）。</li>
 * </ul>
 *
 * @param missingRequired      未满足的必需旗标（空 = 接受）
 * @param providedOptional     可获得的可选旗标（提供给包为 true）
 * @param unavailableOptional  不可获得的可选旗标（提供给包为 false）
 */
public record LuminCapabilityReport(
        Set<LuminFeatureFlag> missingRequired,
        Set<LuminFeatureFlag> providedOptional,
        Set<LuminFeatureFlag> unavailableOptional) {

    private static final LuminCapabilityReport NOT_NEGOTIATED =
            new LuminCapabilityReport(Set.of(), Set.of(), Set.of());

    public LuminCapabilityReport {
        missingRequired = Set.copyOf(missingRequired);
        providedOptional = Set.copyOf(providedOptional);
        unavailableOptional = Set.copyOf(unavailableOptional);
    }

    /** pack 是否可接受（无缺失必需旗标）。 */
    public boolean accepted() {
        return missingRequired.isEmpty();
    }

    /** 协商未执行（如解析期已有 ERROR）时的中性结果。 */
    public static LuminCapabilityReport notNegotiated() {
        return NOT_NEGOTIATED;
    }

    /** 人类可读摘要（诊断/日志用）。 */
    public String describe() {
        if (accepted() && unavailableOptional.isEmpty()) {
            return "all declared capabilities satisfied";
        }
        StringBuilder sb = new StringBuilder();
        if (!accepted()) {
            sb.append("missing required: ").append(missingRequired);
        }
        if (!unavailableOptional.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("optional unavailable: ").append(unavailableOptional);
        }
        return sb.toString();
    }
}
