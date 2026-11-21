package io.github.Ling.create_jei_gauge_bridge.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import io.github.Ling.create_jei_gauge_bridge.compat.createimp.TemplateScreens;
import io.github.Ling.create_jei_gauge_bridge.network.ImportRecipePayload;
import io.github.Ling.create_jei_gauge_bridge.network.OpenPanelScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opens the right configuration screen after an import. Client-only: the payload handler that calls
 * this only runs on the physical client.
 *
 * <p>Opening is deferred by a few ticks on purpose. The server sends the block entity sync and the
 * "open" packet back to back, but they travel on different packet channels, and the screen reads
 * the panel state in its constructor. Opening immediately made the screen show stale data: an item
 * gauge displayed "air", a fluid gauge opened its empty-filter screen, and a template panel showed
 * crafting mode unticked.</p>
 */
public final class ClientScreens {

    /** Enough for the block entity data packet to be applied before the screen samples it. */
    private static final int OPEN_DELAY_TICKS = 4;

    private static final List<Pending> PENDING = new ArrayList<>();

    private ClientScreens() {
    }

    private static final class Pending {
        private final Player player;
        private final OpenPanelScreenPayload payload;
        private int ticks;

        private Pending(Player player, OpenPanelScreenPayload payload, int ticks) {
            this.player = player;
            this.payload = payload;
            this.ticks = ticks;
        }
    }

    public static void queue(Player player, OpenPanelScreenPayload payload) {
        PENDING.add(new Pending(player, payload, OPEN_DELAY_TICKS));
    }

    /** Called once per client tick from the client event subscriber. */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (--pending.ticks > 0) {
                continue;
            }
            iterator.remove();
            open(pending.player, pending.payload);
        }
    }

    public static void open(Player player, OpenPanelScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (payload.kind() == ImportRecipePayload.KIND_TEMPLATE_PANEL) {
            if (ModList.get().isLoaded("createimp")) {
                TemplateScreens.open(player, payload.pos(), payload.slot());
            }
            return;
        }

        PanelSlot[] slots = PanelSlot.values();
        if (payload.slot() < 0 || payload.slot() >= slots.length) {
            return;
        }
        FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(minecraft.level,
                new FactoryPanelPosition(payload.pos(), slots[payload.slot()]));
        if (panel == null) {
            return;
        }
        // Close the ghost item screen first so the configuration screen is not stacked on it.
        player.closeContainer();
        panel.displayScreen(player);
    }
}
