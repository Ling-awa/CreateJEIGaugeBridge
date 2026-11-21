package io.github.Ling.create_jei_gauge_bridge.network;

import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.logic.GaugeImporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server request asking to write a recipe into one gauge and link its inputs.
 *
 * <p>The payload carries a resolved snapshot rather than a recipe id on purpose: JEI's universal
 * transfer handler sees recipes from every category (vanilla crafting, Create processing, modded
 * categories), and most of them are not reachable through {@code RecipeManager} in a uniform way.
 * The server only trusts this data as far as a player could have produced it themselves, and
 * re-validates the parts it can (positions, type limit, filter presence).</p>
 */
public record ImportRecipePayload(
        BlockPos pos,
        int slot,
        int kind,
        ItemStack resultItem,
        ItemStack resultFluidKey,
        int resultFluidAmount,
        List<ItemStack> ingredientSamples,
        List<ResourceLocation> ingredientTags,
        List<ItemStack> fluidKeys,
        List<Integer> fluidAmounts,
        List<String> fluidNames,
        List<ItemStack> arrangement) implements CustomPacketPayload {

    /** Target is a {@code FactoryPanelBehaviour} slot (item or, with Fluid Logistics, fluid). */
    public static final int KIND_FACTORY_PANEL = 0;
    /** Target is a Create: Improve template panel slot. */
    public static final int KIND_TEMPLATE_PANEL = 1;

    public static final CustomPacketPayload.Type<ImportRecipePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Create_jei_gauge_bridge.MODID, "import_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportRecipePayload> STREAM_CODEC =
            StreamCodec.of(ImportRecipePayload::write, ImportRecipePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ImportRecipePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeVarInt(payload.kind);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.resultItem);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.resultFluidKey);
        buf.writeVarInt(payload.resultFluidAmount);
        writeList(buf, payload.ingredientSamples, ItemStack.OPTIONAL_STREAM_CODEC);
        writeNullableList(buf, payload.ingredientTags);
        writeList(buf, payload.fluidKeys, ItemStack.OPTIONAL_STREAM_CODEC);
        writeVarIntList(buf, payload.fluidAmounts);
        writeStringList(buf, payload.fluidNames);
        writeList(buf, payload.arrangement, ItemStack.OPTIONAL_STREAM_CODEC);
    }

    private static ImportRecipePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        int kind = buf.readVarInt();
        ItemStack resultItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack resultFluidKey = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int resultFluidAmount = buf.readVarInt();
        List<ItemStack> samples = readList(buf, ItemStack.OPTIONAL_STREAM_CODEC);
        List<ResourceLocation> tags = readNullableList(buf);
        List<ItemStack> fluidKeys = readList(buf, ItemStack.OPTIONAL_STREAM_CODEC);
        List<Integer> fluidAmounts = readVarIntList(buf);
        List<String> fluidNames = readStringList(buf);
        List<ItemStack> arrangement = readList(buf, ItemStack.OPTIONAL_STREAM_CODEC);
        return new ImportRecipePayload(pos, slot, kind, resultItem, resultFluidKey, resultFluidAmount,
                samples, tags, fluidKeys, fluidAmounts, fluidNames, arrangement);
    }

    public static void handle(ImportRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GaugeImporter.handle(context.player(), payload));
    }

    // --- small codec helpers -------------------------------------------------------------------
    // FriendlyByteBuf#writeCollection wants an encoder for the exact buffer type, which does not
    // accept the registry-aware codecs we need for ItemStack, so the lists are written by hand.

    private static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list,
                                      StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        buf.writeVarInt(list.size());
        for (T value : list) {
            codec.encode(buf, value);
        }
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buf,
                                        StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        int size = buf.readVarInt();
        List<T> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(codec.decode(buf));
        }
        return out;
    }

    private static void writeNullableList(RegistryFriendlyByteBuf buf, List<ResourceLocation> list) {
        buf.writeVarInt(list.size());
        for (ResourceLocation value : list) {
            buf.writeBoolean(value != null);
            if (value != null) {
                buf.writeResourceLocation(value);
            }
        }
    }

    private static List<ResourceLocation> readNullableList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ResourceLocation> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(buf.readBoolean() ? buf.readResourceLocation() : null);
        }
        return out;
    }

    private static void writeVarIntList(RegistryFriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        for (int value : list) {
            buf.writeVarInt(value);
        }
    }

    private static List<Integer> readVarIntList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(buf.readVarInt());
        }
        return out;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String value : list) {
            buf.writeUtf(value == null ? "" : value);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(buf.readUtf());
        }
        return out;
    }
}
