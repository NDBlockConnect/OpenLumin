package io.github.openlumin.shaderpack.parse;

import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminBufferFormat;
import io.github.openlumin.shaderpack.LuminCustomUniform;
import io.github.openlumin.shaderpack.LuminFeatureFlag;
import io.github.openlumin.shaderpack.LuminPackDirectives;
import io.github.openlumin.shaderpack.LuminProgramGroup;
import io.github.openlumin.shaderpack.LuminProgramId;
import io.github.openlumin.shaderpack.LuminProgramSource;
import io.github.openlumin.shaderpack.LuminShaderKind;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.LuminTargetSpec;
import io.github.openlumin.shaderpack.ShaderpackIR;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shaderpack 加载器（WP-2 第 1–3 层）：发现 → 解析 → {@link ShaderpackIR}。
 *
 * <p>纯 CPU、零 GPU/MC 依赖；文件访问经 {@link IncludeGraph.SourceProvider} 注入，
 * 因此可用内存夹具完整单测。include 展开**不在此处**做（编译期前才展开），
 * 本类只建图并登记程序源。</p>
 */
public final class ShaderpackLoader {

    /** pack 内着色器根的路径前缀。 */
    public static final String SHADER_ROOT = "shaders/";

    private final IncludeGraph.SourceProvider provider;
    private final String packName;

    public ShaderpackLoader(String packName, IncludeGraph.SourceProvider provider) {
        this.packName = packName == null ? "<unnamed>" : packName;
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        this.provider = provider;
    }

    /**
     * 加载 pack。
     *
     * @param paths pack 内**全部**文件路径（相对 pack 根、{@code /} 分隔）；
     *              由调用方枚举（目录或压缩包），使本类与存储介质解耦
     */
    public ShaderpackIR load(List<String> paths) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> normalized = new LinkedHashSet<>();
        for (String path : paths) {
            normalized.add(IncludeGraph.normalize(path));
        }

        // ── 第 1 层：发现 ──
        Set<String> dimensions = new LinkedHashSet<>();
        Set<String> programsToLoad = new LinkedHashSet<>();
        // include 图覆盖**全部**着色器源文件（含未被识别为程序的辅助文件）——
        // 否则"被 include 的辅助文件里的环/缺失"会被漏检
        Set<String> shaderSources = new LinkedHashSet<>();
        for (String path : normalized) {
            if (!path.startsWith(SHADER_ROOT)) {
                continue;
            }
            String relative = path.substring(SHADER_ROOT.length());
            String[] segments = relative.split("/");
            if (segments.length >= 2 && segments[0].startsWith("world")) {
                dimensions.add(segments[0]);
                // 维度文件夹内的程序源同样登记（其覆盖语义在编译期按维度选择）
                if (isProgramSource(segments[segments.length - 1])) {
                    shaderSources.add(path);
                    if (isRecognisedProgram(segments[segments.length - 1])) {
                        programsToLoad.add(path);
                    } else {
                        diagnostics.add(Diagnostic.warning(path, 0,
                                "unrecognised program base name: " + segments[segments.length - 1]));
                    }
                }
                continue;
            }
            if (isProgramSource(segments[segments.length - 1])) {
                shaderSources.add(path);
                if (isRecognisedProgram(segments[segments.length - 1])) {
                    programsToLoad.add(path);
                } else {
                    diagnostics.add(Diagnostic.warning(path, 0,
                            "unrecognised program base name: " + segments[segments.length - 1]));
                }
            }
        }

        // ── 第 2 层：include 图 ──
        IncludeGraph includeGraph = new IncludeGraph(provider);
        for (String path : shaderSources) {
            includeGraph.addRoot(path);
        }
        for (IncludeGraph.Cycle cycle : includeGraph.cycles()) {
            diagnostics.add(Diagnostic.error(SHADER_ROOT, 0,
                    "#include cycle detected: " + cycle.describe()));
        }
        for (String missingInclude : includeGraph.missingIncludes()) {
            diagnostics.add(Diagnostic.error(SHADER_ROOT, 0,
                    "included file not found: " + missingInclude));
        }

        // ── 第 3 层：解析为 IR ──
        Map<LuminProgramId, MutableProgram> programs = new LinkedHashMap<>();
        Map<LuminTargetId, LuminTargetSpec> targets = new LinkedHashMap<>();
        LuminPackDirectives directives = parseProperties(diagnostics, targets);

        for (String path : programsToLoad) {
            String relative = path.substring(SHADER_ROOT.length());
            if (relative.contains("/")) {
                relative = relative.substring(relative.lastIndexOf('/') + 1);
            }
            int dot = relative.lastIndexOf('.');
            if (dot < 0) {
                continue;
            }
            String baseName = relative.substring(0, dot);
            LuminShaderKind kind = LuminShaderKind.fromExtension(relative.substring(dot + 1));
            if (kind == null) {
                continue;
            }
            LuminProgramId id = identifyProgram(baseName);
            if (id == null) {
                // 未知基名已在发现阶段产生 WARNING（此处不再重复）
                continue;
            }
            String source = provider.read(path);
            if (source == null) {
                continue;
            }
            MutableProgram program = programs.computeIfAbsent(id, MutableProgram::new);
            program.sources.put(kind, source);
            program.paths.put(kind, path);
            // 源代码级指令：DRAWBUFFERS/RENDERTARGETS + colortex 格式/清屏等
            DirectiveParser.ParsedDirectives parsed = DirectiveParser.parse(source);
            if (parsed.hasDrawBuffersDirective()) {
                program.drawTargets = parsed.drawTargets();
            }
            applyConstDirectives(parsed, targets, directives);
        }

        List<LuminCustomUniform> customUniforms = parseCustomUniforms(diagnostics);
        Set<LuminFeatureFlag> required = parseFeatureFlags("iris.features.required", diagnostics);
        Set<LuminFeatureFlag> optional = parseFeatureFlags("iris.features.optional", diagnostics);

        Map<LuminProgramId, LuminProgramSource> programSources = new LinkedHashMap<>();
        for (Map.Entry<LuminProgramId, MutableProgram> entry : programs.entrySet()) {
            programSources.put(entry.getKey(), entry.getValue().toImmutable());
        }

        boolean hasSources = !programSources.isEmpty();
        if (!hasSources) {
            diagnostics.add(Diagnostic.error(SHADER_ROOT, 0,
                    "no shader programs found under " + SHADER_ROOT));
        }

        ShaderpackIR.PackMetadata metadata = new ShaderpackIR.PackMetadata(
                packName, "<provided>", dimensions, hasSources);
        return new ShaderpackIR(metadata, programSources, directives, targets,
                customUniforms, required, optional, includeGraph, diagnostics);
    }

    /** 是否形如 {@code <name>.<kind>}。 */
    private static boolean isProgramSource(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return LuminShaderKind.fromExtension(fileName.substring(dot + 1)) != null;
    }

    /** 基名是否可识别为已知程序（否则仅参与 include 图，并产生 WARNING）。 */
    private static boolean isRecognisedProgram(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return identifyProgram(fileName.substring(0, dot)) != null;
    }

    /**
     * 由文件基名识别程序标识。
     *
     * <ul>
     *   <li>{@code gbuffers_<name>} → GBUFFERS 组；</li>
     *   <li>{@code shadow} / {@code shadow_<name>} → SHADOW 组；</li>
     *   <li>{@code <group>} / {@code <group><n>} → 合成组；</li>
     *   <li>{@code final} → FINAL。</li>
     * </ul>
     */
    public static LuminProgramId identifyProgram(String baseName) {
        String lower = baseName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("gbuffers_")) {
            String name = lower.substring("gbuffers_".length());
            if (!LuminProgramId.GBUFFER_NAMES.contains(name)) {
                return null;
            }
            return LuminProgramId.of(LuminProgramGroup.GBUFFERS, name);
        }
        if (lower.equals("shadow")) {
            return LuminProgramId.of(LuminProgramGroup.SHADOW, "shadow");
        }
        if (lower.startsWith("shadow_")) {
            String name = lower.substring("shadow_".length());
            if (!LuminProgramId.SHADOW_NAMES.contains(name)) {
                return null;
            }
            return LuminProgramId.of(LuminProgramGroup.SHADOW, name);
        }
        for (LuminProgramGroup group : LuminProgramGroup.values()) {
            String identifier = group.identifier();
            if (lower.equals(identifier)) {
                return new LuminProgramId(group, identifier, 0);
            }
            if (group.isNumbered() && lower.startsWith(identifier)) {
                String digits = lower.substring(identifier.length());
                if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
                    int index = Integer.parseInt(digits);
                    if (index > 0 && index <= LuminProgramId.MAX_INDEX) {
                        return new LuminProgramId(group, identifier, index);
                    }
                }
            }
        }
        return null;
    }

    /** 把源内 const 指令并入目标规格（colortexNFormat/Clear/ClearColor/MipmapEnabled 等）。 */
    private static void applyConstDirectives(DirectiveParser.ParsedDirectives parsed,
                                             Map<LuminTargetId, LuminTargetSpec> targets,
                                             LuminPackDirectives directives) {
        for (DirectiveParser.ConstDeclaration declaration : parsed.constants()) {
            String name = declaration.name();
            // 形如 colortex3Format / shadowcolor1ClearColor
            LuminTargetId target = targetOfDirective(name);
            if (target == null) {
                continue;
            }
            String suffix = name.toLowerCase(Locale.ROOT)
                    .substring(target.canonicalName().length());
            LuminTargetSpec spec = targets.getOrDefault(target, LuminTargetSpec.defaults());
            switch (suffix) {
                case "format" -> {
                    LuminBufferFormat format = LuminBufferFormat.parse(declaration.rawValue());
                    if (format != null) {
                        targets.put(target, spec.withFormat(format));
                    }
                }
                case "clear" -> targets.put(target, spec.withClear(
                        Boolean.parseBoolean(declaration.rawValue().trim())));
                case "clearcolor" -> {
                    float[] color = parseClearColor(declaration.rawValue());
                    if (color != null) {
                        targets.put(target, spec.withClearColor(color));
                    }
                }
                case "mipmapenabled" -> targets.put(target, spec.withMipmap(
                        Boolean.parseBoolean(declaration.rawValue().trim())));
                default -> {
                    // 其它后缀（如 shadowHardwareFiltering）由执行层处理，此处忽略
                }
            }
        }
    }

    /** 从指令名中切出目标前缀（{@code colortex3Format} → colortex3）。 */
    public static LuminTargetId targetOfDirective(String declarationName) {
        String lower = declarationName.toLowerCase(Locale.ROOT);
        for (LuminTargetId.Kind kind : LuminTargetId.Kind.values()) {
            String prefix = kind.prefix();
            if (!lower.startsWith(prefix)) {
                continue;
            }
            int indexEnd = prefix.length();
            while (indexEnd < lower.length() && Character.isDigit(lower.charAt(indexEnd))) {
                indexEnd++;
            }
            if (indexEnd == prefix.length()) {
                continue;
            }
            try {
                int index = Integer.parseInt(lower.substring(prefix.length(), indexEnd));
                if (index >= 0 && index < kind.limit()) {
                    return new LuminTargetId(kind, index);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 解析清屏色：{@code vec4(...)} 或 4 个数字（含 0-255 整数与 0..1 浮点）。 */
    public static float[] parseClearColor(String expression) {
        if (expression == null) {
            return null;
        }
        String value = expression.trim();
        int paren = value.indexOf('(');
        if (paren >= 0 && value.endsWith(")")) {
            value = value.substring(paren + 1, value.length() - 1);
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        float[] color = new float[4];
        for (int index = 0; index < 4; index++) {
            String part = parts[index].trim();
            if (part.endsWith("f") || part.endsWith("F")) {
                part = part.substring(0, part.length() - 1);
            }
            try {
                color[index] = Float.parseFloat(part);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return color;
    }

    /** 解析 shaders.properties 为包级指令（含 scale/blend/alphaTest/size/阴影指令）。 */
    private LuminPackDirectives parseProperties(List<Diagnostic> diagnostics,
                                               Map<LuminTargetId, LuminTargetSpec> targets) {
        String text = provider.read(SHADER_ROOT + "shaders.properties");
        if (text == null) {
            return LuminPackDirectives.empty();
        }
        PropertiesParser.ParsedProperties properties = PropertiesParser.parse(text);
        Map<String, Boolean> switches = new LinkedHashMap<>();
        Map<LuminProgramId, LuminPackDirectives.ViewportScale> scales = new LinkedHashMap<>();
        Map<LuminProgramId, LuminPackDirectives.BlendOverride> blends = new LinkedHashMap<>();
        Map<LuminProgramId, Float> alphaTests = new LinkedHashMap<>();
        Map<String, LuminPackDirectives.BufferSize> bufferSizes = new LinkedHashMap<>();
        Map<String, String> samplerTextures = new LinkedHashMap<>();

        for (String key : properties.keys()) {
            String value = properties.get(key);
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("scale.")) {
                LuminProgramId program = identifyProgram(lower.substring("scale.".length()));
                if (program != null) {
                    scales.put(program, parseScale(value));
                }
                continue;
            }
            if (lower.startsWith("alphaTest.".toLowerCase(Locale.ROOT))) {
                LuminProgramId program = identifyProgram(lower.substring("alphatest.".length()));
                if (program != null) {
                    alphaTests.put(program, parseFloatOr(value, 0f));
                }
                continue;
            }
            if (lower.startsWith("size.buffer.")) {
                String buffer = lower.substring("size.buffer.".length());
                LuminPackDirectives.BufferSize size = parseBufferSize(value);
                if (size != null) {
                    bufferSizes.put(buffer, size);
                }
                continue;
            }
            if (lower.startsWith("texture.")) {
                samplerTextures.put(key, value);
                continue;
            }
            if (lower.startsWith("blend.")) {
                // blend.<pass>[.<buffer>] = <on|off> [src dst]；缓冲级在解析层不细分
                String passName = lower.substring("blend.".length());
                int dot = passName.indexOf('.');
                if (dot > 0) {
                    passName = passName.substring(0, dot);
                }
                LuminProgramId program = identifyProgram(passName);
                if (program != null) {
                    blends.put(program, parseBlend(value));
                }
                continue;
            }
            if (lower.startsWith("uniform.") || lower.startsWith("variable.")
                    || lower.startsWith("screen") || lower.startsWith("sliders")
                    || lower.startsWith("profile.") || lower.startsWith("option.")
                    || lower.startsWith("lang")) {
                continue; // 分别由 custom uniform / 选项菜单层处理
            }
            switches.put(key, parseBooleanOr(value, true));
        }

        LuminPackDirectives.ShadowDirectives shadow = parseShadowDirectives(properties);
        return new LuminPackDirectives(switches, scales, blends, alphaTests,
                bufferSizes, shadow, samplerTextures);
    }

    private static LuminPackDirectives.ViewportScale parseScale(String value) {
        if (value == null) {
            return LuminPackDirectives.ViewportScale.of(1f);
        }
        String[] parts = value.trim().split("\\s+");
        try {
            float scale = Float.parseFloat(parts[0]);
            float offsetX = parts.length > 1 ? Float.parseFloat(parts[1]) : 0f;
            float offsetY = parts.length > 2 ? Float.parseFloat(parts[2]) : 0f;
            return new LuminPackDirectives.ViewportScale(scale, offsetX, offsetY);
        } catch (RuntimeException ignored) {
            return LuminPackDirectives.ViewportScale.of(1f);
        }
    }

    private static LuminPackDirectives.BlendOverride parseBlend(String value) {
        if (value == null) {
            return new LuminPackDirectives.BlendOverride(false, 0, 0);
        }
        String[] parts = value.trim().split("\\s+");
        boolean enabled = parseBooleanOr(parts[0], false);
        int source = parts.length > 1 ? parseBlendFactor(parts[1]) : 1;
        int destination = parts.length > 2 ? parseBlendFactor(parts[2]) : 0;
        return new LuminPackDirectives.BlendOverride(enabled, source, destination);
    }

    /** 混合因子名 → 引擎中立序号（执行层映射为后端常量）。 */
    private static int parseBlendFactor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "zero" -> 0;
            case "one" -> 1;
            case "srccolor" -> 2;
            case "oneminus srccolor", "oneminussrccolor" -> 3;
            case "srcalpha" -> 4;
            case "oneminussrcalpha" -> 5;
            case "dstalpha" -> 6;
            case "oneminusdstalpha" -> 7;
            case "dstcolor" -> 8;
            case "oneminusdstcolor" -> 9;
            default -> 1;
        };
    }

    /**
     * 解析 {@code size.buffer} 值：**含小数点 → 相对**（屏幕倍数），不含 → 绝对像素
     * （与 Iris {@code TextureScaleOverride} 语义一致；X/Y 独立判定）。
     */
    private static LuminPackDirectives.BufferSize parseBufferSize(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        try {
            boolean widthRelative = parts[0].contains(".");
            boolean heightRelative = parts[1].contains(".");
            return new LuminPackDirectives.BufferSize(
                    Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
                    widthRelative, heightRelative);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LuminPackDirectives.ShadowDirectives parseShadowDirectives(
            PropertiesParser.ParsedProperties properties) {
        LuminPackDirectives.ShadowDirectives defaults = LuminPackDirectives.ShadowDirectives.defaults();
        return new LuminPackDirectives.ShadowDirectives(
                (int) parseFloatOr(properties.get("shadowMapResolution"), defaults.resolution()),
                parseBooleanOr(properties.get("shadow.culling"), defaults.culling()),
                parseFloatOr(properties.get("shadowDistance"), defaults.distance()),
                parseBooleanOr(properties.get("shadowTerrain"), defaults.terrain()),
                parseBooleanOr(properties.get("shadowEntities"), defaults.entities()),
                parseBooleanOr(properties.get("shadowPlayer"), defaults.player()),
                parseBooleanOr(properties.get("shadowBlockEntities"), defaults.blockEntities()),
                parseBooleanOr(properties.get("shadowTranslucent"), defaults.translucent()),
                parseFloatOr(properties.get("shadowIntervalSize"), defaults.intervalSize()));
    }

    /** 解析 {@code uniform.<type>.<name> = <expr>} / {@code variable.<type>.<name> = <expr>}。 */
    private List<LuminCustomUniform> parseCustomUniforms(List<Diagnostic> diagnostics) {
        String text = provider.read(SHADER_ROOT + "shaders.properties");
        if (text == null) {
            return List.of();
        }
        PropertiesParser.ParsedProperties properties = PropertiesParser.parse(text);
        List<LuminCustomUniform> uniforms = new ArrayList<>();
        for (String key : properties.keys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            boolean exposed;
            String rest;
            if (lower.startsWith("uniform.")) {
                exposed = true;
                rest = key.substring("uniform.".length());
            } else if (lower.startsWith("variable.")) {
                exposed = false;
                rest = key.substring("variable.".length());
            } else {
                continue;
            }
            int dot = rest.indexOf('.');
            if (dot <= 0) {
                diagnostics.add(Diagnostic.warning(SHADER_ROOT + "shaders.properties",
                        properties.lineOf(key), "malformed custom uniform key: " + key));
                continue;
            }
            String typeName = rest.substring(0, dot);
            String name = rest.substring(dot + 1);
            LuminCustomUniform.ValueType type = LuminCustomUniform.ValueType.parse(typeName);
            if (type == null) {
                diagnostics.add(Diagnostic.warning(SHADER_ROOT + "shaders.properties",
                        properties.lineOf(key), "unknown custom uniform type: " + typeName));
                continue;
            }
            String expression = properties.get(key);
            uniforms.add(new LuminCustomUniform(name, type, expression, exposed,
                    scanReferences(expression)));
        }
        return uniforms;
    }

    /** 初扫表达式中的标识符引用（供依赖拓扑排序；编译期再做严格解析）。 */
    static List<String> scanReferences(String expression) {
        Set<String> references = new LinkedHashSet<>();
        if (expression == null) {
            return List.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z_][A-Za-z0-9_]*").matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            references.add(token);
        }
        return List.copyOf(references);
    }

    /** 解析能力旗标列表（{@code iris.features.required|optional = FLAG1 FLAG2}）。 */
    private Set<LuminFeatureFlag> parseFeatureFlags(String key, List<Diagnostic> diagnostics) {
        String text = provider.read(SHADER_ROOT + "shaders.properties");
        if (text == null) {
            return Set.of();
        }
        PropertiesParser.ParsedProperties properties = PropertiesParser.parse(text);
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<LuminFeatureFlag> flags = new LinkedHashSet<>();
        for (String token : value.trim().split("[\\s,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                flags.add(LuminFeatureFlag.valueOf(token.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                diagnostics.add(Diagnostic.warning(SHADER_ROOT + "shaders.properties",
                        properties.lineOf(key), "unknown feature flag: " + token));
            }
        }
        return flags;
    }

    private static boolean parseBooleanOr(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> defaultValue;
        };
    }

    private static float parseFloatOr(String value, float defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /** 可变程序构建器（解析期内部使用）。 */
    private static final class MutableProgram {
        private final LuminProgramId id;
        private final Map<LuminShaderKind, String> sources = new EnumMap<>(LuminShaderKind.class);
        private final Map<LuminShaderKind, String> paths = new EnumMap<>(LuminShaderKind.class);
        private Set<LuminTargetId> drawTargets = Set.of(LuminTargetId.color(0));

        MutableProgram(LuminProgramId id) {
            this.id = id;
        }

        LuminProgramSource toImmutable() {
            return new LuminProgramSource(id, sources, paths, drawTargets, true);
        }
    }
}
