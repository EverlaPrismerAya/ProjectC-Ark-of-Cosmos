package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.calamity.combat.AncientsRules;
import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class AncientsArkItem extends SwordItem {
    public AncientsArkItem() { super(Tiers.GOLD, 1, 0, new Properties().stacksTo(1).fireResistant()); }
    public static int charge(ItemStack stack) { return stack.hasTag() ? Math.max(0, Math.min(10, stack.getTag().getInt("AncientsCharge"))) : 0; }
    public static void charge(ItemStack stack, int value) { stack.getOrCreateTag().putInt("AncientsCharge", Math.max(0, Math.min(AncientsRules.MAX_CHARGE, value))); }
    public static int cooldown(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("ArkParryCooldown") : 0; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return true; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment != Enchantments.UNBREAKING && enchantment != Enchantments.MENDING && enchantment.category.canEnchant(this);
    }
    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
    @Override public boolean onEntitySwing(ItemStack stack, LivingEntity entity) { return true; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        return com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
            new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", AncientsRules.BASE_DAMAGE - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return InteractionResultHolder.success(player.getItemInHand(hand)); }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && cooldown(stack) != ArkCombat.parryCooldown(player))
            stack.getOrCreateTag().putInt("ArkParryCooldown", ArkCombat.parryCooldown(player));
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId()).withStyle(s -> s.withColor(0xFFFF0A)); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("fractured.parry", Component.keybind("key.use")).withStyle(s -> s.withColor(0x6495ED)));
        lines.add(Component.translatable("fractured.window").withStyle(s -> s.withColor(0x6495ED)));
        lines.add(Component.translatable("fractured.charge").withStyle(s -> s.withColor(0x6495ED)));
        lines.add(Component.translatable("ancients.blast", Component.keybind("key.use"), Component.keybind("key.arkofcosmos.dash")).withStyle(s -> s.withColor(0x6495ED)));
        lines.add(Component.translatable("ancients.flavor").withStyle(s -> s.withColor(0xFFFF0A)));
    }
}
