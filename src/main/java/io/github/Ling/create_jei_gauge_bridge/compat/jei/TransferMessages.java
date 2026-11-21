package io.github.Ling.create_jei_gauge_bridge.compat.jei;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.network.chat.Component;

/** One-line transfer errors used for both the disabled and the ready states of the "+" button. */
public final class TransferMessages {

    private TransferMessages() {
    }

    public record SimpleError(Type type, Component message) implements IRecipeTransferError {

        @Override
        public Type getType() {
            return type;
        }

        /**
         * Replacing the tooltip is safe: JEI only adds its default "transfer recipe" line when the
         * handler returns {@code null}, so a COSMETIC error shows exactly this text while keeping
         * the button clickable.
         */
        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(message);
        }

        /** Zero disables the orange highlight JEI would otherwise draw over a COSMETIC error. */
        @Override
        public int getButtonHighlightColor() {
            return 0;
        }
    }
}
