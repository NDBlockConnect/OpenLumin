package io.github.openlumin;

import io.github.openlumin.impl.NeoForge1214Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("openlumin")
public class OpenLuminMod {
    public OpenLuminMod(IEventBus modEventBus) {
        Constants.LOGGER.info("OpenLumin initializing on NeoForge 1.21.4");
        NeoForge1214Platform.initialize();
    }
}
