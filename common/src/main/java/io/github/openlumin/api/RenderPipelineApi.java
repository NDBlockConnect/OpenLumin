package io.github.openlumin.api;

/**
 * 渲染管线抽象接口
 * 封装着色器程序、混合模式、深度测试等渲染状态
 */
public interface RenderPipelineApi {

    /**
     * 创建渲染管线
     * @param descriptor 管线描述符
     * @return 管线句柄
     */
    RenderPipelineHandle createPipeline(PipelineDescriptor descriptor);

    /**
     * 激活渲染管线
     * @param handle 管线句柄
     */
    void usePipeline(RenderPipelineHandle handle);

    /**
     * 设置 Uniform 变量
     * @param name 变量名
     * @param value 变量值（支持 int, float, vec2, vec3, vec4, mat4 等）
     */
    void setUniform(String name, Object value);

    /**
     * 执行绘制命令
     * @param command 绘制命令
     */
    void draw(DrawCommand command);

    /**
     * 删除渲染管线
     * @param handle 管线句柄
     */
    void deletePipeline(RenderPipelineHandle handle);

    /** 渲染管线描述符 */
    class PipelineDescriptor {
        public ShaderHandle vertexShader;
        public ShaderHandle fragmentShader;
        public VertexFormatHandle vertexFormat;
        public BlendMode blendMode = BlendMode.ALPHA;
        public DepthTest depthTest = DepthTest.DISABLED;
        public CullMode cullMode = CullMode.NONE;
    }

    /** 混合模式 */
    enum BlendMode {
        /** 无混合 */
        NONE,
        /** 标准 alpha 混合 */
        ALPHA,
        /** 相加混合（光效） */
        ADDITIVE,
        /** 相乘混合（阴影） */
        MULTIPLY
    }

    /** 深度测试 */
    enum DepthTest {
        DISABLED,
        LESS,
        LESS_OR_EQUAL,
        EQUAL,
        GREATER,
        GREATER_OR_EQUAL
    }

    /** 面剔除 */
    enum CullMode {
        NONE,
        FRONT,
        BACK,
        FRONT_AND_BACK
    }

    /** 绘制命令 */
    record DrawCommand(
        PrimitiveType primitiveType,
        int vertexCount,
        int instanceCount,
        int firstVertex,
        int firstInstance
    ) {
        public DrawCommand(PrimitiveType primitiveType, int vertexCount) {
            this(primitiveType, vertexCount, 1, 0, 0);
        }
    }

    /** 图元类型 */
    enum PrimitiveType {
        POINTS,
        LINES,
        LINE_STRIP,
        TRIANGLES,
        TRIANGLE_STRIP,
        TRIANGLE_FAN
    }
}
