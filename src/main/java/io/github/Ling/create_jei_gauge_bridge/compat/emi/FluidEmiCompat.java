package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import net.minecraft.world.inventory.MenuType;

/** Fluid gauge menu registration, isolated so it is only loaded when Fluid Logistics is present. */
public final class FluidEmiCompat {

    private FluidEmiCompat() {
    }

    public static void register(EmiRegistry registry) {
        MenuType<?> menuType = EmiMenuTypes.create(
                "com.yision.fluidlogistics.registry.AllMenuTypes", "RESOURCE_GAUGE_SET_FILTER");
        EmiMenuTypes.add(registry, menuType, new EmiGaugeHandler<>());
    }
}
