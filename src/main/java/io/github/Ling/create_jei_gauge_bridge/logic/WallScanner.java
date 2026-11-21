package io.github.Ling.create_jei_gauge_bridge.logic;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the other factory gauges mounted on the same wall as a given one.
 *
 * <p>This reimplements the surface test from Create's {@code FactoryPanelConnectionHandler}:
 * panels only connect when they sit on the same planar surface (the block-state difference must be
 * zero along the panel's normal), face the same way, and are within sixteen blocks. Create exposes
 * the individual checks but no enumerator, so the scan lives here.</p>
 */
public final class WallScanner {

    /** Same range Create enforces for a panel-to-panel connection. */
    public static final int MAX_DISTANCE = 16;

    private WallScanner() {
    }

    /**
     * @return every panel on the same wall as {@code origin}, excluding the origin panel itself.
     *         The list may contain panels with an empty filter; callers decide what counts as a match.
     */
    public static List<FactoryPanelBehaviour> sameWall(ServerLevel level, FactoryPanelBehaviour origin) {
        BlockPos originPos = origin.getPos();
        BlockState originState = origin.blockEntity.getBlockState();
        Direction normal = FactoryPanelBlock.connectedDirection(originState);
        BlockState originComparable = comparable(originState);

        List<FactoryPanelBehaviour> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                originPos.offset(-MAX_DISTANCE, -MAX_DISTANCE, -MAX_DISTANCE),
                originPos.offset(MAX_DISTANCE, MAX_DISTANCE, MAX_DISTANCE))) {

            if (!pos.closerThan(originPos, MAX_DISTANCE)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FactoryPanelBlock)) {
                continue;
            }
            // Same orientation: waterlogged/powered are runtime state, not geometry.
            if (!comparable(state).equals(originComparable)) {
                continue;
            }
            // Same surface: no offset along the axis the panel is mounted on.
            BlockPos diff = pos.subtract(originPos);
            if (normal.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity panelBE)) {
                continue;
            }
            for (FactoryPanelBehaviour panel : panelBE.panels.values()) {
                if (panel == origin || panel.getPanelPosition().equals(origin.getPanelPosition())) {
                    continue;
                }
                result.add(panel);
            }
        }
        // Nearest first, so a wall with several gauges monitoring the same item always links to
        // the same neighbour instead of depending on block iteration order.
        result.sort(java.util.Comparator.comparingDouble(panel -> panel.getPos().distSqr(originPos)));
        return result;
    }

    private static BlockState comparable(BlockState state) {
        return state.setValue(FactoryPanelBlock.WATERLOGGED, false)
                .setValue(FactoryPanelBlock.POWERED, false);
    }
}
