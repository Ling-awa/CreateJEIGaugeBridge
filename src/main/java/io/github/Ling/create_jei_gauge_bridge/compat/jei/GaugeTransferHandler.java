package io.github.Ling.create_jei_gauge_bridge.compat.jei;

import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.logic.GaugeImporter;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * JEI "+" handler for Create's factory gauge item screen (opened by right-clicking an empty gauge
 * with an empty hand).
 *
 * <p>The button has two states, both driven through JEI's error mechanism:</p>
 * <ul>
 *   <li>not importable: a {@code USER_FACING} error disables the button and shows the reason on
 *       hover (too many ingredient types, or an output the target gauge cannot monitor);</li>
 *   <li>ready: a {@code COSMETIC} error keeps the button usable while replacing JEI's default
 *       "transfer recipe" tooltip with our own wording.</li>
 * </ul>
 */
public class GaugeTransferHandler<C extends FactoryPanelSetItemMenu> implements IUniversalRecipeTransferHandler<C> {

    private final Class<C> containerClass;

    public GaugeTransferHandler(Class<C> containerClass) {
        this.containerClass = containerClass;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(C container, Object recipe,
                                                         IRecipeSlotsView recipeSlots, Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        FactoryPanelBehaviour panel = container.contentHolder;
        if (panel == null) {
            return null;
        }

        boolean fluidPanel = ModList.get().isLoaded("fluidlogistics")
                && FluidLogisticsCompat.isFluidPanel(panel);
        RecipeSnapshot snapshot;
        try {
            snapshot = RecipeSnapshot.create(recipe, recipeSlots, player);
        } catch (RuntimeException exception) {
            logFailure(recipe, exception);
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.INTERNAL,
                    Component.empty());
        }

        if (snapshot.distinctTypeCount() > GaugeImporter.MAX_INPUTS) {
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.USER_FACING,
                    Component.translatable("create_jei_gauge_bridge.message.too_many_types"));
        }

        ItemStack output = fluidPanel ? snapshot.resultFluidKey() : snapshot.resultItem();
        if (output.isEmpty()) {
            logMissingOutput(recipe, fluidPanel, snapshot);
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.USER_FACING,
                    Component.translatable(fluidPanel
                            ? "create_jei_gauge_bridge.message.unsupported_fluid_output"
                            : "create_jei_gauge_bridge.message.unsupported_output"));
        }

        if (!doTransfer) {
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.COSMETIC,
                    Component.translatable("create_jei_gauge_bridge.message.import_tooltip"));
        }

        FactoryPanelPosition position = panel.getPanelPosition();
        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] import request: panel={} fluid={} output={} items={} fluids={} types={}",
                position, fluidPanel, output, snapshot.ingredientSamples().size(),
                snapshot.fluidKeys().size(), snapshot.distinctTypeCount());
        PacketDistributor.sendToServer(snapshot.toPayload(position.pos(), position.slot().ordinal(),
                ImportRecipePayload.KIND_FACTORY_PANEL));
        return null;
    }

    /** One log line per recipe, so a report of "this recipe cannot be added" is diagnosable. */
    private static Object lastMissingOutputRecipe;

    /** One log line per failing recipe; JEI queries the button state every tick. */
    private static Object lastFailedRecipe;

    private static void logFailure(Object recipe, RuntimeException exception) {
        if (recipe == lastFailedRecipe) {
            return;
        }
        lastFailedRecipe = recipe;
        Create_jei_gauge_bridge.LOGGER.error(
                "[gauge-bridge] failed to inspect recipe {}", recipe == null ? "null" : recipe.getClass().getName(),
                exception);
    }

    private static void logMissingOutput(Object recipe, boolean fluidPanel, RecipeSnapshot snapshot) {
        if (recipe == lastMissingOutputRecipe) {
            return;
        }
        lastMissingOutputRecipe = recipe;
        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] no usable output: fluidTarget={} itemOut={} fluidOut={} items={} fluids={} recipe={}",
                fluidPanel, snapshot.resultItem(), snapshot.resultFluidKey(),
                snapshot.ingredientSamples().size(), snapshot.fluidKeys().size(),
                recipe == null ? "null" : recipe.getClass().getName());
    }
}
