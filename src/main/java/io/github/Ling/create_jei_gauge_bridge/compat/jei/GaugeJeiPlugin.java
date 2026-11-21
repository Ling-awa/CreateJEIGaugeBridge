package io.github.Ling.create_jei_gauge_bridge.compat.jei;

import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * JEI entry point. Only the factory gauge item screen is wired up here; the optional Create:
 * Improve and Create: Fluid Logistics screens get their own handlers from the compat layer.
 */
@JeiPlugin
public class GaugeJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Create_jei_gauge_bridge.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        if (ModList.get().isLoaded("emi")) {
            // EMI's JEMI bridge wraps every JEI transfer handler into an EMI handler; with both
            // viewers installed that made EMI's recipe screens render incorrectly. EMI registers
            // its own native handlers in GaugeEmiPlugin instead.
            Create_jei_gauge_bridge.LOGGER.info(
                    "[gauge-bridge] EMI present: JEI transfer handlers skipped");
            return;
        }
        registration.addUniversalRecipeTransferHandler(new GaugeTransferHandler<>(FactoryPanelSetItemMenu.class));
        if (ModList.get().isLoaded("fluidlogistics")) {
            FluidLogisticsCompat.registerHandlers(registration);
        }
        if (ModList.get().isLoaded("createimp")) {
            registration.addUniversalRecipeTransferHandler(
                    new io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateTransferHandler());
        }
        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] registered JEI transfer handlers (fluid={}, template={})",
                ModList.get().isLoaded("fluidlogistics"), ModList.get().isLoaded("createimp"));
    }
}
