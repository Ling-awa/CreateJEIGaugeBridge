package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.screen.TemplatePanelSetItemMenu;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.compat.jei.RecipeSnapshot;
import io.github.Ling.create_jei_gauge_bridge.logic.GaugeImporter;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts an EMI recipe into the same snapshot payload the JEI handler sends.
 *
 * <p>EMI resolves tags to concrete stack lists instead of exposing a tag key, so item ingredients
 * are sent as their first stack; the server-side matcher falls back to shared item tags, which
 * keeps {@code #minecraft:planks}-style requirements linking to any wood type.</p>
 */
public final class EmiImport {

    public static final String TOO_MANY_TYPES = "create_jei_gauge_bridge.message.too_many_types";
    public static final String NO_ITEM_OUTPUT = "create_jei_gauge_bridge.message.unsupported_output";
    public static final String NO_FLUID_OUTPUT = "create_jei_gauge_bridge.message.unsupported_fluid_output";
    public static final String TEMPLATE_NO_OUTPUT = "create_jei_gauge_bridge.message.template_needs_output";

    private EmiImport() {
    }

    public record Outcome(@Nullable ImportRecipePayload payload, @Nullable String errorKey) {
    }

    public static Outcome factory(EmiRecipe recipe, FactoryPanelSetItemMenu menu, boolean fluidLogistics) {
        FactoryPanelBehaviour panel = menu.contentHolder;
        if (panel == null) {
            return new Outcome(null, null);
        }
        boolean fluidPanel = fluidLogistics && FluidLogisticsCompat.isFluidPanel(panel);
        Data data = Data.of(recipe, fluidLogistics);
        if (data.typeCount() > GaugeImporter.MAX_INPUTS) {
            return new Outcome(null, TOO_MANY_TYPES);
        }
        ItemStack output = fluidPanel ? data.resultFluidKey() : data.resultItem();
        if (output.isEmpty()) {
            return new Outcome(null, fluidPanel ? NO_FLUID_OUTPUT : NO_ITEM_OUTPUT);
        }
        FactoryPanelPosition position = panel.getPanelPosition();
        return new Outcome(data.toPayload(position.pos(), position.slot().ordinal(),
                ImportRecipePayload.KIND_FACTORY_PANEL), null);
    }

    public static Outcome template(EmiRecipe recipe, TemplatePanelSetItemMenu menu, boolean fluidLogistics) {
        TemplatePanelBehaviour panel = menu.contentHolder;
        if (panel == null) {
            return new Outcome(null, null);
        }
        Data data = Data.of(recipe, fluidLogistics);
        if (data.typeCount() > GaugeImporter.MAX_INPUTS) {
            return new Outcome(null, TOO_MANY_TYPES);
        }
        ItemStack filter = !data.resultItem().isEmpty()
                ? data.resultItem()
                : (fluidLogistics ? data.resultFluidKey() : ItemStack.EMPTY);
        if (filter.isEmpty()) {
            return new Outcome(null, TEMPLATE_NO_OUTPUT);
        }
        TemplatePanelPosition position = panel.getPanelPosition();
        return new Outcome(data.toPayload(position.pos(), position.slot().ordinal(),
                ImportRecipePayload.KIND_TEMPLATE_PANEL), null);
    }

    private record Data(ItemStack resultItem, ItemStack resultFluidKey, int resultFluidAmount,
                        List<ItemStack> samples, List<ResourceLocation> tags,
                        List<ItemStack> fluidKeys, List<Integer> fluidAmounts,
                        List<String> fluidNames, List<ItemStack> arrangement) {

        int typeCount() {
            Set<Item> items = new HashSet<>();
            for (ItemStack sample : samples) {
                if (!sample.isEmpty()) {
                    items.add(sample.getItem());
                }
            }
            return items.size() + fluidKeys.size();
        }

        ImportRecipePayload toPayload(BlockPos pos, int slot, int kind) {
            return new ImportRecipePayload(pos, slot, kind, resultItem, resultFluidKey, resultFluidAmount,
                    samples, tags, fluidKeys, fluidAmounts, fluidNames, arrangement);
        }

        static Data of(EmiRecipe recipe, boolean fluidLogistics) {
            List<ItemStack> samples = new ArrayList<>();
            List<ResourceLocation> tags = new ArrayList<>();
            List<ItemStack> fluidKeys = new ArrayList<>();
            List<Integer> fluidAmounts = new ArrayList<>();
            List<String> fluidNames = new ArrayList<>();

            for (EmiIngredient input : recipe.getInputs()) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                List<EmiStack> stacks = input.getEmiStacks();
                if (stacks.isEmpty()) {
                    continue;
                }
                EmiStack first = stacks.get(0);
                if (first.getKey() instanceof Fluid fluid) {
                    int amount = (int) Math.max(1L,
                            input.getAmount() > 0 ? input.getAmount() : first.getAmount());
                    FluidStack fluidStack = new FluidStack(fluid, amount);
                    fluidKeys.add(fluidLogistics ? safeKey(fluidStack) : ItemStack.EMPTY);
                    fluidAmounts.add(amount);
                    fluidNames.add(first.getName().getString());
                } else {
                    ItemStack stack = first.getItemStack();
                    if (stack.isEmpty()) {
                        continue;
                    }
                    samples.add(stack.copyWithCount(1));
                    tags.add(null);
                }
            }

            ItemStack resultItem = ItemStack.EMPTY;
            FluidStack resultFluid = null;
            for (EmiStack output : recipe.getOutputs()) {
                if (output.getKey() instanceof Fluid fluid) {
                    if (resultFluid == null) {
                        resultFluid = new FluidStack(fluid, (int) Math.max(1L, output.getAmount()));
                    }
                } else if (resultItem.isEmpty()) {
                    ItemStack stack = output.getItemStack();
                    if (!stack.isEmpty()) {
                        resultItem = stack.copyWithCount((int) Math.max(1L, output.getAmount()));
                    }
                }
            }

            ItemStack resultFluidKey = ItemStack.EMPTY;
            int resultFluidAmount = 0;
            if (fluidLogistics && resultFluid != null && !resultFluid.isEmpty()) {
                resultFluidKey = safeKey(resultFluid);
                if (!resultFluidKey.isEmpty()) {
                    resultFluidAmount = resultFluid.getAmount();
                }
            }

            return new Data(resultItem, resultFluidKey, resultFluidAmount, samples, tags,
                    fluidKeys, fluidAmounts, fluidNames,
                    RecipeSnapshot.arrangementFor(resolveHolder(recipe)));
        }
    }

    /**
     * EMI's native crafting recipes do not keep the backing {@code RecipeHolder}, but their id is
     * the real recipe id, so the client recipe manager can hand it back. Without this the imported
     * gauge would not switch into mechanical-crafting mode under EMI.
     */
    private static RecipeHolder<?> resolveHolder(EmiRecipe recipe) {
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        if (holder != null) {
            return holder;
        }
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        return Minecraft.getInstance().level.getRecipeManager()
                .byKey(recipe.getId())
                .orElse(null);
    }

    private static ItemStack safeKey(FluidStack fluid) {
        try {
            return FluidLogisticsCompat.fluidKey(fluid);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }
}
