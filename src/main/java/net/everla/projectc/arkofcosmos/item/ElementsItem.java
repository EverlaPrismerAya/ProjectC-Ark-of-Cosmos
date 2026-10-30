package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules;
import net.everla.projectc.arkofcosmos.combat.ElementsCombat;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class ElementsItem extends SwordItem {
    public ElementsItem() { super(Tiers.NETHERITE, 2, 0, new Properties().stacksTo(1).fireResistant()); }
    public static int charge(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("ElementsCharge") : 0; }
    public static void charge(ItemStack stack, int value) { stack.getOrCreateTag().putInt("ElementsCharge", Math.max(0, Math.min(10, value))); }
    public static int cooldown(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("ElementsCooldown") : 0; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return true; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment != Enchantments.UNBREAKING && enchantment != Enchantments.MENDING && enchantment.category.canEnchant(this);
    }
    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
    @Override public boolean onEntitySwing(ItemStack stack, LivingEntity entity) { return true; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        return com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",ElementsRules.BASE_DAMAGE-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return InteractionResultHolder.success(player.getItemInHand(hand)); }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            int remaining=ElementsCombat.cooldown(player);
            if(cooldown(stack)!=remaining)stack.getOrCreateTag().putInt("ElementsCooldown",remaining);
        }
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId()).withStyle(s->s.withColor(0xA335EE)); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("elements.combo",Component.keybind("key.attack")).withStyle(s->s.withColor(0xDC143C)));
        lines.add(Component.translatable("elements.snap").withStyle(s->s.withColor(0xDC143C)));
        lines.add(Component.translatable("elements.parry",Component.keybind("key.use")).withStyle(s->s.withColor(0xFFA500)));
        lines.add(Component.translatable("elements.parry_window").withStyle(s->s.withColor(0xFFA500)));
        lines.add(Component.translatable("elements.empower").withStyle(s->s.withColor(0xFFA500)));
        lines.add(Component.translatable("elements.blast",Component.keybind("key.arkofcosmos.dash"),Component.keybind("key.use")).withStyle(s->s.withColor(0xFFD700)));
        lines.add(Component.translatable("elements.resonance"));
        lines.add(Component.translatable("elements.charge",charge(stack),10));
        lines.add(Component.translatable("elements.flavor").withStyle(s->s.withColor(0xA335EE)));
    }
}
