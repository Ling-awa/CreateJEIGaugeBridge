package io.github.Ling.create_jei_gauge_bridge.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateAccess;
import io.github.Ling.create_jei_gauge_bridge.config.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;

/** Client-side handling of the "pin this highlight" interaction. */
public final class PinClient {

    private PinClient() {
    }

    public static void pin(BlockPos pos, BlockState state, BlockHitResult hit, boolean template) {
        int slot;
        if (template) {
            if (!ModList.get().isLoaded("createimp")) {
                return;
            }
            slot = TemplateAccess.slotOrdinal(pos, state, hit.getLocation());
        } else {
            slot = FactoryPanelBlock.getTargetedSlot(pos, state, hit.getLocation()).ordinal();
        }
        if (slot < 0) {
            return;
        }
        GoggleGaugeHighlighter.pin(pos, slot, template);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.highlight_pinned",
                            ClientConfig.pinSeconds())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
