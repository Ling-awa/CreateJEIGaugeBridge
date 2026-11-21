package io.github.Ling.create_jei_gauge_bridge.logic;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Decides whether a wall panel monitors what a recipe requirement asks for.
 *
 * <p>Tag requirements are matched through {@code ItemStack#is(TagKey)} so that a recipe asking for
 * {@code #minecraft:planks} links to a spruce gauge even when the recipe's representative stack is
 * oak. Concrete requirements compare item and components, matching Create's own filter semantics.</p>
 */
public final class PanelRequirements {

    private PanelRequirements() {
    }

    public static boolean matches(FactoryPanelBehaviour panel, Requirement requirement) {
        if (requirement.fluid()) {
            // Without Fluid Logistics there are no fluid gauges at all, so nothing can match; the
            // requirement is reported as missing instead of breaking the import.
            return requirement.sample() != null
                    && !requirement.sample().isEmpty()
                    && ModList.get().isLoaded("fluidlogistics")
                    && FluidLogisticsCompat.matchesFluid(panel, requirement);
        }
        ItemStack filter = panel.getFilter();
        if (filter.isEmpty()) {
            return false;
        }
        if (requirement.tag() != null) {
            // Tag first; fall back to the representative item in case the client's tag key cannot
            // be resolved on the server (datapack tag differences between client and server).
            return filter.is(TagKey.create(Registries.ITEM, requirement.tag()))
                    || filter.getItem() == requirement.sample().getItem();
        }
        return ItemStack.isSameItemSameComponents(filter, requirement.sample());
    }
}
