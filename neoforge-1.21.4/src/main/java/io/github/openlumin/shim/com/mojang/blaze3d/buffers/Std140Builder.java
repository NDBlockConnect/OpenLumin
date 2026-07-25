package io.github.openlumin.shim.com.mojang.blaze3d.buffers;

import java.nio.ByteBuffer;

/**
 * NeoForge适配层：模拟Fabric的Std140Builder API
 */
public class Std140Builder {

    private final ByteBuffer buffer;
    private int position = 0;

    public Std140Builder(int capacity) {
        this.buffer = ByteBuffer.allocate(capacity);
    }

    private Std140Builder(ByteBuffer buffer) {
        this.buffer = buffer;
        this.position = buffer.position();
    }

    public static Std140Builder intoBuffer(ByteBuffer buffer) {
        return new Std140Builder(buffer);
    }

    public Std140Builder putFloat(float value) {
        buffer.putFloat(position, value);
        position += 4;
        return this;
    }

    public Std140Builder putInt(int value) {
        buffer.putInt(position, value);
        position += 4;
        return this;
    }

    public Std140Builder putVec2(float x, float y) {
        putFloat(x);
        putFloat(y);
        return this;
    }

    public Std140Builder putVec3(float x, float y, float z) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        position += 4; // padding
        return this;
    }

    public Std140Builder putVec4(float x, float y, float z, float w) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        putFloat(w);
        return this;
    }

    public ByteBuffer build() {
        buffer.flip();
        return buffer;
    }
}
