package net.minecraft.client.renderer;

/**
 * NeoForge适配层：模拟Fabric的ProjectionMatrixBuffer API
 */
public class ProjectionMatrixBuffer {

    private final String name;

    public ProjectionMatrixBuffer(String name) {
        this.name = name;
    }

    public org.joml.Matrix4f getBuffer(Projection projection) {
        // NeoForge使用不同的API
        return new org.joml.Matrix4f();
    }

    public void close() {
        // NeoForge使用不同的API
    }
}
