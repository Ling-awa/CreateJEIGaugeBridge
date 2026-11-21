package io.github.Ling.create_jei_gauge_bridge.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One input the imported recipe needs: an item (identified by a tag or a concrete stack) or a
 * fluid (identified by its Fluid Logistics key stack), plus how much of it a single craft needs.
 *
 * <p>{@code fluidName} is always filled for fluids, because without Fluid Logistics installed no
 * fluid key can be built and the chat report still has to tell the player what is missing.</p>
 */
public record Requirement(ItemStack sample, ResourceLocation tag, int amount, boolean fluid,
                          String fluidName) {

    public Requirement withAmount(int newAmount) {
        return new Requirement(sample, tag, newAmount, fluid, fluidName);
    }
}
