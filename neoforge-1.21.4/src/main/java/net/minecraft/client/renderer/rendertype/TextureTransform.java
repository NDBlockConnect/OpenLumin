package net.minecraft.client.renderer.rendertype;

import org.joml.Matrix4f;

/**
 * NeoForge适配层：模拟Fabric的TextureTransform API
 */
public class TextureTransform {

    public static final TextureTransform IDENTITY = new TextureTransform();
    public static final TextureTransform DEFAULT_TEXTURING = new TextureTransform();

    private float scaleU = 1.0f;
    private float scaleV = 1.0f;
    private float offsetU = 0.0f;
    private float offsetV = 0.0f;
    private Matrix4f matrix = new Matrix4f();

    public TextureTransform() {
    }

    public TextureTransform(float scaleU, float scaleV, float offsetU, float offsetV) {
        this.scaleU = scaleU;
        this.scaleV = scaleV;
        this.offsetU = offsetU;
        this.offsetV = offsetV;
    }

    public float scaleU() {
        return scaleU;
    }

    public float scaleV() {
        return scaleV;
    }

    public float offsetU() {
        return offsetU;
    }

    public float offsetV() {
        return offsetV;
    }

    public Matrix4f getMatrix() {
        return matrix;
    }
}
