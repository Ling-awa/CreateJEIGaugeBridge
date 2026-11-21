package io.github.Ling.create_jei_gauge_bridge.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import io.github.Ling.create_jei_gauge_bridge.Create_jei_gauge_bridge;
import net.minecraft.world.inventory.MenuType;

/**
 * Helpers for reaching Create's and Fluid Logistics' menu types.
 *
 * <p>Both expose them as Registrate {@code MenuEntry} objects. Compiling against Registrate would
 * mean pulling in another dependency that no reachable maven serves, so the entry is read
 * reflectively instead; there is exactly one such lookup per mod at startup.</p>
 */
final class EmiMenuTypes {

    private EmiMenuTypes() {
    }

    static MenuType<?> create(String ownerClass, String field) {
        try {
            Object entry = Class.forName(ownerClass).getField(field).get(null);
            Object menuType = entry.getClass().getMethod("get").invoke(entry);
            return menuType instanceof MenuType<?> type ? type : null;
        } catch (ReflectiveOperationException exception) {
            Create_jei_gauge_bridge.LOGGER.warn(
                    "[gauge-bridge] could not resolve menu type {}.{}", ownerClass, field, exception);
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void add(EmiRegistry registry, MenuType<?> menuType, EmiRecipeHandler<?> handler) {
        if (menuType != null) {
            registry.addRecipeHandler((MenuType) menuType, (EmiRecipeHandler) handler);
        }
    }
}
