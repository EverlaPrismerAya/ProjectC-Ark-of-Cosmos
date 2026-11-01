package net.everla.projectc.arkofcosmos.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.tags.ItemTags;

public final class SpearItem extends Item {
    public final SpearMaterial material;
    private final Multimap<Attribute,AttributeModifier> attributes;
    public SpearItem(SpearMaterial material) {
        super(properties(material)); this.material=material;
        attributes=ImmutableMultimap.<Attribute,AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",material.damage(),AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Weapon modifier",1.0/material.duration()-4,AttributeModifier.Operation.ADDITION)).build();
    }
    private static Properties properties(SpearMaterial m) {
        Properties p=new Properties().durability(m.durability());
        return m.name().equals("netherite") ? p.fireResistant() : p;
    }
    @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return slot==EquipmentSlot.MAINHAND?attributes:super.getDefaultAttributeModifiers(slot); }
    @Override public int getEnchantmentValue() { return material.enchantability(); }
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient) {
        return switch(material.name()) {
            case "wooden" -> ingredient.is(ItemTags.PLANKS);
            case "stone" -> ingredient.is(ItemTags.STONE_TOOL_MATERIALS);
            case "copper" -> ingredient.is(Items.COPPER_INGOT);
            case "iron" -> ingredient.is(Items.IRON_INGOT);
            case "golden" -> ingredient.is(Items.GOLD_INGOT);
            case "diamond" -> ingredient.is(Items.DIAMOND);
            default -> ingredient.is(Items.NETHERITE_INGOT);
        };
    }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack,Enchantment enchantment) { return VanillaEnchantments.accepts(stack,enchantment); }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker) { stack.hurtAndBreak(1,attacker,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));return true; }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player user,InteractionHand hand) {
        user.startUsingItem(hand); if(!level.isClientSide) SpearCombat.start(user);
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }
    @Override public void onUseTick(Level level,LivingEntity user,ItemStack stack,int remaining) {
        if(!level.isClientSide) SpearCombat.kinetic(user,stack,72000-remaining);
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity user,int remaining) { SpearCombat.stop(user); }
}
