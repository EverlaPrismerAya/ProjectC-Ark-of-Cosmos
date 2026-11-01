package net.everla.projectc.arkofcosmos.item;

import com.google.common.collect.*;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;

public final class MaceItem extends Item {
    private final Multimap<Attribute,AttributeModifier> attributes=ImmutableMultimap.<Attribute,AttributeModifier>builder()
        .put(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",5,AttributeModifier.Operation.ADDITION))
        .put(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Weapon modifier",-3.4f,AttributeModifier.Operation.ADDITION)).build();
    public MaceItem() { super(new Properties().durability(500).rarity(Rarity.EPIC)); }
    @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return slot==EquipmentSlot.MAINHAND?attributes:super.getDefaultAttributeModifiers(slot); }
    @Override public int getEnchantmentValue() { return 15; }
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient) { return ingredient.is(Items.BLAZE_ROD); }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack,Enchantment enchantment) { return VanillaEnchantments.accepts(stack,enchantment); }
    @Override public boolean mineBlock(ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.BlockPos pos,LivingEntity miner) {
        if(!level.isClientSide&&state.getDestroySpeed(level,pos)!=0)stack.hurtAndBreak(2,miner,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));return true;
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker) {
        if(!attacker.level().isClientSide) { if(MaceCombat.canSmash(attacker))MaceCombat.impact(attacker,target,stack);else MaceCombat.wind(attacker,stack); }
        stack.hurtAndBreak(1,attacker,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));return true;
    }
}
