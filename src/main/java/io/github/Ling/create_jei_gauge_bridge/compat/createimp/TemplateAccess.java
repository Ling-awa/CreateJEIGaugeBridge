package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-safe entry points into Create: Improve template panel types.
 *
 * <p>Kept free of client classes so the common interaction handler can call it on both sides
 * without loading client-only types on a dedicated server.</p>
 */
public final class TemplateAccess {

    private TemplateAccess() {
    }

    public static boolean isTemplatePanel(BlockState state) {
        return state.getBlock() instanceof TemplatePanelBlock;
    }

    public static int slotOrdinal(BlockPos pos, BlockState state, Vec3 hitLocation) {
        return TemplatePanelBlock.getTargetedSlot(pos, state, hitLocation).ordinal();
    }
}
