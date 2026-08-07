package io.github.openlumin;

import io.github.openlumin.platform.NeoForge2622Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = "openlumin", dist = Dist.CLIENT)
public final class OpenLuminNeoForge2622 {

    public OpenLuminNeoForge2622() {
        PlatformRegistry.register(new NeoForge2622Platform());
        Constants.LOGGER.info("neoforge-26.2 adapter initialized");
    }
}
