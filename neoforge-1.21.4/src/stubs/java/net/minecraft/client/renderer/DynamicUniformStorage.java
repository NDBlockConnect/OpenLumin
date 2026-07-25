package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * NeoForge 实现：使用 OpenGL UBO 的动态 Uniform 存储
 * 采用简化的环形缓冲区策略
 */
public class DynamicUniformStorage<T extends DynamicUniformStorage.DynamicUniform> {

    private final String label;
    private final int uniformSize;
    private final int capacity;
    private GpuBuffer buffer;
    private int writeOffset;
    private ByteBuffer stagingBuffer;

    public DynamicUniformStorage(String label, int uniformSize, int initialCapacity) {
        this.label = label;
        this.uniformSize = uniformSize;
        this.capacity = initialCapacity;
        this.buffer = new GpuBuffer((long) uniformSize * initialCapacity, GpuBuffer.USAGE_UNIFORM);
        this.writeOffset = 0;
        this.stagingBuffer = MemoryUtil.memAlloc(uniformSize);
    }

    public GpuBufferSlice write(T uniform) {
        // 重置 staging buffer
        stagingBuffer.clear();

        // 调用用户的 write 方法填充数据
        uniform.write(stagingBuffer);
        stagingBuffer.flip();

        // 写入到 GPU buffer
        int offset = writeOffset;
        buffer.write(offset, stagingBuffer);

        // 更新偏移（环形缓冲）
        writeOffset += uniformSize;
        if (writeOffset + uniformSize > uniformSize * capacity) {
            writeOffset = 0; // 回到起点
        }

        return new GpuBufferSlice(buffer, offset, uniformSize);
    }

    public GpuBufferSlice writeUniform(T uniform) {
        return write(uniform);
    }

    /**
     * Fabric API适配：写入变换矩阵数据（modelView + colorModulator + modelOffset + textureMatrix）
     */
    public GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix) {
        // NeoForge使用不同的API，返回占位slice
        return new GpuBufferSlice(buffer, writeOffset, uniformSize);
    }

    public void endFrame() {
        // 重置写入指针到缓冲区开始
        writeOffset = 0;
    }

    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        if (stagingBuffer != null) {
            MemoryUtil.memFree(stagingBuffer);
            stagingBuffer = null;
        }
    }

    public interface DynamicUniform {
        void write(java.nio.ByteBuffer buffer);
    }
}
