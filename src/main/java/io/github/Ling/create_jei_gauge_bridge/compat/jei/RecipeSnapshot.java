package io.github.Ling.create_jei_gauge_bridge.compat.jei;

import com.simibubi.create.content.processing.basin.BasinRecipe;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the server needs to know about the recipe the player clicked "+" on, extracted from
 * JEI's slot views so that it works for every recipe category (vanilla crafting, Create processing,
 * modded categories) instead of only {@code RecipeManager}-reachable recipes.
 *
 * <p>Fluids are always collected, even when Fluid Logistics is not installed: the import still
 * succeeds, and the chat report lists the fluids that no gauge could monitor.</p>
 */
public record RecipeSnapshot(
        ItemStack resultItem,
        ItemStack resultFluidKey,
        int resultFluidAmount,
        List<ItemStack> ingredientSamples,
        List<ResourceLocation> ingredientTags,
        List<ItemStack> fluidKeys,
        List<Integer> fluidAmounts,
        List<String> fluidNames,
        List<ItemStack> arrangement) {

    private static final int CRAFTING_GRID = 9;

    public static RecipeSnapshot create(Object recipe, IRecipeSlotsView slots, Player player) {
        boolean fluidLogistics = ModList.get().isLoaded("fluidlogistics");

        List<ItemStack> samples = new ArrayList<>();
        List<ResourceLocation> tags = new ArrayList<>();

        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> items = slot.getIngredients(VanillaTypes.ITEM_STACK).toList();
            if (items.isEmpty()) {
                continue;
            }
            ItemStack sample = slot.getDisplayedIngredient(VanillaTypes.ITEM_STACK).orElse(items.get(0));
            if (sample.isEmpty()) {
                continue;
            }
            samples.add(sample.copyWithCount(1));
            tags.add(slot.getTagKey().map(TagKey::location).orElse(null));
        }

        Map<Fluid, Integer> fluidTypeIndex = new LinkedHashMap<>();
        List<ItemStack> fluidKeys = new ArrayList<>();
        List<Integer> fluidAmounts = new ArrayList<>();
        List<String> fluidNames = new ArrayList<>();

        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            for (FluidStack fluid : slot.getIngredients(NeoForgeTypes.FLUID_STACK).toList()) {
                addFluidRequirement(fluidTypeIndex, fluidKeys, fluidAmounts, fluidNames, fluid,
                        fluidLogistics);
            }
        }

        ItemStack resultItem = ItemStack.EMPTY;
        FluidStack resultFluid = null;
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            if (resultItem.isEmpty()) {
                resultItem = slot.getDisplayedIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
            }
            if (resultFluid == null) {
                resultFluid = slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK).orElse(null);
            }
        }

        // Fallback: some Create categories render the fluid through their own slot widgets, so the
        // JEI slot view can come back without it. Read the processing recipe directly in that case.
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof BasinRecipe basin) {
            if (resultFluid == null || resultFluid.isEmpty()) {
                List<FluidStack> fluidResults = basin.getFluidResults();
                if (!fluidResults.isEmpty()) {
                    resultFluid = fluidResults.get(0);
                }
            }
            if (fluidKeys.isEmpty()) {
                for (SizedFluidIngredient ingredient : basin.getFluidIngredients()) {
                    for (FluidStack fluid : ingredient.getFluids()) {
                        addFluidRequirement(fluidTypeIndex, fluidKeys, fluidAmounts, fluidNames, fluid,
                                fluidLogistics);
                    }
                }
            }
        }

        ItemStack resultFluidKey = ItemStack.EMPTY;
        int resultFluidAmount = 0;
        if (fluidLogistics && resultFluid != null && !resultFluid.isEmpty()) {
            try {
                resultFluidKey = FluidLogisticsCompat.fluidKey(resultFluid);
                resultFluidAmount = resultFluid.getAmount();
            } catch (RuntimeException exception) {
                // Fluid Logistics only has proxy keys for fluids it registered; an unknown fluid
                // must not take the whole recipe transfer down with it.
                resultFluidKey = ItemStack.EMPTY;
            }
        }

        // List.copyOf rejects null elements, and a null tag means "concrete ingredient", so the
        // tag list has to be wrapped rather than copied. Copying used to throw a NullPointerException
        // on every button-state query for any recipe with a non-tag ingredient.
        return new RecipeSnapshot(resultItem.copy(), resultFluidKey, resultFluidAmount,
                List.copyOf(samples), Collections.unmodifiableList(new ArrayList<>(tags)),
                List.copyOf(fluidKeys), List.copyOf(fluidAmounts), List.copyOf(fluidNames),
                buildArrangement(recipe));
    }

    /** Number of distinct materials (item or fluid) the recipe consumes. */
    public int distinctTypeCount() {
        Set<Item> items = new HashSet<>();
        for (ItemStack sample : ingredientSamples) {
            if (!sample.isEmpty()) {
                items.add(sample.getItem());
            }
        }
        return items.size() + fluidKeys.size();
    }

    public ImportRecipePayload toPayload(net.minecraft.core.BlockPos pos, int slot, int kind) {
        return new ImportRecipePayload(pos, slot, kind, resultItem, resultFluidKey, resultFluidAmount,
                ingredientSamples, ingredientTags, fluidKeys, fluidAmounts, fluidNames, arrangement);
    }

    private static void addFluidRequirement(Map<Fluid, Integer> index, List<ItemStack> keys,
                                            List<Integer> amounts, List<String> names,
                                            FluidStack fluid, boolean fluidLogistics) {
        if (fluid.isEmpty()) {
            return;
        }
        int amount = Math.max(1, fluid.getAmount());
        Integer existing = index.get(fluid.getFluid());
        if (existing != null) {
            amounts.set(existing, amounts.get(existing) + amount);
            return;
        }

        ItemStack key = ItemStack.EMPTY;
        if (fluidLogistics) {
            try {
                key = FluidLogisticsCompat.fluidKey(fluid);
            } catch (RuntimeException exception) {
                // No proxy for this fluid: keep the requirement so the chat report can name it.
                key = ItemStack.EMPTY;
            }
        }

        index.put(fluid.getFluid(), keys.size());
        keys.add(key);
        amounts.add(amount);
        names.add(fluid.getHoverName().getString());
    }

    /**
     * Builds the 3x3 crafting arrangement Create stores in {@code activeCraftingArrangement}.
     * Only crafting recipes can be run by the mechanical crafter, so every other recipe leaves the
     * gauge in plain monitoring mode.
     */
    private static List<ItemStack> buildArrangement(Object recipe) {
        return recipe instanceof RecipeHolder<?> holder ? arrangementFor(holder) : List.of();
    }

    /** Public so the EMI compat layer can build the same 3x3 arrangement from a backing recipe. */
    public static List<ItemStack> arrangementFor(RecipeHolder<?> holder) {
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting)) {
            return List.of();
        }
        NonNullList<Ingredient> ingredients = crafting.getIngredients();
        int width = crafting instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;

        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(CRAFTING_GRID, ItemStack.EMPTY));
        for (int i = 0; i < Math.min(CRAFTING_GRID, ingredients.size()); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) {
                continue;
            }
            int row = i / Math.max(1, width);
            int column = i % Math.max(1, width);
            if (row < 3 && column < 3) {
                grid.set(row * 3 + column, options[0].copyWithCount(1));
            }
        }
        return List.copyOf(grid);
    }
}
