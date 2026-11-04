package net.everla.projectc.arkofcosmos.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.everla.projectc.arkofcosmos.item.ArkItem;
import net.everla.projectc.arkofcosmos.item.OldOneItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** A combat-only enchantment view: the item's actual enchantments remain untouched. */
public final class InnateEnchantment {
    private InnateEnchantment() {}

    private static Enchantment realityPiercer() {
        if (!ModList.get().isLoaded("goety_revelation")) return null;
        return ForgeRegistries.ENCHANTMENTS.getValue(
                ResourceLocation.fromNamespaceAndPath("goety_revelation", "reality_piercer"));
    }

    private static int level(ItemStack stack) {
        return stack.getItem() instanceof ArkItem ? 5 : stack.getItem() instanceof OldOneItem ? 255 : 0;
    }

    public static Map<Enchantment, Integer> combatEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> actual = stack.getAllEnchantments();
        int bonus = level(stack);
        Enchantment enchantment = bonus == 0 ? null : realityPiercer();
        if (enchantment == null) return actual;
        Map<Enchantment, Integer> effective = new HashMap<>(actual);
        effective.merge(enchantment, bonus, Integer::sum);
        return effective;
    }

    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        int bonus = level(stack);
        Enchantment enchantment = bonus == 0 ? null : realityPiercer();
        if (enchantment != null)
            lines.add(Component.translatable("tooltip.arkofcosmos.innate_enchantment", bonus > 10
                    ? Component.translatable(enchantment.getDescriptionId()).append(" " + bonus)
                    : enchantment.getFullname(bonus))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
