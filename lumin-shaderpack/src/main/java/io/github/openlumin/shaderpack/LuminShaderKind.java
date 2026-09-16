package io.github.openlumin.shaderpack;

/**
 * GLSL 着色器阶段（对应源文件扩展名）。
 *
 * <p>与 OptiFine/Iris 的扩展名约定一致：{@code .vsh/.fsh/.gsh/.tcs/.tes/.csh}。
 * 顶点与片段阶段是所有程序的最低要求；几何/曲面细分/计算阶段需对应能力旗标。</p>
 */
public enum LuminShaderKind {
    VERTEX("vsh"),
    FRAGMENT("fsh"),
    GEOMETRY("gsh"),
    TESS_CONTROL("tcs"),
    TESS_EVALUATION("tes"),
    COMPUTE("csh");

    private final String extension;

    LuminShaderKind(String extension) {
        this.extension = extension;
    }

    /** 源文件扩展名（不含点）。 */
    public String extension() {
        return extension;
    }

    /** 按扩展名查找；未知返回 null。 */
    public static LuminShaderKind fromExtension(String extension) {
        for (LuminShaderKind kind : values()) {
            if (kind.extension.equalsIgnoreCase(extension)) {
                return kind;
            }
        }
        return null;
    }

    /** 是否为图形阶段（非计算）。 */
    public boolean isGraphics() {
        return this != COMPUTE;
    }
}
