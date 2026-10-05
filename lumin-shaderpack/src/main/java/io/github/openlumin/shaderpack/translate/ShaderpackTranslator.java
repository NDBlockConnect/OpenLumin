package io.github.openlumin.shaderpack.translate;

import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminShaderKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GLSL 翻译层（WP-2 编译前段）：把 OptiFine/Iris 风格的 legacy GLSL
 * （{@code #version 120/130}、{@code attribute/varying}、{@code gl_*} 内建、
 * {@code texture2D}、{@code ftransform}）翻译为 26.1.2 可编译的 {@code #version 330} 形式。
 *
 * <p>变换（均在**注释之外**生效——注释经占位遮蔽保护）：</p>
 * <ol>
 *   <li>版本归一：{@code #version < 330} 丢弃并顶部回填 {@code #version 330}；</li>
 *   <li>关键字：{@code attribute→in}；{@code varying→out(顶点)/in(片段)}；</li>
 *   <li>内建属性：{@code gl_Vertex→Position}、{@code gl_MultiTexCoord0..2→UV0..2}、
 *       {@code gl_Normal→Normal}、{@code gl_Color→Color}；</li>
 *   <li>函数：{@code texture2D/3D/Cube→texture}、{@code texture2DProj→textureProj}、
 *       {@code ftransform()→(ProjMat * ModelViewMat * vec4(Position, 1.0))}；</li>
 *   <li>片段输出：{@code gl_FragColor→fragColor}、{@code gl_FragData[n]→fragDataN}
 *       （并按引用注入 {@code out vec4} 声明）；</li>
 *   <li>按引用注入缺失声明（属性/输出/UBO 块
 *       {@code DynamicTransforms}+{@code Projection}，与 26.1.2 原生 include 逐字节同构）。</li>
 * </ol>
 *
 * <p>不可映射符号（如 {@code gl_FogFragCoord}/{@code gl_ModelViewMatrix}/{@code shadow2D}）
 * 产生 WARNING 清单，交由上层诊断——**不静默产出注定编译失败的源**。</p>
 */
public final class ShaderpackTranslator {

    /** 翻译结果。 */
    public record Result(String source, List<Diagnostic> diagnostics, boolean translated) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    /** 无法/暂不映射的 legacy 内建（诊断用）。 */
    private static final List<String> UNMAPPABLE_BUILTINS = List.of(
            "gl_FogFragCoord", "gl_ModelViewMatrix", "gl_ProjectionMatrix",
            "gl_ModelViewProjectionMatrix", "gl_NormalMatrix", "gl_TexCoord",
            "shadow2D", "shadow2DProj");

    /** 26.1.2 原生 UBO 声明（与 vanilla include 同构；供 ftransform 等映射使用）。 */
    private static final String DYNAMIC_TRANSFORMS_BLOCK = """
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
                mat4 TextureMat;
            };
            """;
    private static final String PROJECTION_BLOCK = """
            layout(std140) uniform Projection {
                mat4 ProjMat;
            };
            """;

    private static final Pattern VERSION_LINE =
            Pattern.compile("^\\s*#version\\s+(\\d+).*$", Pattern.MULTILINE);
    private static final Pattern IDENT = Pattern.compile("(?<![A-Za-z0-9_])(%s)(?![A-Za-z0-9_])");

    private ShaderpackTranslator() {
    }

    /**
     * 翻译一个着色器阶段。
     *
     * @param source          预处理后的源文本（include 已展开）
     * @param kind            阶段（决定 {@code varying} 方向）
     * @param drawTargetCount 该程序的绘制目标数（决定 {@code gl_FragData} 输出声明上限）
     * @return 翻译结果；无 legacy 特征时原样返回（{@code translated=false}）
     */
    public static Result translate(String source, LuminShaderKind kind, int drawTargetCount) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        CommentMask mask = CommentMask.of(source);
        String code = mask.masked();

        if (!isLegacy(code)) {
            return new Result(source, List.of(), false);
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (String builtin : UNMAPPABLE_BUILTINS) {
            if (containsIdent(code, builtin)) {
                diagnostics.add(Diagnostic.warning("<translate>", 0,
                        "unmappable legacy builtin '" + builtin
                                + "'; translated source may not compile"));
            }
        }

        boolean legacyVersion = hasPre330Version(code);
        code = VERSION_LINE.matcher(code).replaceAll("");
        code = replaceIdent(code, "attribute", "in");
        code = replaceIdent(code, "varying",
                kind == LuminShaderKind.VERTEX ? "out" : "in");
        code = replaceIdent(code, "gl_Vertex", "Position");
        code = replaceIdent(code, "gl_MultiTexCoord0", "UV0");
        code = replaceIdent(code, "gl_MultiTexCoord1", "UV1");
        code = replaceIdent(code, "gl_MultiTexCoord2", "UV2");
        code = replaceIdent(code, "gl_Normal", "Normal");
        code = replaceIdent(code, "gl_Color", "Color");
        code = replaceIdent(code, "texture2DProj", "textureProj");
        code = replaceIdent(code, "texture2D", "texture");
        code = replaceIdent(code, "texture3D", "texture");
        code = replaceIdent(code, "textureCube", "texture");
        code = code.replaceAll(
                "(?<![A-Za-z0-9_])ftransform\\s*\\(\\s*\\)",
                Matcher.quoteReplacement("(ProjMat * ModelViewMat) * vec4(Position, 1.0)"));
        code = code.replaceAll(
                "(?<![A-Za-z0-9_])gl_FragData\\s*\\[\\s*(\\d+)\\s*\\]",
                "fragData$1");
        code = replaceIdent(code, "gl_FragColor", "fragColor");

        StringBuilder prelude = new StringBuilder();
        if (legacyVersion || !VERSION_LINE.matcher(code).find()) {
            prelude.append("#version 330\n");
        }
        if (containsIdent(code, "ProjMat") || containsIdent(code, "ModelViewMat")) {
            if (!code.contains("uniform DynamicTransforms")) {
                prelude.append(DYNAMIC_TRANSFORMS_BLOCK);
            }
            if (!code.contains("uniform Projection")) {
                prelude.append(PROJECTION_BLOCK);
            }
        }
        prelude.append(attributeDeclarations(code));
        prelude.append(fragmentOutputDeclarations(code, kind));

        String restored = mask.restore(prelude + "\n" + code);
        return new Result(restored, diagnostics, true);
    }

    private static boolean isLegacy(String code) {
        if (hasPre330Version(code)) {
            return true;
        }
        for (String marker : List.of("attribute", "varying", "gl_FragColor", "gl_FragData",
                "texture2D", "texture3D", "ftransform", "gl_Vertex", "gl_MultiTexCoord",
                "gl_Normal", "gl_Color")) {
            if (containsIdent(code, marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPre330Version(String code) {
        Matcher matcher = VERSION_LINE.matcher(code);
        while (matcher.find()) {
            try {
                if (Integer.parseInt(matcher.group(1)) < 330) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 非数字版本行交给下游
            }
        }
        return false;
    }

    private static boolean containsIdent(String code, String name) {
        return Pattern.compile(IDENT.pattern().formatted(Pattern.quote(name)))
                .matcher(code).find();
    }

    private static String replaceIdent(String code, String name, String replacement) {
        return Pattern.compile(IDENT.pattern().formatted(Pattern.quote(name)))
                .matcher(code).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String attributeDeclarations(String code) {
        Map<String, String> declarations = new LinkedHashMap<>();
        declarations.put("Position", "in vec3 Position;");
        declarations.put("UV0", "in vec2 UV0;");
        declarations.put("UV1", "in vec2 UV1;");
        declarations.put("UV2", "in vec2 UV2;");
        declarations.put("Normal", "in vec3 Normal;");
        declarations.put("Color", "in vec4 Color;");
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : declarations.entrySet()) {
            String name = entry.getKey();
            if (containsIdent(code, name) && !declaredAs(code, name)) {
                out.append(entry.getValue()).append('\n');
            }
        }
        return out.toString();
    }

    private static String fragmentOutputDeclarations(String code, LuminShaderKind kind) {
        if (kind != LuminShaderKind.FRAGMENT) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (containsIdent(code, "fragColor") && !declaredAs(code, "fragColor")) {
            out.append("out vec4 fragColor;\n");
        }
        Set<String> dataOuts = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?<![A-Za-z0-9_])(fragData\\d+)(?![A-Za-z0-9_])")
                .matcher(code);
        while (matcher.find()) {
            dataOuts.add(matcher.group(1));
        }
        for (String name : dataOuts) {
            if (!declaredAs(code, name)) {
                out.append("out vec4 ").append(name).append(";\n");
            }
        }
        return out.toString();
    }

    private static boolean declaredAs(String code, String name) {
        Pattern pattern = Pattern.compile(
                "(?m)^\\s*(?:in|out|attribute|varying)\\s+\\w+\\s+"
                        + Pattern.quote(name) + "\\s*;");
        return pattern.matcher(code).find();
    }

    /** 注释占位遮蔽：注释内容不参与重写，最后原样恢复。 */
    private static final class CommentMask {
        private final String masked;
        private final List<String> comments;

        private CommentMask(String masked, List<String> comments) {
            this.masked = masked;
            this.comments = comments;
        }

        static CommentMask of(String source) {
            StringBuilder out = new StringBuilder(source.length());
            List<String> comments = new ArrayList<>();
            int index = 0;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    int end = source.indexOf('\n', index);
                    end = end < 0 ? source.length() : end;
                    out.append(placeholder(comments.size()));
                    comments.add(source.substring(index, end));
                    index = end;
                } else if (c == '/' && index + 1 < source.length()
                        && source.charAt(index + 1) == '*') {
                    int end = source.indexOf("*/", index + 2);
                    end = end < 0 ? source.length() : end + 2;
                    out.append(placeholder(comments.size()));
                    comments.add(source.substring(index, end));
                    index = end;
                } else {
                    out.append(c);
                    index++;
                }
            }
            return new CommentMask(out.toString(), comments);
        }

        private static String placeholder(int index) {
            return "\u0001C" + index + "C\u0001";
        }

        String masked() {
            return masked;
        }

        String restore(String text) {
            String result = text;
            for (int i = 0; i < comments.size(); i++) {
                result = result.replace(placeholder(i), comments.get(i));
            }
            return result;
        }
    }
}
