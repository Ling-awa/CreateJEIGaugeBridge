package io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.content.logistics.factoryGauge.ResourceFactoryGaugeSetFilterMenu;
import com.yision.fluidlogistics.content.logistics.factoryGauge.ResourceFactoryPanelBehaviour;
import io.github.Ling.create_jei_gauge_bridge.compat.jei.GaugeTransferHandler;
import io.github.Ling.create_jei_gauge_bridge.logic.Requirement;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Optional integration with Create: Fluid Logistics.
 *
 * <p>Fluid gauges are ordinary Create factory panels whose behaviour was swapped for
 * {@code ResourceFactoryPanelBehaviour}; the monitored fluid is stored as a synthetic
 * "fluid proxy" ItemStack rather than a {@code FluidStack}. Every method here is only called
 * behind a {@code ModList.isLoaded("fluidlogistics")} check so this class is never loaded when the
 * mod is absent.</p>
 */
public final class FluidLogisticsCompat {

    private FluidLogisticsCompat() {
    }

    public static boolean isFluidPanel(FactoryPanelBehaviour panel) {
        return panel instanceof ResourceFactoryPanelBehaviour resource && resource.isResourceGauge();
    }

    /** The fluid a panel monitors, or an empty stack for item panels / empty filters. */
    public static FluidStack fluidOf(FactoryPanelBehaviour panel) {
        if (!isFluidPanel(panel)) {
            return FluidStack.EMPTY;
        }
        return PackageResourceTypes.getFluid(panel.getFilter());
    }

    public static boolean matchesFluid(FactoryPanelBehaviour panel, Requirement requirement) {
        return isFluidPanel(panel) && matchesFluid(panel.getFilter(), requirement);
    }

    /**
     * Matches a raw filter stack (used for template panels, whose behaviour is not a Create factory
     * panel but which can still hold a Fluid Logistics key when the mod is installed).
     */
    public static boolean matchesFluid(ItemStack filter, Requirement requirement) {
        FluidStack wanted = PackageResourceTypes.getFluid(requirement.sample());
        FluidStack present = PackageResourceTypes.getFluid(filter);
        if (wanted.isEmpty() || present.isEmpty()) {
            return false;
        }
        return wanted.getFluid() == present.getFluid();
    }

    /** Fluid Logistics' canonical filter stack for a fluid. */
    public static ItemStack fluidKey(FluidStack fluid) {
        return PackageResourceTypes.createFluidKey(fluid);
    }

    /**
     * The fluid gauge's item screen is a subclass of Create's menu, but JEI looks handlers up by
     * the exact container class, so it needs a registration of its own.
     */
    public static void registerHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(
                new GaugeTransferHandler<>(ResourceFactoryGaugeSetFilterMenu.class));
    }
}
