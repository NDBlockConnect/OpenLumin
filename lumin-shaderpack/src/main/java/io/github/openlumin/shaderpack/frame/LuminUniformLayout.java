package io.github.openlumin.shaderpack.frame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * std140 布局计算（纯 CPU）：由有序成员列表推导偏移与结构尺寸。
 *
 * <p>规则（OpenGL std140，无数组/结构体嵌套）：成员偏移 = 上一成员结束位置向上取整到
 * 本成员对齐；结构尺寸 = 末尾向上取整到 16。GLSL 编译器对同一声明的推导必须与本表一致
 * （由 {@code LuminShaderpackUniformBlock} 的 GLSL 文本与偏移断言共同锁定）。</p>
 */
public final class LuminUniformLayout {

    private final List<LuminUniformField> fields;
    private final Map<String, Integer> offsets;
    private final int size;

    private LuminUniformLayout(List<LuminUniformField> fields,
                               Map<String, Integer> offsets, int size) {
        this.fields = List.copyOf(fields);
        this.offsets = Map.copyOf(offsets);
        this.size = size;
    }

    public static LuminUniformLayout of(List<LuminUniformField> fields) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        int offset = 0;
        for (LuminUniformField field : fields) {
            int alignment = field.type().alignment();
            offset = roundUp(offset, alignment);
            offsets.put(field.name(), offset);
            offset += field.type().size();
        }
        return new LuminUniformLayout(fields, offsets, roundUp(offset, 16));
    }

    public List<LuminUniformField> fields() {
        return fields;
    }

    /** 成员偏移（字节）。 */
    public int offset(String name) {
        Integer value = offsets.get(name);
        if (value == null) {
            throw new IllegalArgumentException("no field named " + name);
        }
        return value;
    }

    /** 结构总尺寸（字节，16 对齐）。 */
    public int size() {
        return size;
    }

    /** GLSL 块声明文本（{@code layout(std140) uniform <blockName> { ... };}）。 */
    public String glslDeclaration(String blockName) {
        return glslDeclaration(blockName, null);
    }

    /**
     * GLSL 块声明文本；{@code instanceName} 非空时生成实例名
     * （成员经 {@code instanceName.member} 访问——无实例名时成员只能裸名访问）。
     */
    public String glslDeclaration(String blockName, String instanceName) {
        StringBuilder out = new StringBuilder();
        out.append("layout(std140) uniform ").append(blockName).append(" {\n");
        for (LuminUniformField field : fields) {
            out.append("    ").append(field.type().glsl())
                    .append(' ').append(field.name()).append(";\n");
        }
        out.append('}');
        if (instanceName != null && !instanceName.isEmpty()) {
            out.append(' ').append(instanceName);
        }
        out.append(";\n");
        return out.toString();
    }

    private static int roundUp(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }
}
