package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * EMI "+" handler for Create's factory gauge item screen (and, with Fluid Logistics installed, the
 * fluid gauge screen, which uses a subclass of the same menu).
 */
public class EmiGaugeHandler<T extends FactoryPanelSetItemMenu> implements EmiRecipeHandler<T> {

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return EmiInventories.of(screen.getMinecraft().player);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return outcome(recipe, context).payload() != null;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        EmiImport.Outcome outcome = outcome(recipe, context);
        if (outcome.payload() == null) {
            return false;
        }
        PacketDistributor.sendToServer(outcome.payload());
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        EmiImport.Outcome outcome = outcome(recipe, context);
        if (outcome.errorKey() == null) {
            return List.of();
        }
        return List.of(ClientTooltipComponent.create(
                Component.translatable(outcome.errorKey()).getVisualOrderText()));
    }

    protected EmiImport.Outcome outcome(EmiRecipe recipe, EmiCraftContext<T> context) {
        return EmiImport.factory(recipe, context.getScreenHandler(),
                ModList.get().isLoaded("fluidlogistics"));
    }
}
