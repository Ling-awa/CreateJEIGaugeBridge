package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.screen.TemplatePanelSetItemMenu;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.compat.jei.RecipeSnapshot;
import io.github.Ling.create_jei_gauge_bridge.compat.jei.TransferMessages;
import io.github.Ling.create_jei_gauge_bridge.logic.GaugeImporter;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** JEI "+" handler for Create: Improve's template panel item screen. */
public class TemplateTransferHandler implements IUniversalRecipeTransferHandler<TemplatePanelSetItemMenu> {

    private static Object lastFailedRecipe;

    @Override
    public Class<? extends TemplatePanelSetItemMenu> getContainerClass() {
        return TemplatePanelSetItemMenu.class;
    }

    @Override
    public Optional<MenuType<TemplatePanelSetItemMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(TemplatePanelSetItemMenu container, Object recipe,
                                                         IRecipeSlotsView recipeSlots, Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        TemplatePanelBehaviour panel = container.contentHolder;
        if (panel == null) {
            return null;
        }

        RecipeSnapshot snapshot;
        try {
            snapshot = RecipeSnapshot.create(recipe, recipeSlots, player);
        } catch (RuntimeException exception) {
            if (recipe != lastFailedRecipe) {
                lastFailedRecipe = recipe;
                Create_jei_gauge_bridge.LOGGER.error("[gauge-bridge] failed to inspect template recipe {}",
                        recipe == null ? "null" : recipe.getClass().getName(), exception);
            }
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.INTERNAL,
                    Component.empty());
        }
        if (snapshot.distinctTypeCount() > GaugeImporter.MAX_INPUTS) {
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.USER_FACING,
                    Component.translatable("create_jei_gauge_bridge.message.too_many_types"));
        }
        // Template panels are item-based, but with Fluid Logistics a template chain can also track a
        // fluid, so a fluid-only recipe is accepted when that mod can provide a key for it.
        ItemStack output = snapshot.resultItem().isEmpty()
                ? snapshot.resultFluidKey()
                : snapshot.resultItem();
        if (output.isEmpty()) {
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.USER_FACING,
                    Component.translatable("create_jei_gauge_bridge.message.template_needs_output"));
        }
        if (!doTransfer) {
            return new TransferMessages.SimpleError(IRecipeTransferError.Type.COSMETIC,
                    Component.translatable("create_jei_gauge_bridge.message.import_tooltip"));
        }

        TemplatePanelPosition position = panel.getPanelPosition();
        Create_jei_gauge_bridge.LOGGER.info("[gauge-bridge] template import request: panel={} output={} items={} types={}",
                position, snapshot.resultItem(), snapshot.ingredientSamples().size(),
                snapshot.distinctTypeCount());
        PacketDistributor.sendToServer(snapshot.toPayload(position.pos(), position.slot().ordinal(),
                ImportRecipePayload.KIND_TEMPLATE_PANEL));
        return null;
    }
}
