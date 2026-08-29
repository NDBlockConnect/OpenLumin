package io.github.openlumin.rhi;

/** 缓冲视图（offset/length 子范围） */
public record LuminBufferView(LuminBuffer buffer, long offset, long length) {}
