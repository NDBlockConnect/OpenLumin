package io.github.openlumin;

import io.github.openlumin.platform.NeoForge2612Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = "openlumin", dist = Dist.CLIENT)
public final class OpenLuminNeoForge2612 {

    public OpenLuminNeoForge2612() {
        PlatformRegistry.register(new NeoForge2612Platform());
        Constants.LOGGER.info("neoforge-26.1.2 adapter initialized");
    }
}
