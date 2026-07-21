package io.github.openlumin.api;

/**
 * OpenLumin API 注册中心
 * 由各版本模块在初始化时注册平台特定实现
 */
public final class LuminApi {
    private static RenderContext renderContext;
    private static GpuBufferApi gpuBuffer;
    private static VertexFormatApi vertexFormat;
    private static RenderPipelineApi renderPipeline;
    private static TextureApi texture;

    private static boolean initialized = false;

    private LuminApi() {}

    /**
     * 初始化 OpenLumin API（由各版本模块的 Platform.java 调用）
     * @param ctx 渲染上下文实现
     * @param buf GPU 缓冲实现
     * @param vfmt 顶点格式实现
     * @param pipe 渲染管线实现
     * @param tex 纹理实现
     */
    public static void initialize(
        RenderContext ctx,
        GpuBufferApi buf,
        VertexFormatApi vfmt,
        RenderPipelineApi pipe,
        TextureApi tex
    ) {
        if (initialized) {
            throw new IllegalStateException("LuminApi already initialized");
        }

        renderContext = ctx;
        gpuBuffer = buf;
        vertexFormat = vfmt;
        renderPipeline = pipe;
        texture = tex;
        initialized = true;
    }

    /** 获取渲染上下文 */
    public static RenderContext context() {
        ensureInitialized();
        return renderContext;
    }

    /** 获取 GPU 缓冲 API */
    public static GpuBufferApi buffer() {
        ensureInitialized();
        return gpuBuffer;
    }

    /** 获取顶点格式 API */
    public static VertexFormatApi format() {
        ensureInitialized();
        return vertexFormat;
    }

    /** 获取渲染管线 API */
    public static RenderPipelineApi pipeline() {
        ensureInitialized();
        return renderPipeline;
    }

    /** 获取纹理 API */
    public static TextureApi texture() {
        ensureInitialized();
        return texture;
    }

    /** 检查是否已初始化 */
    public static boolean isInitialized() {
        return initialized;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "LuminApi not initialized. " +
                "Platform module must call LuminApi.initialize() before use."
            );
        }
    }
}
