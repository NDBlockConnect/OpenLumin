package io.github.openlumin.shaderpack.capability;

import io.github.openlumin.shaderpack.LuminFeatureFlag;

import java.util.EnumSet;
import java.util.Set;

/**
 * 引擎能力集（WP-2 M6）：某一后端/版本线上**实际具备**的 {@link LuminFeatureFlag} 集合。
 *
 * <p>与 {@link io.github.openlumin.shaderpack.ShaderpackIR} 的 required/optional 声明做协商
 * （见 {@link LuminCapabilityNegotiator}）。纯 CPU、不可变；平台层负责按后端真实能力构造。</p>
 */
public final class LuminCapabilitySet {

    private final Set<LuminFeatureFlag> supported;

    private LuminCapabilitySet(Set<LuminFeatureFlag> supported) {
        this.supported = Set.copyOf(supported);
    }

    public static LuminCapabilitySet of(LuminFeatureFlag... flags) {
        EnumSet<LuminFeatureFlag> set = EnumSet.noneOf(LuminFeatureFlag.class);
        for (LuminFeatureFlag flag : flags) {
            if (flag != null) {
                set.add(flag);
            }
        }
        return new LuminCapabilitySet(set);
    }

    public static LuminCapabilitySet of(Set<LuminFeatureFlag> flags) {
        return new LuminCapabilitySet(flags);
    }

    /** 空能力集（测试/占位；所有 required 均不满足）。 */
    public static LuminCapabilitySet none() {
        return of(EnumSet.noneOf(LuminFeatureFlag.class));
    }

    public boolean supports(LuminFeatureFlag flag) {
        return supported.contains(flag);
    }

    public Set<LuminFeatureFlag> supported() {
        return supported;
    }
}
