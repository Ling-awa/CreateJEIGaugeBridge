package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the EMI inventory view for a player.
 *
 * <p>Both {@code EmiPlayerInventory.of(player)} and the {@code EmiPlayerInventory(Player)}
 * constructor go through the recipe handlers registered for the currently open screen. Our own
 * handlers are registered for that screen, so either call re-entered {@code getInventory} until the
 * stack overflowed. Collecting the stacks here and using the plain list constructor keeps the
 * lookup side-effect free.</p>
 */
final class EmiInventories {

    private EmiInventories() {
    }

    static EmiPlayerInventory of(Player player) {
        List<EmiStack> stacks = new ArrayList<>();
        if (player != null) {
            addAll(stacks, player.getInventory().items);
            addAll(stacks, player.getInventory().armor);
            addAll(stacks, player.getInventory().offhand);
            add(stacks, player.containerMenu.getCarried());
        }
        return new EmiPlayerInventory(stacks);
    }

    private static void addAll(List<EmiStack> target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            add(target, stack);
        }
    }

    private static void add(List<EmiStack> target, ItemStack stack) {
        if (!stack.isEmpty()) {
            target.add(EmiStack.of(stack, stack.getCount()));
        }
    }
}
