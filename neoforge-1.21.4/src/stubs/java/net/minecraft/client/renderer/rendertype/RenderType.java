package net.minecraft.client.renderer.rendertype;

/**
 * NeoForge适配层：模拟Fabric的RenderType API
 */
public class RenderType {

    /** 768KB — matches vanilla Minecraft's SMALL_BUFFER_SIZE */
    public static final int SMALL_BUFFER_SIZE = 786432;

    public static RenderType create(String name, RenderSetup setup) {
        // NeoForge使用不同的API
        return new RenderType();
    }
}
