package io.github.openlumin;

import io.github.openlumin.platform.NeoForge1210Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import static io.github.openlumin.Constants.LOG_PREFIX;

/**
 * OpenLumin NeoForge 1.21.10 入口
 */
@Mod("openlumin")
public class OpenLuminNeoForge1210 {

    public OpenLuminNeoForge1210(IEventBus modEventBus) {
        PlatformRegistry.register(new NeoForge1210Platform());
        System.out.println(LOG_PREFIX + "Platform registered: NeoForge1210Platform");
        System.out.println(LOG_PREFIX + "neoforge-1.21.10 library initialized");
    }
}
