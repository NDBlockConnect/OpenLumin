package io.github.openlumin.shaderpack;

/**
 * 渲染目标标识：colortex*、depthtex*、shadowtex*、shadowcolor*。
 *
 * <p>命名与语义（与 Iris 对齐，见 WP-2 设计 §5）：</p>
 * <ul>
 *   <li>{@link Kind#COLOR}：{@code colortexN}（0..31；0..7 另有 legacy 别名
 *       gcolor/gdepth/gnormal/composite/gaux1..4）；</li>
 *   <li>{@link Kind#DEPTH}：{@code depthtex0}（世界深度）、{@code depthtex1}（半透明前拷贝）、
 *       {@code depthtex2}（手部前拷贝）；</li>
 *   <li>{@link Kind#SHADOW_DEPTH}：{@code shadowtex0}（全阴影深度）、{@code shadowtex1}
 *       （排除半透明）；</li>
 *   <li>{@link Kind#SHADOW_COLOR}：{@code shadowcolorN}。</li>
 * </ul>
 */
public record LuminTargetId(Kind kind, int index) {

    public enum Kind {
        COLOR("colortex", 32),
        DEPTH("depthtex", 3),
        SHADOW_DEPTH("shadowtex", 2),
        SHADOW_COLOR("shadowcolor", 8);

        private final String prefix;
        private final int limit;

        Kind(String prefix, int limit) {
            this.prefix = prefix;
            this.limit = limit;
        }

        public String prefix() {
            return prefix;
        }

        public int limit() {
            return limit;
        }
    }

    /** colortexN（0..7）的 legacy 别名 → 索引映射。 */
    private static final String[] LEGACY_COLOR_ALIASES = {
            "gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"
    };

    public LuminTargetId {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (index < 0 || index >= kind.limit()) {
            throw new IllegalArgumentException(
                    kind.prefix() + " index must be in [0," + (kind.limit() - 1) + "], got " + index);
        }
    }

    public static LuminTargetId color(int index) {
        return new LuminTargetId(Kind.COLOR, index);
    }

    public static LuminTargetId depth(int index) {
        return new LuminTargetId(Kind.DEPTH, index);
    }

    public static LuminTargetId shadowDepth(int index) {
        return new LuminTargetId(Kind.SHADOW_DEPTH, index);
    }

    public static LuminTargetId shadowColor(int index) {
        return new LuminTargetId(Kind.SHADOW_COLOR, index);
    }

    /** 规范名（如 {@code colortex3}、{@code shadowtex1}）。 */
    public String canonicalName() {
        return kind.prefix() + index;
    }

    /**
     * 按名称解析（支持 colortexN / depthtexN / shadowtexN / shadowcolorN 与 colortex0..7 的 legacy 别名）。
     *
     * @return 目标标识；无法识别返回 null
     */
    public static LuminTargetId parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (int alias = 0; alias < LEGACY_COLOR_ALIASES.length; alias++) {
            if (LEGACY_COLOR_ALIASES[alias].equals(lower)) {
                return color(alias);
            }
        }
        for (Kind kind : Kind.values()) {
            String prefix = kind.prefix();
            if (lower.startsWith(prefix)) {
                String digits = lower.substring(prefix.length());
                if (digits.isEmpty()) {
                    continue;
                }
                try {
                    int index = Integer.parseInt(digits);
                    if (index >= 0 && index < kind.limit()) {
                        return new LuminTargetId(kind, index);
                    }
                    return null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return canonicalName();
    }
}
