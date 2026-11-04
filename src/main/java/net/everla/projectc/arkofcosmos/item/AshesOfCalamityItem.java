package net.everla.projectc.arkofcosmos.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AshesOfCalamityItem extends Item {
    public AshesOfCalamityItem() { super(new Properties()); }

    @Override public Component getName(ItemStack stack) {
        // Calamity AshesofCalamity uses Terraria rarity 7 (Lime: RGB 150, 255, 10).
        return Component.translatable(getDescriptionId(stack)).withStyle(style -> style.withColor(0x96FF0A));
    }
}
