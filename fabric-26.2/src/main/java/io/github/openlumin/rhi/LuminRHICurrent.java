package io.github.openlumin.rhi;

/**
 * LuminRHI 静态访问器
 *
 * Alpha 2 范围内，RHI 实例由加载器入口类（Fabric/NeoForge）注册；
 * 业务层通过 LuminRHI.current() 获取当前后端实现。
 *
 * Alpha 5 目标：多后端并存时，按 RHIInfo.backendName 选择（例如 Metal 优先于 Vulkan，
 * 或用户通过配置覆盖）。
 */
public final class LuminRHICurrent {
    private static volatile LuminRHI instance;

    private LuminRHICurrent() {}

    public static void register(LuminRHI rhi) {
        if (instance != null && instance != rhi) {
            throw new IllegalStateException("LuminRHI already registered: " + instance.info().backendName());
        }
        instance = rhi;
    }

    public static LuminRHI get() {
        LuminRHI r = instance;
        if (r == null) {
            throw new IllegalStateException("LuminRHI not registered");
        }
        return r;
    }

    public static boolean isRegistered() {
        return instance != null;
    }

    public static void reset() {
        instance = null;
    }
}
