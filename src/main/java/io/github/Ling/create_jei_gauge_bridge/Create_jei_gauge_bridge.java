package io.github.Ling.create_jei_gauge_bridge;

import com.mojang.logging.LogUtils;
import io.github.Ling.create_jei_gauge_bridge.config.ClientConfig;
import io.github.Ling.create_jei_gauge_bridge.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point for Create JEI Gauge Bridge.
 *
 * <p>This mod is a pure compatibility layer: it registers no blocks, items or creative tabs of its
 * own. Its whole job is to expose Create's factory gauges to JEI, so any content it adds later
 * belongs in dedicated {@code compat}/{@code jei} packages rather than here.</p>
 */
@Mod(Create_jei_gauge_bridge.MODID)
public final class Create_jei_gauge_bridge {

    /**
     * Mod id. Must stay in sync with {@code mod_id} in gradle.properties and the {@code modId}
     * entry in {@code META-INF/neoforge.mods.toml}.
     */
    public static final String MODID = "create_jei_gauge_bridge";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * FML passes the mod event bus and this mod's container in by type, so nothing has to be
     * looked up manually. Kept even while unused: the bus is what future registration goes on.
     */
    public Create_jei_gauge_bridge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModNetwork::register);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        LOGGER.info("Create JEI Gauge Bridge initialized");
    }
}
