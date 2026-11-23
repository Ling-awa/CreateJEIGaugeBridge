package io.github.Ling.create_jei_gauge_bridge.compat.extragauges;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import io.github.Ling.create_jei_gauge_bridge.logic.Requirement;
import net.liukrast.eg.content.logistics.board.FilterPanelBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Optional integration with Create: Extra Gauges.
 *
 * <p>Filter gauges are ordinary {@code FactoryPanelBehaviour}s on the same board, so Create's
 * connection system and the goggle overlay already treat them normally. What they need is recipe
 * import support: a filter gauge may stand in for a recipe ingredient only when <em>every</em> item
 * its filter accepts is valid for that ingredient. A filter that also matches one invalid item is
 * skipped, and the importer keeps looking for another gauge.</p>
 */
public final class ExtraGaugesCompat {

    private ExtraGaugesCompat() {
    }

    public static boolean isFilterPanel(FactoryPanelBehaviour panel) {
        return panel instanceof FilterPanelBehaviour;
    }

    /** True when every item the filter accepts is valid for the requirement, and it accepts any. */
    public static boolean filterCovers(FactoryPanelBehaviour panel, Requirement requirement, Level level) {
        if (requirement.fluid() || requirement.sample() == null || requirement.sample().isEmpty()) {
            return false;
        }
        if (!(panel instanceof FilterPanelBehaviour filterPanel)) {
            return false;
        }
        FilterItemStack filter = filterPanel.cachedFilter;
        if (filter == null || filter.isEmpty()) {
            return false;
        }

        boolean acceptsAnything = false;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!filter.test(level, stack)) {
                continue;
            }
            acceptsAnything = true;
            if (!requirementAccepts(requirement, stack)) {
                return false;
            }
        }
        return acceptsAnything;
    }

    /** Held-item matching: any item the filter accepts counts as "monitored" by this panel. */
    public static boolean filterAccepts(FactoryPanelBehaviour panel, ItemStack held) {
        if (!(panel instanceof FilterPanelBehaviour filterPanel)) {
            return false;
        }
        FilterItemStack filter = filterPanel.cachedFilter;
        return filter != null && !filter.isEmpty() && filter.test(panel.getWorld(), held);
    }

    private static boolean requirementAccepts(Requirement requirement, ItemStack stack) {
        if (requirement.tag() != null) {
            return stack.is(TagKey.create(Registries.ITEM, requirement.tag()));
        }
        return stack.getItem() == requirement.sample().getItem();
    }
}
