package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import com.molox.createimp.screen.TemplatePanelSetItemMenu;
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

/** EMI "+" handler for Create: Improve's template panel item screen. */
public class EmiTemplateHandler implements EmiRecipeHandler<TemplatePanelSetItemMenu> {

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<TemplatePanelSetItemMenu> screen) {
        return EmiInventories.of(screen.getMinecraft().player);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<TemplatePanelSetItemMenu> context) {
        return outcome(recipe, context).payload() != null;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<TemplatePanelSetItemMenu> context) {
        EmiImport.Outcome outcome = outcome(recipe, context);
        if (outcome.payload() == null) {
            return false;
        }
        PacketDistributor.sendToServer(outcome.payload());
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe,
                                                   EmiCraftContext<TemplatePanelSetItemMenu> context) {
        EmiImport.Outcome outcome = outcome(recipe, context);
        if (outcome.errorKey() == null) {
            return List.of();
        }
        return List.of(ClientTooltipComponent.create(
                Component.translatable(outcome.errorKey()).getVisualOrderText()));
    }

    private EmiImport.Outcome outcome(EmiRecipe recipe,
                                      EmiCraftContext<TemplatePanelSetItemMenu> context) {
        return EmiImport.template(recipe, context.getScreenHandler(),
                ModList.get().isLoaded("fluidlogistics"));
    }
}
