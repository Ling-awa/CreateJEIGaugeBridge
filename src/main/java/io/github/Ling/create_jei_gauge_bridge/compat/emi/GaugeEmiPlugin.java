package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import net.neoforged.fml.ModList;

/**
 * EMI entry point. EMI's "+" is driven by {@code EmiRecipeHandler}s registered per menu type, so
 * the gauge item screens get their own handlers here.
 *
 * <p>When EMI is installed the JEI transfer handlers are not registered at all: EMI's JEMI bridge
 * wraps every JEI transfer handler into an EMI handler, which made EMI's recipe screens misbehave
 * when both viewers were present. One viewer, one set of handlers.</p>
 */
@EmiEntrypoint
public class GaugeEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiMenuTypes.add(registry,
                EmiMenuTypes.create("com.simibubi.create.AllMenuTypes", "FACTORY_PANEL_SET_ITEM"),
                new EmiGaugeHandler<>());

        if (ModList.get().isLoaded("fluidlogistics")) {
            FluidEmiCompat.register(registry);
        }
        if (ModList.get().isLoaded("createimp")) {
            TemplateEmiCompat.register(registry);
        }

        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] registered EMI recipe handlers (fluid={}, template={})",
                ModList.get().isLoaded("fluidlogistics"), ModList.get().isLoaded("createimp"));
    }
}
