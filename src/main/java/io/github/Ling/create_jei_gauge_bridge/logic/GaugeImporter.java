package io.github.Ling.create_jei_gauge_bridge.logic;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateImporter;
import io.github.Ling.create_jei_gauge_bridge.compat.fluidlogistics.FluidLogisticsCompat;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import io.github.Ling.create_jei_gauge_bridge.network.OpenPanelScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server side of the import: writes the recipe into the gauge, then wires up the neighbouring
 * gauges that monitor its ingredients.
 *
 * <p>Linking is best-effort: whatever exists on the wall gets connected, and anything that could
 * not be found is listed in chat without undoing the import. This is deliberate — a recipe may
 * legitimately need a fluid that the player does not want to route through fluid logistics.</p>
 */
public final class GaugeImporter {

    /** Create allows at most nine inputs on one gauge, which is also the ingredient-type limit. */
    public static final int MAX_INPUTS = 9;

    /** How far the player may be from the gauge they are editing. */
    private static final double MAX_INTERACTION_DISTANCE_SQ = 64.0;

    private static final PanelSlot[] SLOTS = PanelSlot.values();

    private GaugeImporter() {
    }

    public static void handle(Player player, ImportRecipePayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_INTERACTION_DISTANCE_SQ) {
            return;
        }
        ServerLevel level = serverPlayer.serverLevel();
        if (!level.isLoaded(payload.pos())) {
            return;
        }

        if (payload.kind() == ImportRecipePayload.KIND_TEMPLATE_PANEL) {
            if (ModList.get().isLoaded("createimp")) {
                TemplateImporter.handle(serverPlayer, payload);
            }
            return;
        }

        importIntoFactoryPanel(serverPlayer, level, payload);
    }

    private static FactoryPanelBehaviour findDirectMatch(List<FactoryPanelBehaviour> wall,
                                                         List<FactoryPanelBehaviour> claimed,
                                                         Requirement requirement) {
        for (FactoryPanelBehaviour candidate : wall) {
            if (claimed.contains(candidate) || candidate.getFilter().isEmpty()) {
                continue;
            }
            // Filter gauges are handled separately: their filter may accept items the recipe does
            // not allow, so they only qualify when the whole filter set is valid.
            if (ModList.get().isLoaded("extra_gauges")
                    && io.github.Ling.create_jei_gauge_bridge.compat.extragauges.ExtraGaugesCompat
                    .isFilterPanel(candidate)) {
                continue;
            }
            if (PanelRequirements.matches(candidate, requirement)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Create: Extra Gauges filter gauges are accepted only when every item their filter accepts is
     * valid for the recipe ingredient. Otherwise the gauge is skipped and the search continues.
     */
    private static FactoryPanelBehaviour findFilterGaugeMatch(ServerLevel level,
                                                              List<FactoryPanelBehaviour> wall,
                                                              List<FactoryPanelBehaviour> claimed,
                                                              Requirement requirement) {
        if (!ModList.get().isLoaded("extra_gauges")) {
            return null;
        }
        for (FactoryPanelBehaviour candidate : wall) {
            if (claimed.contains(candidate)) {
                continue;
            }
            if (io.github.Ling.create_jei_gauge_bridge.compat.extragauges.ExtraGaugesCompat
                    .filterCovers(candidate, requirement, level)) {
                return candidate;
            }
        }
        return null;
    }

    private static void importIntoFactoryPanel(ServerPlayer player, ServerLevel level,
                                               ImportRecipePayload payload) {
        if (payload.slot() < 0 || payload.slot() >= SLOTS.length) {
            return;
        }
        FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(level,
                new FactoryPanelPosition(payload.pos(), SLOTS[payload.slot()]));
        if (panel == null) {
            return;
        }
        // Same network protection Create applies when a player opens the gauge normally.
        if (!com.simibubi.create.Create.LOGISTICS.mayInteract(panel.network, player)) {
            return;
        }

        boolean fluidPanel = ModList.get().isLoaded("fluidlogistics")
                && FluidLogisticsCompat.isFluidPanel(panel);
        ItemStack filterStack = fluidPanel ? payload.resultFluidKey() : payload.resultItem();
        if (filterStack.isEmpty()) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.unsupported_output")
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
        panel.recipeOutput = fluidPanel
                ? Math.max(1, payload.resultFluidAmount())
                : Math.max(1, filterStack.getCount());

        syncGhostSlot(player, filterStack);
        panel.blockEntity.notifyUpdate();

        List<Requirement> requirements = collectRequirements(payload);
        if (requirements.isEmpty()) {
            openEditor(player, payload);
            return;
        }

        List<FactoryPanelBehaviour> wall = WallScanner.sameWall(level, panel);
        Create_jei_gauge_bridge.LOGGER.info(
                "[gauge-bridge] server import: panel={} fluid={} filter={} wall={} requirements={}",
                panel.getPanelPosition(), fluidPanel, filterStack, wall.size(), requirements.size());
        Create_jei_gauge_bridge.LOGGER.info("[gauge-bridge] wall={} requirements={}",
                describeWall(wall), describeRequirements(requirements));

        Map<Requirement, FactoryPanelBehaviour> matches = new LinkedHashMap<>();
        List<Requirement> missing = new ArrayList<>();
        List<FactoryPanelBehaviour> claimed = new ArrayList<>();

        for (Requirement requirement : requirements) {
            FactoryPanelBehaviour match = findDirectMatch(wall, claimed, requirement);
            if (match == null) {
                match = findFilterGaugeMatch(level, wall, claimed, requirement);
            }
            if (match == null) {
                missing.add(requirement);
            } else {
                matches.put(requirement, match);
                claimed.add(match);
            }
        }

        int free = MAX_INPUTS - panel.targetedBy.size();
        if (free <= 0 && !matches.isEmpty()) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.too_many_inputs")
                    .withStyle(ChatFormatting.RED), false);
            openEditor(player, payload);
            return;
        }

        int linked = 0;
        for (Map.Entry<Requirement, FactoryPanelBehaviour> entry : matches.entrySet()) {
            if (linked >= free) {
                missing.add(entry.getKey());
                continue;
            }
            FactoryPanelBehaviour source = entry.getValue();
            panel.addConnection(source.getPanelPosition());
            FactoryPanelConnection connection = panel.targetedBy.get(source.getPanelPosition());
            if (connection != null) {
                connection.amount = entry.getKey().amount();
            }
            linked++;
        }
        panel.blockEntity.notifyUpdate();
        panel.blockEntity.sendData();

        Create_jei_gauge_bridge.LOGGER.info("[gauge-bridge] linked {} inputs, missing {}",
                linked, missing.size());
        report(player, linked, missing);
        openEditor(player, payload);
    }

    /** Tells the client to open the gauge's configuration screen once the import has been applied. */
    private static void openEditor(ServerPlayer player, ImportRecipePayload payload) {
        PacketDistributor.sendToPlayer(player,
                new OpenPanelScreenPayload(payload.pos(), payload.slot(), payload.kind()));
    }

    public static void report(ServerPlayer player, int linked, List<Requirement> missing) {
        if (missing.isEmpty()) {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.linked", linked)
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            player.displayClientMessage(Component
                    .translatable("create_jei_gauge_bridge.message.linked_with_missing",
                            linked, describe(missing))
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /**
     * Keeps the ghost slot of the still-open item screen in sync. Without this, closing the menu
     * calls {@code saveData}, which writes the (empty) ghost slot back over the filter we just set.
     */
    public static void syncGhostSlot(Player player, ItemStack stack) {
        if (player.containerMenu instanceof GhostItemMenu<?> menu) {
            menu.ghostInventory.setStackInSlot(0, stack.copy());
            if (menu.slots.size() > 36) {
                menu.getSlot(36).setChanged();
            }
        }
    }

    public static List<Requirement> collectRequirements(ImportRecipePayload payload) {
        Map<String, Requirement> unique = new LinkedHashMap<>();

        List<ItemStack> samples = payload.ingredientSamples();
        List<ResourceLocation> tags = payload.ingredientTags();
        for (int i = 0; i < samples.size(); i++) {
            ItemStack sample = samples.get(i);
            if (sample.isEmpty()) {
                continue;
            }
            ResourceLocation tag = i < tags.size() ? tags.get(i) : null;
            String key = tag != null ? "tag:" + tag : "item:" + keyOf(sample);
            // The 3x3 arrangement already counts every occurrence of an ingredient; only the
            // slot-by-slot fallback (non-crafting recipes) may accumulate duplicates. Summing both
            // is what turned six planks into "36".
            boolean countedFromArrangement = !payload.arrangement().isEmpty();
            int amount = countedFromArrangement
                    ? countInArrangement(payload.arrangement(), sample, tag)
                    : 1;

            Requirement previous = unique.get(key);
            if (previous == null) {
                unique.put(key, new Requirement(sample.copyWithCount(1), tag, amount, false, null));
            } else if (!countedFromArrangement) {
                unique.put(key, previous.withAmount(previous.amount() + amount));
            }
        }

        List<ItemStack> fluidKeys = payload.fluidKeys();
        List<Integer> fluidAmounts = payload.fluidAmounts();
        List<String> fluidNames = payload.fluidNames();
        for (int i = 0; i < fluidKeys.size(); i++) {
            ItemStack key = fluidKeys.get(i);
            String name = i < fluidNames.size() ? fluidNames.get(i) : "";
            int amount = i < fluidAmounts.size() ? Math.max(1, fluidAmounts.get(i)) : 1000;
            // A key is only available when Fluid Logistics is installed; the requirement is kept
            // regardless so the report can still name the fluid.
            String id = key.isEmpty() ? "fluid:" + name : "fluid:" + keyOf(key);

            Requirement previous = unique.get(id);
            if (previous == null) {
                unique.put(id, new Requirement(
                        key.isEmpty() ? ItemStack.EMPTY : key.copyWithCount(1),
                        null, amount, true, name));
            } else {
                unique.put(id, previous.withAmount(previous.amount() + amount));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static int countInArrangement(List<ItemStack> arrangement, ItemStack sample,
                                          ResourceLocation tag) {
        if (arrangement.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (ItemStack stack : arrangement) {
            if (stack.isEmpty()) {
                continue;
            }
            if (tag != null
                    ? stack.is(TagKey.create(Registries.ITEM, tag))
                    : ItemStack.isSameItemSameComponents(stack, sample)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static String keyOf(ItemStack stack) {
        Optional<ResourceLocation> id = stack.getItemHolder().unwrapKey()
                .map(key -> key.location());
        return id.map(ResourceLocation::toString).orElseGet(() -> "id:" + Item.getId(stack.getItem()));
    }

    private static List<String> describeWall(List<FactoryPanelBehaviour> wall) {
        List<String> out = new ArrayList<>();
        for (FactoryPanelBehaviour panel : wall) {
            out.add(panel.getPos().toShortString() + "/" + panel.getPanelPosition().slot()
                    + "=" + panel.getFilter().getHoverName().getString());
        }
        return out;
    }

    private static List<String> describeRequirements(List<Requirement> requirements) {
        List<String> out = new ArrayList<>();
        for (Requirement requirement : requirements) {
            String label;
            if (requirement.fluid()) {
                label = requirement.fluidName();
            } else if (requirement.tag() != null) {
                label = "#" + requirement.tag() + " (" + requirement.sample().getHoverName().getString() + ")";
            } else {
                label = requirement.sample().getHoverName().getString();
            }
            out.add(label + " x" + requirement.amount());
        }
        return out;
    }

    public static Component describe(List<Requirement> requirements) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < requirements.size(); i++) {
            if (i > 0) {
                result.append(Component.literal("、"));
            }
            Requirement requirement = requirements.get(i);
            if (requirement.fluid()) {
                result.append(Component.literal(requirement.fluidName() == null
                        ? "?" : requirement.fluidName()));
            } else {
                result.append(requirement.tag() != null
                        ? Component.literal("#" + requirement.tag())
                        : requirement.sample().getHoverName());
            }
        }
        return result;
    }
}
