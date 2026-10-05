package io.github.openlumin.shaderpack.frame;

/**
 * std140 uniform 成员声明（名称 + 类型）。
 */
public record LuminUniformField(String name, Type type) {

    /** 支持的基础类型（std140 对齐/尺寸，单位字节）。 */
    public enum Type {
        FLOAT(4, 4, "float"),
        INT(4, 4, "int"),
        VEC2(8, 8, "vec2"),
        VEC3(16, 12, "vec3"),
        VEC4(16, 16, "vec4");

        private final int alignment;
        private final int size;
        private final String glsl;

        Type(int alignment, int size, String glsl) {
            this.alignment = alignment;
            this.size = size;
            this.glsl = glsl;
        }

        public int alignment() {
            return alignment;
        }

        public int size() {
            return size;
        }

        public String glsl() {
            return glsl;
        }
    }

    public LuminUniformField {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("field name must not be empty");
        }
        if (type == null) {
            throw new NullPointerException("type");
        }
    }
}
