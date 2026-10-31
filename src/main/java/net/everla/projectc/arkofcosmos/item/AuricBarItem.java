package net.everla.projectc.arkofcosmos.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AuricBarItem extends Item {
    public AuricBarItem() { super(new Properties().stacksTo(64).fireResistant()); }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(style -> style.withColor(0x6C2DC7));
    }
}
