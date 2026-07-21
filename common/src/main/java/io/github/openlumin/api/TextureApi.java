package io.github.openlumin.api;

import java.nio.ByteBuffer;

/**
 * 纹理管理抽象接口
 * 封装纹理的创建、上传、绑定、采样等操作
 */
public interface TextureApi {

    /**
     * 创建纹理
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @param format 纹理格式
     * @return 纹理句柄
     */
    TextureHandle createTexture(int width, int height, TextureFormat format);

    /**
     * 上传像素数据到纹理
     * @param handle 纹理句柄
     * @param pixels 像素数据（格式需与创建时指定的格式匹配）
     */
    void uploadTexture(TextureHandle handle, ByteBuffer pixels);

    /**
     * 绑定纹理到纹理单元
     * @param handle 纹理句柄
     * @param unit 纹理单元索引（0-31）
     */
    void bindTexture(TextureHandle handle, int unit);

    /**
     * 设置纹理采样器
     * @param handle 纹理句柄
     * @param sampler 采样器描述符
     */
    void setSampler(TextureHandle handle, SamplerDescriptor sampler);

    /**
     * 删除纹理
     * @param handle 纹理句柄
     */
    void deleteTexture(TextureHandle handle);

    /** 纹理格式 */
    enum TextureFormat {
        /** 单通道 8 位 */
        R8,
        /** 双通道 8 位 */
        RG8,
        /** RGB 8 位 */
        RGB8,
        /** RGBA 8 位 */
        RGBA8,
        /** 单通道 16 位浮点 */
        R16F,
        /** RGBA 16 位浮点 */
        RGBA16F
    }

    /** 采样器描述符 */
    class SamplerDescriptor {
        /** 缩小过滤 */
        public FilterMode minFilter = FilterMode.LINEAR;
        /** 放大过滤 */
        public FilterMode magFilter = FilterMode.LINEAR;
        /** S 轴（U）环绕模式 */
        public WrapMode wrapS = WrapMode.CLAMP;
        /** T 轴（V）环绕模式 */
        public WrapMode wrapT = WrapMode.CLAMP;

        public SamplerDescriptor() {}

        public SamplerDescriptor(FilterMode minFilter, FilterMode magFilter, WrapMode wrapS, WrapMode wrapT) {
            this.minFilter = minFilter;
            this.magFilter = magFilter;
            this.wrapS = wrapS;
            this.wrapT = wrapT;
        }
    }

    /** 过滤模式 */
    enum FilterMode {
        /** 最近邻 */
        NEAREST,
        /** 线性插值 */
        LINEAR,
        /** Mipmap */
        MIPMAP
    }

    /** 环绕模式 */
    enum WrapMode {
        /** 夹取到边缘 */
        CLAMP,
        /** 重复 */
        REPEAT,
        /** 镜像重复 */
        MIRROR
    }
}
