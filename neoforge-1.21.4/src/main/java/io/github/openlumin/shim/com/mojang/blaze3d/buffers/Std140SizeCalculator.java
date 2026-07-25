package io.github.openlumin.shim.com.mojang.blaze3d.buffers;

/**
 * NeoForge适配层：模拟Fabric的Std140SizeCalculator API
 */
public class Std140SizeCalculator {

    private int size = 0;

    public Std140SizeCalculator putFloat() {
        size += 4;
        return this;
    }

    public Std140SizeCalculator putInt() {
        size += 4;
        return this;
    }

    public Std140SizeCalculator putVec2() {
        size += 8;
        return this;
    }

    public Std140SizeCalculator putVec3() {
        size += 16; // 包含padding
        return this;
    }

    public Std140SizeCalculator putVec4() {
        size += 16;
        return this;
    }

    public Std140SizeCalculator putMat4() {
        size += 64;
        return this;
    }

    public int get() {
        return size;
    }

    // 保留旧方法名以兼容性
    public Std140SizeCalculator addFloat() {
        return putFloat();
    }

    public Std140SizeCalculator addInt() {
        return putInt();
    }

    public Std140SizeCalculator addVec2() {
        return putVec2();
    }

    public Std140SizeCalculator addVec3() {
        return putVec3();
    }

    public Std140SizeCalculator addVec4() {
        return putVec4();
    }

    public Std140SizeCalculator addMat4() {
        return putMat4();
    }

    public int calculate() {
        return get();
    }
}

