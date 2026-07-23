package net.minecraft.client.renderer;

/**
 * NeoForge适配层：模拟Fabric的Projection API
 */
public class Projection {

    public Projection setupOrtho(float near, float far, float width, float height, boolean flipY) {
        // NeoForge使用不同的API
        return this;
    }
}
