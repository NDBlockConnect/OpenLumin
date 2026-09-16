package io.github.openlumin.shaderpack.parse;

import io.github.openlumin.shaderpack.LuminTargetId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 源内指令解析（WP-2 §2.3）：{@code const} 常量声明与魔法注释。
 *
 * <p>识别项：</p>
 * <ul>
 *   <li>魔法注释 {@code /* DRAWBUFFERS:NNNN *}{@code /}（主）与
 *       {@code /* RENDERTARGETS:n,n *}{@code /}（备选）——**后出现者胜**；</li>
 *   <li>{@code const <type> <name> = <value>;} 与 {@code const <type> <name> = <value>, ...;}；</li>
 *   <li>整型常量中的十六进制（{@code 0xFF88FF}）与位或表达式（{@code A | B}）。</li>
 * </ul>
 *
 * <p>纯文本解析：不做类型系统检查（着色器编译器负责），只抽取引擎需要消费的指令。</p>
 */
public final class DirectiveParser {

    /** 一条 const 声明。 */
    public record ConstDeclaration(String type, String name, String rawValue, int line) {
    }

    /** 单文件解析结果。 */
    public record ParsedDirectives(List<ConstDeclaration> constants,
                                   Set<LuminTargetId> drawTargets,
                                   boolean hasDrawBuffersDirective,
                                   List<String> magicComments) {
    }

    private static final Pattern CONST_PATTERN = Pattern.compile(
            "^\\s*const\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;]+);");

    private static final Pattern DRAW_BUFFERS_PATTERN = Pattern.compile(
            "/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");

    private static final Pattern RENDERTARGETS_PATTERN = Pattern.compile(
            "/\\*\\s*RENDERTARGETS\\s*:\\s*([^\\*]+)\\*/");

    private static final Pattern MAGIC_COMMENT_PATTERN = Pattern.compile("/\\*\\s*([A-Z][A-Z0-9_]+)\\s*:[^\\*]*\\*/");

    /**
     * 解析单文件源文本。
     */
    public static ParsedDirectives parse(String source) {
        List<ConstDeclaration> constants = new ArrayList<>();
        List<String> magicComments = new ArrayList<>();
        Set<LuminTargetId> drawTargets = new LinkedHashSet<>();
        boolean hasDrawBuffers = false;

        if (source == null || source.isEmpty()) {
            return new ParsedDirectives(constants, drawTargets, false, magicComments);
        }
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int lineNumber = index + 1;

            Matcher constMatcher = CONST_PATTERN.matcher(line);
            if (constMatcher.find()) {
                constants.add(new ConstDeclaration(
                        constMatcher.group(1), constMatcher.group(2),
                        constMatcher.group(3).trim(), lineNumber));
            }

            Matcher magicMatcher = MAGIC_COMMENT_PATTERN.matcher(line);
            while (magicMatcher.find()) {
                magicComments.add(magicMatcher.group(0));
            }

            // DRAWBUFFERS 与 RENDERTARGETS：后出现者胜
            Matcher drawMatcher = DRAW_BUFFERS_PATTERN.matcher(line);
            if (drawMatcher.find()) {
                drawTargets = parseDrawBufferDigits(drawMatcher.group(1));
                hasDrawBuffers = true;
            }
            Matcher renderTargetsMatcher = RENDERTARGETS_PATTERN.matcher(line);
            if (renderTargetsMatcher.find()) {
                drawTargets = parseRenderTargetsList(renderTargetsMatcher.group(1));
                hasDrawBuffers = true;
            }
        }
        return new ParsedDirectives(constants, drawTargets, hasDrawBuffers, magicComments);
    }

    /** {@code DRAWBUFFERS:0123} → colortex0..3（数字逐位对应 colortex 索引）。 */
    static Set<LuminTargetId> parseDrawBufferDigits(String digits) {
        Set<LuminTargetId> targets = new LinkedHashSet<>();
        for (int index = 0; index < digits.length(); index++) {
            int target = digits.charAt(index) - '0';
            if (target >= 0 && target < LuminTargetId.Kind.COLOR.limit()) {
                targets.add(LuminTargetId.color(target));
            }
        }
        return targets;
    }

    /** {@code RENDERTARGETS:0,1,2} → 对应目标（支持 colortexN 名称与裸索引）。 */
    static Set<LuminTargetId> parseRenderTargetsList(String list) {
        Set<LuminTargetId> targets = new LinkedHashSet<>();
        for (String token : list.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int index = Integer.parseInt(trimmed);
                if (index >= 0 && index < LuminTargetId.Kind.COLOR.limit()) {
                    targets.add(LuminTargetId.color(index));
                }
                continue;
            } catch (NumberFormatException ignored) {
                // 落到名称解析
            }
            LuminTargetId parsed = LuminTargetId.parse(trimmed);
            if (parsed != null) {
                targets.add(parsed);
            }
        }
        return targets;
    }

    /**
     * 从 const 声明中按 name 取值（不区分大小写）。
     */
    public static ConstDeclaration findConst(ParsedDirectives directives, String name) {
        for (ConstDeclaration declaration : directives.constants()) {
            if (declaration.name().equalsIgnoreCase(name)) {
                return declaration;
            }
        }
        return null;
    }

    /**
     * 解析整型常量表达式的值：支持十进制、{@code 0x} 十六进制与 {@code A | B} 位或（递归）。
     *
     * @return 求值结果；无法解析返回 {@code defaultValue}
     */
    public static int parseIntExpression(String expression, int defaultValue) {
        if (expression == null) {
            return defaultValue;
        }
        String value = expression.trim();
        int or = value.lastIndexOf('|');
        if (or >= 0) {
            int left = parseIntExpression(value.substring(0, or), defaultValue);
            int right = parseIntExpression(value.substring(or + 1), defaultValue);
            return left | right;
        }
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return (int) Long.parseLong(value.substring(2), 16);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * 解析浮点常量表达式（支持简单后缀 {@code f}/{@code F}）。
     */
    public static float parseFloatExpression(String expression, float defaultValue) {
        if (expression == null) {
            return defaultValue;
        }
        String value = expression.trim();
        if (value.endsWith("f") || value.endsWith("F")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private DirectiveParser() {
    }
}
