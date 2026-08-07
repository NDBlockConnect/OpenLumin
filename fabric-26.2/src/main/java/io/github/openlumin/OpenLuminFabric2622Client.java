package io.github.openlumin;

import io.github.openlumin.platform.Fabric2622Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.fabricmc.api.ClientModInitializer;

public final class OpenLuminFabric2622Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PlatformRegistry.register(new Fabric2622Platform());
        Constants.LOGGER.info("fabric-26.2 adapter initialized");
    }
}
