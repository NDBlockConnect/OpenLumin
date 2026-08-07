package io.github.openlumin.shaders;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OpenGL Shader Program 包装类
 * 用于加载、编译和管理 GLSL shader
 */
public class ShaderProgram implements AutoCloseable {

    private final int programId;
    private boolean closed = false;

    private ShaderProgram(int programId) {
        this.programId = programId;
    }

    /**
     * 从资源加载并编译 shader program
     *
     * @param vertexShaderPath   顶点着色器路径（如 "openlumin:shaders/filter.vsh"）
     * @param fragmentShaderPath 片段着色器路径（如 "openlumin:shaders/filter.fsh"）
     * @param resourceManager    资源管理器
     * @return 编译后的 ShaderProgram
     * @throws IOException 如果加载或编译失败
     */
    public static ShaderProgram load(ResourceLocation vertexShaderPath, ResourceLocation fragmentShaderPath, ResourceManager resourceManager) throws IOException {
        // 加载并预处理 shader 源码（处理 #moj_import 指令）
        String vertexSource = preprocessSource(loadShaderSource(vertexShaderPath, resourceManager), resourceManager);
        String fragmentSource = preprocessSource(loadShaderSource(fragmentShaderPath, resourceManager), resourceManager);

        // 编译 shader
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, vertexShaderPath.toString());
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, fragmentShaderPath.toString());

        // 链接 program
        int programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShader);
        GL20.glAttachShader(programId, fragmentShader);
        GL20.glLinkProgram(programId);

        // 检查链接状态
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(programId);
            GL20.glDeleteProgram(programId);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new IOException("Failed to link shader program: " + log);
        }

        // 删除 shader 对象（已链接到 program）
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return new ShaderProgram(programId);
    }

    /**
     * 预处理 shader 源码，展开所有 #moj_import 指令
     * 格式：#moj_import <namespace:filename>
     * 映射到：assets/{namespace}/shaders/include/{filename}
     */
    private static String preprocessSource(String source, ResourceManager resourceManager) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#moj_import")) {
                int start = trimmed.indexOf('<');
                int end = trimmed.indexOf('>');
                if (start >= 0 && end > start) {
                    String ref = trimmed.substring(start + 1, end); // e.g. "minecraft:projection.glsl"
                    int colon = ref.indexOf(':');
                    if (colon > 0) {
                        String namespace = ref.substring(0, colon);
                        String filename  = ref.substring(colon + 1);
                        ResourceLocation includeLoc = ResourceLocation.fromNamespaceAndPath(
                                namespace, "shaders/include/" + filename);
                        // 递归处理嵌套 import
                        String includeSource = preprocessSource(
                                loadShaderSource(includeLoc, resourceManager), resourceManager);
                        result.append(includeSource);
                        if (!includeSource.endsWith("\n")) result.append('\n');
                        continue;
                    }
                }
                // 格式不对时原样保留，让 GLSL 编译器报错
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }

    /**
     * 从资源加载 shader 源码
     */
    private static String loadShaderSource(ResourceLocation location, ResourceManager resourceManager) throws IOException {
        var resourceOpt = resourceManager.getResource(location);
        if (resourceOpt.isEmpty()) {
            throw new IOException("Resource not found: " + location);
        }
        try (var inputStream = resourceOpt.get().open()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 编译单个 shader
     */
    private static int compileShader(int type, String source, String name) throws IOException {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shaderId);
            GL20.glDeleteShader(shaderId);
            throw new IOException("Failed to compile shader '" + name + "': " + log);
        }

        return shaderId;
    }

    /**
     * 使用此 shader program
     */
    public void use() {
        if (closed) {
            throw new IllegalStateException("ShaderProgram has been closed");
        }
        GL20.glUseProgram(programId);
    }

    /**
     * 获取 uniform location
     */
    public int getUniformLocation(String name) {
        return GL20.glGetUniformLocation(programId, name);
    }

    /**
     * 获取 uniform block index
     */
    public int getUniformBlockIndex(String name) {
        return GL31.glGetUniformBlockIndex(programId, name);
    }

    /**
     * 绑定 uniform block 到 binding point
     */
    public void bindUniformBlock(String blockName, int bindingPoint) {
        int blockIndex = getUniformBlockIndex(blockName);
        if (blockIndex >= 0) {
            GL31.glUniformBlockBinding(programId, blockIndex, bindingPoint);
        }
    }

    /**
     * 设置纹理采样器的 texture unit
     */
    public void setSamplerUnit(String samplerName, int textureUnit) {
        int location = getUniformLocation(samplerName);
        if (location >= 0) {
            use();
            GL20.glUniform1i(location, textureUnit);
        }
    }

    /**
     * 获取 program ID
     */
    public int getProgramId() {
        return programId;
    }

    @Override
    public void close() {
        if (!closed) {
            GL20.glDeleteProgram(programId);
            closed = true;
        }
    }
}
