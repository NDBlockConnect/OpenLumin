package io.github.openlumin.shaderpack.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code #include} 依赖图（WP-2 §2.2）。
 *
 * <p>语义要点（与 OptiFine/Iris 对齐）：</p>
 * <ul>
 *   <li>{@code #include} 是**源文本拼接**，在任何条件编译之前展开——因此 include guard
 *       （{@code #ifndef/#define}）**不起作用**，这必须在文档与诊断中明示；</li>
 *   <li>相对路径以**包含者所在目录**为基准；绝对路径（以 {@code /} 开头）以包根为基准；</li>
 *   <li>环路（含自环）必须被检测并**报告完整闭环路径**（相对 Iris 的诊断增强点）。</li>
 * </ul>
 *
 * <p>本类只负责"图"——文件读取由 {@link SourceProvider} 注入，便于合成夹具测试。</p>
 */
public final class IncludeGraph {

    /** 源文件读取抽象（测试用内存实现，运行时用目录/压缩包实现）。 */
    @FunctionalInterface
    public interface SourceProvider {
        /**
         * @param path 规范化路径（相对包根的 {@code /} 分隔路径）
         * @return 文件内容；不存在返回 null
         */
        String read(String path);
    }

    /** 图节点：一个规范化路径。 */
    public static final class Node {
        private final String path;
        private final Set<String> includes = new LinkedHashSet<>();

        Node(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        /** 本文件直接包含的路径（保序、去重）。 */
        public Set<String> includes() {
            return includes;
        }
    }

    /** 环路报告。 */
    public record Cycle(List<String> path) {
        public String describe() {
            return String.join(" -> ", path);
        }
    }

    private final Map<String, Node> nodes = new HashMap<>();
    private final SourceProvider provider;
    private final List<Cycle> cycles = new ArrayList<>();
    private final List<String> missing = new ArrayList<>();

    public IncludeGraph(SourceProvider provider) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        this.provider = provider;
    }

    /**
     * 登记一个根文件并递归解析其 include。
     *
     * @param path 规范化路径
     * @return 该路径的节点；文件不存在返回 null
     */
    public Node addRoot(String path) {
        return visit(normalize(path), new ArrayList<>());
    }

    public Node node(String path) {
        return nodes.get(normalize(path));
    }

    public Set<String> paths() {
        return nodes.keySet();
    }

    /** 检测到的环路（去重后）。 */
    public List<Cycle> cycles() {
        return List.copyOf(cycles);
    }

    public boolean hasCycles() {
        return !cycles.isEmpty();
    }

    /** 被引用但不存在的 include 目标（保序、去重）。 */
    public List<String> missingIncludes() {
        return List.copyOf(missing);
    }

    private Node visit(String path, List<String> stack) {
        Node existing = nodes.get(path);
        if (existing != null) {
            // 已在图中：若它出现在当前 DFS 栈上 → 环路
            int index = stack.indexOf(path);
            if (index >= 0) {
                List<String> cyclePath = new ArrayList<>(stack.subList(index, stack.size()));
                cyclePath.add(path);
                Cycle cycle = new Cycle(List.copyOf(cyclePath));
                if (!cycles.contains(cycle)) {
                    cycles.add(cycle);
                }
            }
            return existing;
        }
        String content = provider.read(path);
        if (content == null) {
            if (!missing.contains(path)) {
                missing.add(path);
            }
            return null;
        }
        Node node = new Node(path);
        nodes.put(path, node);
        stack.add(path);
        try {
            for (String raw : scanIncludes(content)) {
                String resolved = resolve(path, raw);
                node.includes.add(resolved);
                visit(resolved, stack);
            }
        } finally {
            stack.remove(stack.size() - 1);
        }
        return node;
    }

    /**
     * 扫描源文本中的 {@code #include} 指令（跳过行注释内的伪指令）。
     *
     * <p>支持两种写法：{@code #include "path"} 与 {@code #include <path>}。</p>
     */
    public static List<String> scanIncludes(String source) {
        List<String> includes = new ArrayList<>();
        for (String rawLine : source.split("\n", -1)) {
            String line = stripLineComment(rawLine).trim();
            if (!line.startsWith("#")) {
                continue;
            }
            String directive = line.substring(1).trim();
            if (!directive.startsWith("include")) {
                continue;
            }
            String rest = directive.substring("include".length()).trim();
            if (rest.isEmpty()) {
                continue;
            }
            char first = rest.charAt(0);
            char last = rest.charAt(rest.length() - 1);
            if ((first == '"' && last == '"') || (first == '<' && last == '>')) {
                includes.add(rest.substring(1, rest.length() - 1).trim());
            }
        }
        return includes;
    }

    /** 解析相对/绝对 include 路径为规范化路径。 */
    public static String resolve(String includerPath, String includedPath) {
        if (includedPath.startsWith("/")) {
            return normalize(includedPath.substring(1));
        }
        int slash = includerPath.lastIndexOf('/');
        String directory = slash < 0 ? "" : includerPath.substring(0, slash + 1);
        return normalize(directory + includedPath);
    }

    /**
     * 路径规范化：统一分隔符、解析 {@code .} 与 {@code ..}、去掉前导 {@code /}。
     */
    public static String normalize(String path) {
        String unified = path.replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : unified.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(part);
        }
        return String.join("/", parts);
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }

    /**
     * 展开为扁平文本：按出现顺序**原地内联**每次 include（重复包含会重复文本——
     * 与 OptiFine/Iris 语义一致，故 include guard 无效）；仅以递归栈防环。
     */
    public String flatten(String rootPath) {
        StringBuilder out = new StringBuilder();
        flattenInto(normalize(rootPath), out, new ArrayList<>());
        return out.toString();
    }

    private void flattenInto(String path, StringBuilder out, List<String> stack) {
        if (stack.contains(path)) {
            // 环路：不内联（避免无限展开）；环路本身已由 cycles() 报告
            return;
        }
        String content = provider.read(path);
        if (content == null) {
            return;
        }
        stack.add(path);
        try {
            for (String line : content.split("\n", -1)) {
                List<String> scanned = scanIncludes(line + "\n");
                if (scanned.isEmpty()) {
                    out.append(line).append('\n');
                    continue;
                }
                // include 行本身不进入输出，改以其目标内容替换
                for (String raw : scanned) {
                    flattenInto(resolve(path, raw), out, stack);
                }
            }
        } finally {
            stack.remove(stack.size() - 1);
        }
    }
}
