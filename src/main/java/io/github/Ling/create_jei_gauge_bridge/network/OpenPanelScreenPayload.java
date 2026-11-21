package io.github.Ling.create_jei_gauge_bridge.network;

import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import io.github.Ling.create_jei_gauge_bridge.client.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: "the import is applied, open this panel's configuration screen".
 *
 * <p>Sent after the block entity data packet so the client copy already carries the new filter and
 * crafting arrangement when the screen reads them.</p>
 */
public record OpenPanelScreenPayload(BlockPos pos, int slot, int kind) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenPanelScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Create_jei_gauge_bridge.MODID, "open_panel_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPanelScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenPanelScreenPayload::pos,
                    ByteBufCodecs.VAR_INT, OpenPanelScreenPayload::slot,
                    ByteBufCodecs.VAR_INT, OpenPanelScreenPayload::kind,
                    OpenPanelScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPanelScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScreens.queue(context.player(), payload));
    }
}
