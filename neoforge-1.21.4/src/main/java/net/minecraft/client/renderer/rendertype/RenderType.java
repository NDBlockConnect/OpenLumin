package net.minecraft.client.renderer.rendertype;

/**
 * NeoForge适配层：模拟Fabric的RenderType API
 */
public class RenderType {

    public static RenderType create(String name, RenderSetup setup) {
        // NeoForge使用不同的API
        return new RenderType();
    }
}
