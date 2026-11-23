package io.github.Ling.create_jei_gauge_bridge.compat.createimp;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.logic.GaugeImporter;
import io.github.Ling.create_jei_gauge_bridge.logic.Requirement;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import io.github.Ling.create_jei_gauge_bridge.network.OpenPanelScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an imported recipe into a Create: Improve template panel and links the panels on the same
 * wall that monitor its ingredients.
 *
 * <p>Template panels are not Create factory gauges: they keep their own {@code targetedBy} map and
 * their own position type, but {@code addConnection} deliberately accepts an upstream that lives in
 * a {@code FactoryPanelBlockEntity}, so both families can feed a template chain. With Fluid
 * Logistics installed a template panel may also monitor a fluid, which is stored as that mod's
 * fluid-key ItemStack.</p>
 */
public final class TemplateImporter {

    private TemplateImporter() {
    }

    public static void handle(ServerPlayer player, ImportRecipePayload payload) {
        ServerLevel level = player.serverLevel();
        TemplatePanelBlock.PanelSlot[] slots = TemplatePanelBlock.PanelSlot.values();
        if (payload.slot() < 0 || payload.slot() >= slots.length) {
            return;
        }
        TemplatePanelBehaviour panel = TemplatePanelBehaviour.at(level,
                new TemplatePanelPosition(payload.pos(), slots[payload.slot()]));
        if (panel == null) {
            Create_jei_gauge_bridge.LOGGER.warn("[gauge-bridge] template panel not found at {} slot {}",
                    payload.pos(), payload.slot());
            return;
        }
        if (!com.simibubi.create.Create.LOGISTICS.mayInteract(panel.network, player)) {
            return;
        }

        boolean fluidLogistics = ModList.get().isLoaded("fluidlogistics");
        boolean fluidTarget = payload.resultItem().isEmpty()
                && fluidLogistics && !payload.resultFluidKey().isEmpty();
        ItemStack filterStack = !payload.resultItem().isEmpty()
                ? payload.resultItem()
                : (fluidTarget ? payload.resultFluidKey() : ItemStack.EMPTY);

        if (filterStack.isEmpty()) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.template_needs_output")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!panel.setFilter(filterStack)) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.invalid_filter")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        List<ItemStack> arrangement = payload.arrangement();
        panel.activeCraftingArrangement = arrangement.size() == 9 ? arrangement : List.of();
        panel.recipeOutput = fluidTarget
                ? Math.max(1, payload.resultFluidAmount())
                : Math.max(1, filterStack.getCount());

        GaugeImporter.syncGhostSlot(player, filterStack);
        panel.blockEntity.notifyUpdate();

        List<Requirement> requirements = GaugeImporter.collectRequirements(payload);
        if (requirements.isEmpty()) {
            openEditor(player, payload);
            return;
        }

        List<TemplateWall.Candidate> wall = TemplateWall.scan(level, panel);
        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] template import: panel={} fluid={} filter={} wall={} requirements={}",
                panel.getPanelPosition(), fluidTarget, filterStack, wall.size(), requirements.size());

        Map<Requirement, TemplateWall.Candidate> matches = new LinkedHashMap<>();
        List<Requirement> missing = new ArrayList<>();
        List<TemplateWall.Candidate> claimed = new ArrayList<>();

        for (Requirement requirement : requirements) {
            TemplateWall.Candidate match = null;
            for (TemplateWall.Candidate candidate : wall) {
                if (claimed.contains(candidate) || TemplateWall.isFilterGauge(candidate)) {
                    continue;
                }
                if (TemplateWall.matches(candidate, requirement)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                for (TemplateWall.Candidate candidate : wall) {
                    if (claimed.contains(candidate)
                            || !TemplateWall.matchesFilterGauge(level, candidate, requirement)) {
                        continue;
                    }
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                missing.add(requirement);
            } else {
                matches.put(requirement, match);
                claimed.add(match);
            }
        }

        int free = GaugeImporter.MAX_INPUTS - panel.targetedBy.size();
        if (free <= 0 && !matches.isEmpty()) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.too_many_inputs")
                    .withStyle(ChatFormatting.RED), false);
            openEditor(player, payload);
            return;
        }

        int linked = 0;
        for (Map.Entry<Requirement, TemplateWall.Candidate> entry : matches.entrySet()) {
            if (linked >= free) {
                missing.add(entry.getKey());
                continue;
            }
            TemplateWall.Candidate source = entry.getValue();
            TemplatePanelPosition position = TemplateWall.positionOf(source);
            panel.addConnection(position);
            TemplatePanelConnection connection = panel.targetedBy.get(position);
            if (connection != null) {
                connection.amount = entry.getKey().amount();
            }
            linked++;
        }
        panel.blockEntity.notifyUpdate();
        panel.blockEntity.sendData();

        Create_jei_gauge_bridge.LOGGER.info("[gauge-bridge] template linked {} inputs, missing {}",
                linked, missing.size());
        GaugeImporter.report(player, linked, missing);
        openEditor(player, payload);
    }

    private static void openEditor(ServerPlayer player, ImportRecipePayload payload) {
        PacketDistributor.sendToPlayer(player,
                new OpenPanelScreenPayload(payload.pos(), payload.slot(), payload.kind()));
    }
}
