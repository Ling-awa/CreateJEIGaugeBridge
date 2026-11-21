package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.logic.Requirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wall scan shared by the Create: Improve importer. Template panels are a different block than
 * Create's gauges, so both families have to be collected and compared through their respective
 * {@code connectedDirection} helpers.
 */
final class TemplateWall {

    static final int MAX_DISTANCE = 16;

    private TemplateWall() {
    }

    /**
     * @param factoryPanel the backing Create behaviour when this candidate is an ordinary/fluid
     *                     factory gauge, {@code null} for a template panel. Needed because fluid
     *                     requirements can only be matched against a real factory panel.
     */
    record Candidate(BlockPos pos, TemplatePanelBlock.PanelSlot slot, boolean template,
                     ItemStack filter, FactoryPanelBehaviour factoryPanel) {
    }

    static boolean matches(Candidate candidate, Requirement requirement) {
        if (candidate.filter().isEmpty()) {
            return false;
        }
        if (requirement.fluid()) {
            if (!ModList.get().isLoaded("fluidlogistics")) {
                return false;
            }
            if (candidate.factoryPanel() != null) {
                return FluidLogisticsCompat.matchesFluid(candidate.factoryPanel(), requirement);
            }
            // A template panel can also hold a Fluid Logistics key when that mod is installed.
            return candidate.template()
                    && FluidLogisticsCompat.matchesFluid(candidate.filter(), requirement);
        }
        if (requirement.tag() != null) {
            return candidate.filter().is(TagKey.create(Registries.ITEM, requirement.tag()))
                    || candidate.filter().getItem() == requirement.sample().getItem();
        }
        return ItemStack.isSameItemSameComponents(candidate.filter(), requirement.sample())
                || sharesItemTag(candidate.filter(), requirement.sample());
    }

    private static boolean sharesItemTag(ItemStack a, ItemStack b) {
        java.util.Set<TagKey<net.minecraft.world.item.Item>> tags =
                a.getItemHolder().tags().collect(java.util.stream.Collectors.toSet());
        return !tags.isEmpty() && b.getItemHolder().tags().anyMatch(tags::contains);
    }

    static List<Candidate> scan(ServerLevel level, TemplatePanelBehaviour origin) {
        BlockPos originPos = origin.getPos();
        Direction normal = TemplatePanelBlock.connectedDirection(origin.blockEntity.getBlockState());

        List<Candidate> result = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(
                originPos.offset(-MAX_DISTANCE, -MAX_DISTANCE, -MAX_DISTANCE),
                originPos.offset(MAX_DISTANCE, MAX_DISTANCE, MAX_DISTANCE))) {
            if (!cursor.closerThan(originPos, MAX_DISTANCE)) {
                continue;
            }
            // betweenClosed reuses one mutable BlockPos; keep a stable copy before storing it.
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);
            Direction direction;
            if (state.getBlock() instanceof TemplatePanelBlock) {
                direction = TemplatePanelBlock.connectedDirection(state);
            } else if (state.getBlock() instanceof FactoryPanelBlock) {
                direction = FactoryPanelBlock.connectedDirection(state);
            } else {
                continue;
            }
            if (direction != normal) {
                continue;
            }
            BlockPos diff = pos.subtract(originPos);
            if (normal.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TemplatePanelBlockEntity templateBE) {
                templateBE.panels.forEach((slot, panel) ->
                        result.add(new Candidate(pos, slot, true, panel.getFilter(), null)));
            } else if (blockEntity instanceof FactoryPanelBlockEntity factoryBE) {
                factoryBE.panels.forEach((slot, panel) -> result.add(new Candidate(pos,
                        TemplatePanelBlock.PanelSlot.valueOf(slot.name()), false, panel.getFilter(), panel)));
            }
        }
        // Nearest first, so a wall with several gauges monitoring the same item always links to the
        // same neighbour instead of depending on block iteration order.
        result.sort(Comparator.comparingDouble(candidate -> candidate.pos().distSqr(originPos)));
        return result;
    }

    static TemplatePanelPosition positionOf(Candidate candidate) {
        return new TemplatePanelPosition(candidate.pos(), candidate.slot());
    }
}
