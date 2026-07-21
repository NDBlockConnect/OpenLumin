package io.github.openlumin.api;

import java.nio.ByteBuffer;

/**
 * GPU 缓冲管理抽象接口
 * 封装不同版本的 GPU 缓冲创建、映射、绑定操作
 */
public interface GpuBufferApi {

    /**
     * 创建 GPU 缓冲
     * @param sizeBytes 缓冲大小（字节）
     * @param usage 使用方式
     * @return 缓冲句柄
     */
    GpuBufferHandle createBuffer(long sizeBytes, BufferUsage usage);

    /**
     * 映射缓冲到 CPU 可写内存
     * @param handle 缓冲句柄
     * @param offset 偏移量（字节）
     * @param length 长度（字节）
     * @return CPU 可访问的字节缓冲
     */
    ByteBuffer mapBuffer(GpuBufferHandle handle, long offset, long length);

    /**
     * 解除映射（提交到 GPU）
     * @param handle 缓冲句柄
     */
    void unmapBuffer(GpuBufferHandle handle);

    /**
     * 绑定缓冲到渲染管线
     * @param handle 缓冲句柄
     * @param bindingPoint 绑定点索引
     */
    void bindBuffer(GpuBufferHandle handle, int bindingPoint);

    /**
     * 释放缓冲资源
     * @param handle 缓冲句柄
     */
    void deleteBuffer(GpuBufferHandle handle);

    /** 缓冲使用方式 */
    enum BufferUsage {
        /** 静态数据（写一次，读多次） */
        STATIC_DRAW,
        /** 动态数据（频繁更新） */
        DYNAMIC_DRAW,
        /** 流式数据（每帧更新） */
        STREAM_DRAW
    }
}
