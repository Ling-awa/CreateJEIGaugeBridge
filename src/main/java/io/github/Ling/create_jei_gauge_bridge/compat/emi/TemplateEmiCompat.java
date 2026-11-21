package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import dev.emi.emi.api.EmiRegistry;

/** Template panel menu registration, isolated so it is only loaded when Create: Improve is present. */
public final class TemplateEmiCompat {

    private TemplateEmiCompat() {
    }

    public static void register(EmiRegistry registry) {
        registry.addRecipeHandler(
                com.molox.createimp.registry.ModMenuTypes.TEMPLATE_PANEL_SET_ITEM.get(),
                new EmiTemplateHandler());
    }
}
