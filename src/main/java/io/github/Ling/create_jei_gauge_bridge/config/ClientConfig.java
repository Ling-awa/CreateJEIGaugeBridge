package io.github.Ling.create_jei_gauge_bridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings for the engineer-goggles overlay.
 *
 * <p>Values are read through the helpers instead of the raw spec so a config that has not been
 * loaded yet (first ticks, or a dedicated server) falls back to sensible defaults.</p>
 */
public final class ClientConfig {

    /** When an overlay should be shown. */
    public enum HighlightMode {
        /** Never. */
        OFF,
        /** Only while the player is sneaking. */
        SNEAKING_ONLY,
        /** Always while goggles are worn. */
        ALWAYS
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<HighlightMode> CONNECTIONS = BUILDER
            .comment("When to highlight the gauges connected to the one you are looking at.")
            .defineEnum("highlightConnections", HighlightMode.ALWAYS);

    public static final ModConfigSpec.EnumValue<HighlightMode> HELD_ITEM = BUILDER
            .comment("When to highlight gauges monitoring the item you are holding.")
            .defineEnum("highlightHeldItem", HighlightMode.ALWAYS);

    public static final ModConfigSpec.IntValue PIN_SECONDS = BUILDER
            .comment("How long a sneak right-click pins a highlight, in seconds (must be greater than 0).")
            .defineInRange("pinDurationSeconds", 15, 1, 3600);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static HighlightMode connectionMode() {
        return SPEC.isLoaded() ? CONNECTIONS.get() : HighlightMode.ALWAYS;
    }

    public static HighlightMode heldItemMode() {
        return SPEC.isLoaded() ? HELD_ITEM.get() : HighlightMode.ALWAYS;
    }

    /** Pin duration converted to client ticks. */
    public static int pinTicks() {
        return pinSeconds() * 20;
    }

    public static int pinSeconds() {
        return Math.max(1, SPEC.isLoaded() ? PIN_SECONDS.get() : 15);
    }

    /** Whether an overlay with the given mode should be visible for this player right now. */
    public static boolean isVisible(HighlightMode mode, boolean sneaking) {
        return switch (mode) {
            case OFF -> false;
            case SNEAKING_ONLY -> sneaking;
            case ALWAYS -> true;
        };
    }
}
