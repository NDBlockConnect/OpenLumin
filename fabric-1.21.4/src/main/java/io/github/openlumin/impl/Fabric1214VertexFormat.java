package io.github.openlumin.impl;

import io.github.openlumin.api.VertexFormatApi;
import io.github.openlumin.api.VertexFormatHandle;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Fabric 1.21.4 顶点格式实现。
 * 复用 Mojang 的 DefaultVertexFormat 常量。
 */
public class Fabric1214VertexFormat implements VertexFormatApi {

    @Override
    public VertexFormatHandle POSITION_3F() {
        return new VertexFormatHandle(DefaultVertexFormat.POSITION);
    }

    @Override
    public VertexFormatHandle POSITION_COLOR() {
        return new VertexFormatHandle(DefaultVertexFormat.POSITION_COLOR);
    }

    @Override
    public VertexFormatHandle POSITION_TEX() {
        return new VertexFormatHandle(DefaultVertexFormat.POSITION_TEX);
    }

    @Override
    public VertexFormatHandle POSITION_COLOR_TEX() {
        return new VertexFormatHandle(DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    @Override
    public VertexFormatHandle custom(VertexAttribute... attributes) {
        // 自定义顶点格式暂未支持，返回最通用的格式作为兜底
        return new VertexFormatHandle(DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    @Override
    public int getStride(VertexFormatHandle format) {
        VertexFormat vf = (VertexFormat) format.nativeHandle();
        return vf.getVertexSize();
    }
}
