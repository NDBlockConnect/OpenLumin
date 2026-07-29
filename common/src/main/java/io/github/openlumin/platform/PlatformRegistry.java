package io.github.openlumin.platform;

/**
 * 平台注册表 - 单例模式
 *
 * 在模组初始化时，各平台实现注册自己的 LuminPlatform 实例。
 * 业务代码通过 PlatformRegistry.get() 获取当前平台实现。
 */
public final class PlatformRegistry {

    private static LuminPlatform instance;

    private PlatformRegistry() {}

    /**
     * 注册平台实现（模组初始化时调用一次）
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
            throw new IllegalStateException("Platform not registered. Call PlatformRegistry.register() in mod initializer.");
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
     * 清理（仅用于测试）
     */
    static void reset() {
        instance = null;
    }
}
