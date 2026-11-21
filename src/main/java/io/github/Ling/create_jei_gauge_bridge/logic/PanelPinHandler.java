package io.github.Ling.create_jei_gauge_bridge.logic;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateAccess;
import io.github.Ling.create_jei_gauge_bridge.config.ClientConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak + empty-hand right-click on a gauge pins its highlight, so a wall of panels can be
 * inspected from a distance.
 *
 * <p>Only the client side is handled: cancelling the interaction there stops the use packet from
 * being sent, so Create never opens the panel screen either. When connection highlighting is
 * switched off in the client config the interaction is left untouched.</p>
 */
@EventBusSubscriber(modid = Create_jei_gauge_bridge.MODID)
public final class PanelPinHandler {

    private PanelPinHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        if (ClientConfig.connectionMode() == ClientConfig.HighlightMode.OFF) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        boolean factory = state.getBlock() instanceof FactoryPanelBlock;
        boolean template = !factory
                && ModList.get().isLoaded("createimp")
                && TemplateAccess.isTemplatePanel(state);
        if (!factory && !template) {
            return;
        }

        event.setCanceled(true);
        io.github.Ling.create_jei_gauge_bridge.client.PinClient
                .pin(event.getPos(), state, event.getHitVec(), template);
    }
}
