package io.github.openlumin.shaderpack;

/**
 * 解析期诊断（带位置与严重级）。
 *
 * <p>设计目标（相对 Iris 的超集点之一）：**解析错误必须可定位**——给出源文件、行号与
 * 上下文，而不是仅仅"pack 加载失败"。{@link Severity#ERROR} 会阻止该 pack 被接受
 * （能力旗标缺失另见 {@link LuminFeatureFlag} 的协商语义）。</p>
 */
public record Diagnostic(Severity severity, String source, int line, String message) {

    public enum Severity {
        /** 致命：pack 不可用。 */
        ERROR,
        /** 可容忍但需告知（如未知指令被忽略）。 */
        WARNING,
        /** 信息（统计/提示）。 */
        INFO
    }

    public Diagnostic {
        if (severity == null) {
            throw new NullPointerException("severity");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must not be empty");
        }
        source = source == null ? "<unknown>" : source;
    }

    public static Diagnostic error(String source, int line, String message) {
        return new Diagnostic(Severity.ERROR, source, line, message);
    }

    public static Diagnostic warning(String source, int line, String message) {
        return new Diagnostic(Severity.WARNING, source, line, message);
    }

    public static Diagnostic info(String source, int line, String message) {
        return new Diagnostic(Severity.INFO, source, line, message);
    }

    /** 人类可读格式：{@code source:line [SEVERITY] message}。 */
    public String format() {
        return source + ":" + line + " [" + severity + "] " + message;
    }

    @Override
    public String toString() {
        return format();
    }
}
