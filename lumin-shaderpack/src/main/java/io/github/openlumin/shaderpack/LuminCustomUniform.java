package io.github.openlumin.shaderpack;

import java.util.List;
import java.util.Objects;

/**
 * 自定义 uniform / 变量声明（来自 {@code shaders.properties} 的
 * {@code uniform.<type>.<name>} 与 {@code variable.<type>.<name>}）。
 *
 * <p>语义：{@code variable.*} 定义表达式值；{@code uniform.*} 在此基础上**额外暴露为着色器
 * uniform**。表达式求值与依赖拓扑排序在编译期完成（解析层只保留原文与依赖声明的初步信息）。</p>
 *
 * @param name        变量名（着色器中的 uniform 名，{@code uniform.*} 时）
 * @param type        值类型
 * @param expression  表达式原文（未经求值）
 * @param exposed     是否作为 uniform 暴露（{@code uniform.*} = true；{@code variable.*} = false）
 * @param references  表达式中引用到的其它变量名（初步扫描结果，用于依赖拓扑排序）
 */
public record LuminCustomUniform(
        String name,
        ValueType type,
        String expression,
        boolean exposed,
        List<String> references) {

    public enum ValueType {
        BOOL("bool"),
        FLOAT("float"),
        INT("int"),
        VEC2("vec2"),
        VEC3("vec3"),
        VEC4("vec4");

        private final String identifier;

        ValueType(String identifier) {
            this.identifier = identifier;
        }

        public String identifier() {
            return identifier;
        }

        public static ValueType parse(String identifier) {
            if (identifier == null) {
                return null;
            }
            for (ValueType type : values()) {
                if (type.identifier.equalsIgnoreCase(identifier.trim())) {
                    return type;
                }
            }
            return null;
        }
    }

    public LuminCustomUniform {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("expression must not be empty");
        }
        references = List.copyOf(references);
    }

    @Override
    public String toString() {
        return (exposed ? "uniform." : "variable.") + type.identifier() + "." + name + " = " + expression;
    }
}
