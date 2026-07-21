package io.github.openlumin.api;

/**
 * 顶点格式抽象接口
 * 定义顶点属性布局（位置、颜色、UV 等）
 */
public interface VertexFormatApi {

    /** 位置 (vec3) */
    VertexFormatHandle POSITION_3F();

    /** 位置 + 颜色 (vec3 + rgba) */
    VertexFormatHandle POSITION_COLOR();

    /** 位置 + UV (vec3 + vec2) */
    VertexFormatHandle POSITION_TEX();

    /** 位置 + 颜色 + UV (vec3 + rgba + vec2) */
    VertexFormatHandle POSITION_COLOR_TEX();

    /**
     * 创建自定义顶点格式
     * @param attributes 顶点属性列表
     * @return 顶点格式句柄
     */
    VertexFormatHandle custom(VertexAttribute... attributes);

    /**
     * 获取顶点格式的步长（单个顶点的字节数）
     * @param format 顶点格式句柄
     * @return 步长（字节）
     */
    int getStride(VertexFormatHandle format);

    /** 顶点属性定义 */
    record VertexAttribute(String name, AttributeType type, int count) {
        public enum AttributeType {
            FLOAT,
            BYTE,
            UNSIGNED_BYTE,
            SHORT,
            UNSIGNED_SHORT,
            INT,
            UNSIGNED_INT
        }
    }
}
