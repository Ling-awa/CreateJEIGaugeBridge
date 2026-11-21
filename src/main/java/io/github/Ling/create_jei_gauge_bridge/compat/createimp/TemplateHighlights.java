package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelConnectionHandler;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import io.github.Ling.create_jei_gauge_bridge.client.GoggleGaugeHighlighter;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Set;

/**
 * Engineer-goggles highlight support for Create: Improve template panels, mirroring the factory
 * gauge behaviour in {@link GoggleGaugeHighlighter}. Connections are traced along the panel's own
 * bent path, coloured by direction (extract blue / deposit gold).
 */
public final class TemplateHighlights {

    private TemplateHighlights() {
    }

    public static void lookAt(Minecraft minecraft, BlockHitResult hit, BlockState state, BlockPos pos) {
        TemplatePanelBlock.PanelSlot slot = TemplatePanelBlock.getTargetedSlot(pos, state, hit.getLocation());
        highlight(minecraft, state, new TemplatePanelPosition(pos, slot));
    }

    /** Highlights a template panel slot by position (used by the pinned highlight). */
    public static void highlight(Minecraft minecraft, BlockPos pos, int slotOrdinal) {
        TemplatePanelBlock.PanelSlot[] slots = TemplatePanelBlock.PanelSlot.values();
        if (slotOrdinal < 0 || slotOrdinal >= slots.length) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TemplatePanelBlock)) {
            return;
        }
        highlight(minecraft, state, new TemplatePanelPosition(pos, slots[slotOrdinal]));
    }

    private static void highlight(Minecraft minecraft, BlockState state, TemplatePanelPosition self) {
        TemplatePanelBehaviour panel = TemplatePanelBehaviour.at(minecraft.level, self);
        if (panel == null) {
            return;
        }
        AABB selfBox = TemplatePanelConnectionHandler.getBB(state, self);
        GoggleGaugeHighlighter.addLookBox(key("template-box", self), selfBox);
        Vec3 selfCenter = selfBox.getCenter();

        for (TemplatePanelConnection connection : panel.targetedBy.values()) {
            AABB sourceBox = boxFor(minecraft, connection.from);
            if (sourceBox == null) {
                continue;
            }
            GoggleGaugeHighlighter.addLookBox(key("template-box", connection.from), sourceBox);
            addPath("template-in:" + suffix(connection.from), selfCenter, sourceBox.getCenter(),
                    connection.getPath(minecraft.level, state, self), state,
                    GoggleGaugeHighlighter.COLOR_EXTRACT, selfBox, sourceBox);
        }

        for (TemplatePanelPosition target : panel.targeting) {
            AABB targetBox = boxFor(minecraft, target);
            if (targetBox == null) {
                continue;
            }
            GoggleGaugeHighlighter.addLookBox(key("template-box", target), targetBox);
            addOutputPath(minecraft, self, target, targetBox, selfBox);
        }
    }

    /** Draws the path from a downstream panel back to {@code self}, whatever type it is. */
    private static void addOutputPath(Minecraft minecraft, TemplatePanelPosition self,
                                      TemplatePanelPosition target, AABB targetBox, AABB selfBox) {
        BlockEntity blockEntity = minecraft.level.getBlockEntity(target.pos());
        BlockState state = minecraft.level.getBlockState(target.pos());
        Vec3 start = targetBox.getCenter();
        Vec3 end = selfBox.getCenter();

        if (blockEntity instanceof TemplatePanelBlockEntity templateBE) {
            TemplatePanelBehaviour downstream = templateBE.panels.get(target.slot());
            if (downstream == null) {
                return;
            }
            TemplatePanelConnection back = downstream.targetedBy.get(self);
            if (back == null) {
                GoggleGaugeHighlighter.addClippedLine("template-out:" + suffix(target),
                        start, end, GoggleGaugeHighlighter.COLOR_DEPOSIT, targetBox, selfBox);
                return;
            }
            addPath("template-out:" + suffix(target), start, end,
                    back.getPath(minecraft.level, state, target), state,
                    GoggleGaugeHighlighter.COLOR_DEPOSIT, targetBox, selfBox);
            return;
        }

        if (blockEntity instanceof FactoryPanelBlockEntity) {
            FactoryPanelPosition factoryTarget = toFactoryPosition(target);
            FactoryPanelBehaviour downstream = FactoryPanelBehaviour.at(minecraft.level, factoryTarget);
            if (downstream == null) {
                return;
            }
            FactoryPanelConnection back = downstream.targetedBy.get(toFactoryPosition(self));
            if (back == null) {
                GoggleGaugeHighlighter.addClippedLine("template-out:" + suffix(target),
                        start, end, GoggleGaugeHighlighter.COLOR_DEPOSIT, targetBox, selfBox);
                return;
            }
            float xRot = (float) Math.toDegrees(FactoryPanelBlock.getXRot(state));
            float yRot = (float) Math.toDegrees(FactoryPanelBlock.getYRot(state));
            GoggleGaugeHighlighter.addPolyline("template-out:" + suffix(target), start, end,
                    back.getPath(minecraft.level, state, factoryTarget), xRot, yRot,
                    GoggleGaugeHighlighter.COLOR_DEPOSIT, targetBox, selfBox);
        }
    }

    private static void addPath(String base, Vec3 start, Vec3 end, List<Direction> path,
                                BlockState state, int color, AABB startBox, AABB endBox) {
        float xRot = (float) Math.toDegrees(TemplatePanelBlock.getXRot(state));
        float yRot = (float) Math.toDegrees(TemplatePanelBlock.getYRot(state));
        GoggleGaugeHighlighter.addPolyline(base, start, end, path, xRot, yRot, color, startBox, endBox);
    }

    private static AABB boxFor(Minecraft minecraft, TemplatePanelPosition position) {
        BlockEntity blockEntity = minecraft.level.getBlockEntity(position.pos());
        BlockState state = minecraft.level.getBlockState(position.pos());
        if (blockEntity instanceof TemplatePanelBlockEntity) {
            return TemplatePanelConnectionHandler.getBB(state, position);
        }
        if (blockEntity instanceof FactoryPanelBlockEntity) {
            return FactoryPanelConnectionHandler.getBB(state, toFactoryPosition(position));
        }
        return null;
    }

    /** Adds held-item highlights for a nearby template panel block entity. */
    public static void collectHeld(BlockEntity blockEntity, ItemStack held, FluidStack heldFluid) {
        if (!(blockEntity instanceof TemplatePanelBlockEntity templateBE)) {
            return;
        }
        for (TemplatePanelBehaviour panel : templateBE.panels.values()) {
            ItemStack filter = panel.getFilter();
            if (filter.isEmpty()) {
                continue;
            }
            boolean matches = ItemStack.isSameItemSameComponents(filter, held);
            if (!matches && !heldFluid.isEmpty() && ModList.get().isLoaded("fluidlogistics")) {
                FluidStack fluid = FluidLogisticsCompat.fluidOfFilter(filter);
                matches = !fluid.isEmpty() && fluid.getFluid() == heldFluid.getFluid();
            }
            if (matches) {
                GoggleGaugeHighlighter.addHeldBox(key("template-held", panel.getPanelPosition()),
                        TemplatePanelConnectionHandler.getBB(
                                panel.blockEntity.getBlockState(), panel.getPanelPosition()));
            }
        }
    }

    private static FactoryPanelPosition toFactoryPosition(TemplatePanelPosition position) {
        return new FactoryPanelPosition(position.pos(),
                FactoryPanelBlock.PanelSlot.valueOf(position.slot().name()));
    }

    private static String suffix(TemplatePanelPosition position) {
        return position.pos().asLong() + ":" + position.slot().ordinal();
    }

    private static Object key(String prefix, TemplatePanelPosition position) {
        return GoggleGaugeHighlighter.boxKey(prefix, position.pos(), position.slot().ordinal());
    }
}
