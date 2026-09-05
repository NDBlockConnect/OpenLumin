package io.github.openlumin.chunk.cull;

import java.util.Objects;

/**
 * Section 坐标（1 单位 = 1 个 section，即 16 方块）。
 * <p>打包：每轴 21 位有符号（±1,048,575 section ≈ ±16.7M 方块），
 * 越界抛 {@link IllegalArgumentException}——遍历器的 visited 集以打包 long 为键。</p>
 */
public record LuminSectionPos(int x, int y, int z) {

    public static final int MAX_COORDINATE = (1 << 20) - 1;
    public static final int MIN_COORDINATE = -MAX_COORDINATE;
    private static final int FIELD_BITS = 21;

    public LuminSectionPos {
        if (x < MIN_COORDINATE || x > MAX_COORDINATE
                || y < MIN_COORDINATE || y > MAX_COORDINATE
                || z < MIN_COORDINATE || z > MAX_COORDINATE) {
            throw new IllegalArgumentException("section coordinate out of range: " + x + ", " + y + ", " + z);
        }
    }

    public LuminSectionPos offset(LuminGraphDirection direction) {
        return new LuminSectionPos(x + direction.dx(), y + direction.dy(), z + direction.dz());
    }

    public long pack() {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    public static LuminSectionPos unpack(long packed) {
        return new LuminSectionPos(
                decode(packed, 42),
                decode(packed, 21),
                decode(packed, 0));
    }

    private static int decode(long packed, int shift) {
        int raw = (int) ((packed >>> shift) & 0x1FFFFF);
        return (raw << (32 - FIELD_BITS)) >> (32 - FIELD_BITS);
    }

    public int distanceSquaredTo(LuminSectionPos other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LuminSectionPos other
                && x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "SectionPos{" + x + ", " + y + ", " + z + "}";
    }
}
