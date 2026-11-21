package io.github.Ling.create_jei_gauge_bridge.client;

import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives the deferred screen opening; client-only. */
@EventBusSubscriber(modid = Create_jei_gauge_bridge.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientScreens.tick();
        GoggleGaugeHighlighter.tick();
    }
}
