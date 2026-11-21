package io.github.Ling.create_jei_gauge_bridge.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the mod's payloads. Handlers run on the server and are always enqueued on the main
 * thread by NeoForge.
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(ImportRecipePayload.TYPE, ImportRecipePayload.STREAM_CODEC,
                        ImportRecipePayload::handle)
                .playToClient(OpenPanelScreenPayload.TYPE, OpenPanelScreenPayload.STREAM_CODEC,
                        OpenPanelScreenPayload::handle);
    }
}
