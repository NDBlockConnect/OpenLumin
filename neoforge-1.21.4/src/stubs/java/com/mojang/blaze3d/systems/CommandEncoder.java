package com.mojang.blaze3d.systems;

/**
 * NeoForge适配层：模拟Fabric的CommandEncoder API
 */
public class CommandEncoder {

    public RenderPass beginRenderPass() {
        return new RenderPass();
    }

    public void endRenderPass() {
        // NeoForge使用不同的API
    }

    public void copyBufferToBuffer(Object src, long srcOffset, Object dst, long dstOffset, long size) {
        // NeoForge使用不同的API
    }

    public void copyTextureToTexture(Object src, Object dst) {
        // NeoForge使用不同的API
    }
}
