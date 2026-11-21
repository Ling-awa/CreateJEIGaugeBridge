package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Client-only helper that opens a template panel's configuration screen. */
public final class TemplateScreens {

    private TemplateScreens() {
    }

    public static void open(Player player, BlockPos pos, int slot) {
        TemplatePanelBlock.PanelSlot[] slots = TemplatePanelBlock.PanelSlot.values();
        if (slot < 0 || slot >= slots.length || Minecraft.getInstance().level == null) {
            return;
        }
        TemplatePanelBehaviour panel = TemplatePanelBehaviour.at(Minecraft.getInstance().level,
                new TemplatePanelPosition(pos, slots[slot]));
        if (panel == null) {
            return;
        }
        player.closeContainer();
        panel.displayScreen(player);
    }
}
