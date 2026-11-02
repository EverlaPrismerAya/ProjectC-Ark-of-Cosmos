package net.everla.projectc.arkofcosmos.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Calamity's animated Annihilation Ember material. */
public final class AnnihilationEmberItem extends Item {
    public AnnihilationEmberItem() { super(new Properties().stacksTo(64).fireResistant()); }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(style -> style.withColor(0x6C2DC7));
    }
}
