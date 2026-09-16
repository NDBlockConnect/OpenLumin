package io.github.openlumin.shaderpack.parse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OptiFine 风格属性文件解析器（保序；**首声明优先**）。
 *
 * <p>与 Iris 的 OrderBackedProperties 同语义：{@code shaders.properties} 中同名键
 * <b>先出现者胜</b>，因此必须保序而不能用普通 Map 覆盖语义。支持：</p>
 * <ul>
 *   <li>{@code key=value} 与 {@code key: value}；</li>
 *   <li>{@code #} 与 {@code !} 起首的整行注释；</li>
 *   <li>行尾 {@code #} 注释（仅当其后无引号时按注释剥离）；</li>
 *   <li>反斜杠续行（以 {@code \} 结尾则与下一行拼接）；</li>
 *   <li>值中的空白保留（仅裁剪首尾）。</li>
 * </ul>
 *
 * <p>不处理：locale 编码转换（由调用方按 ISO-8859-1 读取后传入文本）。</p>
 */
public final class PropertiesParser {

    /** 解析结果：保序键值（首声明优先）+ 每键的源行号（诊断用）。 */
    public static final class ParsedProperties {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Integer> lines = new LinkedHashMap<>();

        void putIfAbsent(String key, String value, int line) {
            if (!values.containsKey(key)) {
                values.put(key, value);
                lines.put(key, line);
            }
        }

        public String get(String key) {
            return values.get(key);
        }

        public boolean contains(String key) {
            return values.containsKey(key);
        }

        /** 源行号（1 起）；键不存在返回 -1。 */
        public int lineOf(String key) {
            return lines.getOrDefault(key, -1);
        }

        /** 保序键集。 */
        public java.util.Set<String> keys() {
            return values.keySet();
        }

        public Map<String, String> asMap() {
            return java.util.Collections.unmodifiableMap(values);
        }

        public int size() {
            return values.size();
        }
    }

    /**
     * 解析属性文本。
     */
    public static ParsedProperties parse(String text) {
        ParsedProperties result = new ParsedProperties();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String[] rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder logical = new StringBuilder();
        int logicalStartLine = 0;
        for (int index = 0; index < rawLines.length; index++) {
            String line = rawLines[index];
            if (logical.isEmpty() && isBlankOrComment(line)) {
                continue;
            }
            if (logical.isEmpty()) {
                logicalStartLine = index + 1;
            }
            String trimmedEnd = line.stripTrailing();
            boolean continuation = trimmedEnd.endsWith("\\");
            String segment = continuation ? trimmedEnd.substring(0, trimmedEnd.length() - 1) : line;
            if (!logical.isEmpty()) {
                // 续行：折叠为单个空格（剔除两侧空白，避免出现大段空白）
                logical.append(' ').append(segment.strip());
            } else {
                logical.append(segment.stripTrailing());
            }
            if (continuation) {
                continue;
            }
            parseLogicalLine(logical.toString(), logicalStartLine, result);
            logical.setLength(0);
        }
        if (!logical.isEmpty()) {
            parseLogicalLine(logical.toString(), logicalStartLine, result);
        }
        return result;
    }

    private static void parseLogicalLine(String line, int lineNumber, ParsedProperties out) {
        String content = stripComment(line);
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        int separator = indexOfSeparator(trimmed);
        if (separator < 0) {
            // 无分隔符：OptiFine 视作布尔开关（key 自身）
            out.putIfAbsent(trimmed, "true", lineNumber);
            return;
        }
        String key = trimmed.substring(0, separator).trim();
        String value = trimmed.substring(separator + 1).trim();
        if (key.isEmpty()) {
            return;
        }
        out.putIfAbsent(key, unquote(value), lineNumber);
    }

    /** 去掉成对的定界引号（OptiFine 属性值的常见写法）。 */
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /** 找第一个未被引号包裹的 {@code =} 或 {@code :}。 */
    private static int indexOfSeparator(String text) {
        boolean inQuotes = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && (c == '=' || c == ':')) {
                return index;
            }
        }
        return -1;
    }

    /** 剥离行尾注释（{@code #} 或 {@code !} 起首；引号内不算）。 */
    private static String stripComment(String line) {
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char c = line.charAt(index);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && (c == '#' || c == '!')) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!");
    }

    private PropertiesParser() {
    }
}
