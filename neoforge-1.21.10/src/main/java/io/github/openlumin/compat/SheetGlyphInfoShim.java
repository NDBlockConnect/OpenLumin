package io.github.openlumin.compat;

/**
 * 1.21.10 兼容性桩：SheetGlyphInfo 在 1.21.10 中已被 BakedSheetGlyph 替代。
 * 此接口仅用于编译通过，避免在 com.mojang.* 包中创建类导致模块冲突。
 */
public interface SheetGlyphInfoShim {}
