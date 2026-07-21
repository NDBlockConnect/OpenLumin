package net.minecraft.client.input;

/**
 * NeoForge适配层：模拟Fabric的MouseButtonEvent API
 */
public record MouseButtonEvent(double x, double y, Object buttonInfo) {
    // NeoForge使用不同的API
}
