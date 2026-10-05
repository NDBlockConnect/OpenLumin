package io.github.openlumin.shaderpack.capability;

import io.github.openlumin.shaderpack.LuminFeatureFlag;
import io.github.openlumin.shaderpack.ShaderpackIR;

import java.util.EnumSet;
import java.util.Set;

/**
 * 能力协商器（WP-2 M6）：{@link ShaderpackIR} 的 required/optional 声明
 * × 引擎 {@link LuminCapabilitySet} → {@link LuminCapabilityReport}。
 *
 * <p>纯 CPU、无副作用；协商语义与设计 §5 一致（required 缺失即拒绝，绝不静默降级）。</p>
 */
public final class LuminCapabilityNegotiator {

    private LuminCapabilityNegotiator() {
    }

    public static LuminCapabilityReport negotiate(ShaderpackIR ir, LuminCapabilitySet capabilities) {
        if (ir == null) {
            throw new NullPointerException("ir");
        }
        if (capabilities == null) {
            throw new NullPointerException("capabilities");
        }
        Set<LuminFeatureFlag> missingRequired = EnumSet.noneOf(LuminFeatureFlag.class);
        for (LuminFeatureFlag flag : ir.requiredFeatures()) {
            if (!capabilities.supports(flag)) {
                missingRequired.add(flag);
            }
        }
        Set<LuminFeatureFlag> providedOptional = EnumSet.noneOf(LuminFeatureFlag.class);
        Set<LuminFeatureFlag> unavailableOptional = EnumSet.noneOf(LuminFeatureFlag.class);
        for (LuminFeatureFlag flag : ir.optionalFeatures()) {
            if (capabilities.supports(flag)) {
                providedOptional.add(flag);
            } else {
                unavailableOptional.add(flag);
            }
        }
        return new LuminCapabilityReport(missingRequired, providedOptional, unavailableOptional);
    }
}
