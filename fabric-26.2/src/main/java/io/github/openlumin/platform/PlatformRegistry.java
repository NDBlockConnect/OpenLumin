package io.github.openlumin.platform;

/**
 * 平台注册中心
 *
 * 管理当前运行时的平台实现（Fabric/NeoForge）。
 * 每个加载器的入口类在初始化时注册自己的平台实现。
 */
public class PlatformRegistry {

    private static LuminPlatform instance;

    /**
     * 注册平台实现（由加载器入口类调用）
     */
    public static void register(LuminPlatform platform) {
        if (instance != null) {
            throw new IllegalStateException("Platform already registered: " + instance.getClass().getName());
        }
        instance = platform;
        System.out.println("[OpenLumin] Platform registered: " + platform.getClass().getSimpleName());
    }

    /**
     * 获取当前平台实现
     */
    public static LuminPlatform get() {
        if (instance == null) {
            throw new IllegalStateException("No platform registered. Did you forget to call PlatformRegistry.register()?");
        }
        return instance;
    }

    /**
     * 检查平台是否已注册
     */
    public static boolean isRegistered() {
        return instance != null;
    }

    /**
     * 重置注册（仅用于测试）
     */
    public static void reset() {
        instance = null;
    }
}
