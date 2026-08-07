package io.github.openlumin;

import io.github.openlumin.platform.Fabric2612Platform;
import io.github.openlumin.platform.PlatformRegistry;
import net.fabricmc.api.ClientModInitializer;

public final class OpenLuminFabric2612Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PlatformRegistry.register(new Fabric2612Platform());
        System.out.println("[OpenLumin] fabric-26.1.2 adapter initialized");
    }
}
