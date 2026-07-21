package io.github.openlumin.api;

/** GPU 缓冲句柄 */
public record GpuBufferHandle(Object nativeHandle) implements Handle {}
