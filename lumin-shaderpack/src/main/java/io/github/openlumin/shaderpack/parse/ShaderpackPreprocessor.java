package io.github.openlumin.shaderpack.parse;

import io.github.openlumin.shaderpack.Diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 源文本预处理（WP-2 第 2 层，编译前最后一步）：include 展开 +
 * {@code #version/#extension} 顶部回填 + 选项 define 注入。
 *
 * <p>语义要点（与 OptiFine/Iris 对齐，见设计 §2.2）：</p>
 * <ul>
 *   <li>{@code #include} 在任何条件编译前展开（include guard 无效）——由
 *       {@link IncludeGraph#flatten(String)} 完成，重复包含会重复拼接；</li>
 *   <li>展开后 {@code #version} 可能出现在文件中部（非法）→ 提取首个 {@code #version}
 *       并回填到顶部；多个不一致的 {@code #version} 报 ERROR；</li>
 *   <li>{@code #extension} 行同样提取到 {@code #version} 之后，去重保序；</li>
 *   <li>选项/环境宏以 {@code #define NAME VALUE} 形式在扩展之后、正文之前注入
 *       （{@code VALUE} 为空时输出裸 {@code #define NAME}）。</li>
 * </ul>
 *
 * <p>纯 CPU、无副作用；错误经 {@link Diagnostic} 报告，不做静默修补以外的事。</p>
 */
public final class ShaderpackPreprocessor {

    /** 预处理结果。 */
    public record Result(String source, List<Diagnostic> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    private ShaderpackPreprocessor() {
    }

    /**
     * 预处理一个程序源文件。
     *
     * @param graph   该 pack 的 include 图（解析期已建，环/缺失已登记）
     * @param rootPath 源文件规范化路径（如 {@code shaders/composite1.fsh}）
     * @param defines  选项/环境宏（有序；值可为空字符串表示裸 define）
     * @return 扁平且可直接交给 GLSL 编译器的源文本 + 诊断
     */
    public static Result preprocess(IncludeGraph graph, String rootPath,
                                    Map<String, String> defines) {
        if (graph == null) {
            throw new NullPointerException("graph");
        }
        if (rootPath == null || rootPath.isEmpty()) {
            throw new IllegalArgumentException("rootPath must not be empty");
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        String path = IncludeGraph.normalize(rootPath);

        if (graph.hasCycles()) {
            for (IncludeGraph.Cycle cycle : graph.cycles()) {
                diagnostics.add(Diagnostic.error(path, 0,
                        "#include cycle detected: " + cycle.describe()));
            }
        }
        for (String missing : graph.missingIncludes()) {
            diagnostics.add(Diagnostic.error(path, 0,
                    "included file not found: " + missing));
        }

        String flat = graph.flatten(path);
        List<String> body = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        Set<String> extensions = new LinkedHashSet<>();
        for (String line : flat.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#version")) {
                versions.add(trimmed);
                continue;
            }
            if (trimmed.startsWith("#extension")) {
                extensions.add(trimmed);
                continue;
            }
            body.add(line);
        }

        String version = null;
        if (!versions.isEmpty()) {
            version = versions.get(0);
            for (String candidate : versions) {
                if (!candidate.equals(version)) {
                    diagnostics.add(Diagnostic.error(path, 0,
                            "conflicting #version directives: '" + version
                                    + "' vs '" + candidate + "'"));
                    break;
                }
            }
        }

        StringBuilder out = new StringBuilder(flat.length() + 64);
        if (version != null) {
            out.append(version).append('\n');
        }
        for (String extension : extensions) {
            out.append(extension).append('\n');
        }
        if (defines != null && !defines.isEmpty()) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(defines).entrySet()) {
                String value = entry.getValue();
                if (value == null || value.isEmpty()) {
                    out.append("#define ").append(entry.getKey()).append('\n');
                } else {
                    out.append("#define ").append(entry.getKey())
                            .append(' ').append(value).append('\n');
                }
            }
        }
        if (version != null || !extensions.isEmpty()
                || (defines != null && !defines.isEmpty())) {
            out.append('\n');
        }
        for (String line : body) {
            out.append(line).append('\n');
        }
        return new Result(out.toString(), diagnostics);
    }
}
