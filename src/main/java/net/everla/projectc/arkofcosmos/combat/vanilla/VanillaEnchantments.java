package net.everla.projectc.arkofcosmos.combat.vanilla;

import net.everla.projectc.arkofcosmos.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;

public final class VanillaEnchantments extends Enchantment {
    public enum Kind { LUNGE, DENSITY, BREACH, WIND_BURST }
    public final Kind kind;
    public VanillaEnchantments(Kind kind) { super(kind==Kind.BREACH||kind==Kind.WIND_BURST?Rarity.RARE:Rarity.UNCOMMON,EnchantmentCategory.BREAKABLE,new EquipmentSlot[]{EquipmentSlot.MAINHAND});this.kind=kind; }
    @Override public int getMaxLevel() { return switch(kind) { case DENSITY -> 5;case BREACH -> 4;default -> 3; }; }
    @Override public int getMinCost(int level) { return (kind==Kind.LUNGE||kind==Kind.DENSITY?5:15)+(level-1)*(kind==Kind.LUNGE||kind==Kind.DENSITY?8:9); }
    @Override public int getMaxCost(int level) { return (kind==Kind.LUNGE||kind==Kind.DENSITY?25:65)+(level-1)*(kind==Kind.LUNGE||kind==Kind.DENSITY?8:9); }
    @Override public boolean isTreasureOnly() { return kind==Kind.WIND_BURST; }
    @Override public boolean isTradeable() { return kind!=Kind.WIND_BURST; }
    @Override public boolean isDiscoverable() { return kind!=Kind.WIND_BURST; }
    @Override public boolean canEnchant(ItemStack stack) { return kind==Kind.LUNGE?stack.getItem() instanceof SpearItem:stack.getItem() instanceof MaceItem; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return canEnchant(stack)&&kind!=Kind.WIND_BURST; }
    @Override protected boolean checkCompatibility(Enchantment other) {
        if(!super.checkCompatibility(other)) return false;
        if(kind!=Kind.DENSITY&&kind!=Kind.BREACH) return true;
        return !(other instanceof DamageEnchantment) && !(other instanceof VanillaEnchantments e && (e.kind==Kind.DENSITY||e.kind==Kind.BREACH));
    }
    public static boolean accepts(ItemStack stack,Enchantment e) {
        if(e instanceof VanillaEnchantments v) return v.canApplyAtEnchantingTable(stack);
        if(e==Enchantments.UNBREAKING||e==Enchantments.MENDING||e==Enchantments.VANISHING_CURSE||e==Enchantments.FIRE_ASPECT||e==Enchantments.MOB_LOOTING) return true;
        if(stack.getItem() instanceof SpearItem) return e instanceof DamageEnchantment||e==Enchantments.KNOCKBACK;
        return stack.getItem() instanceof MaceItem&&(e==Enchantments.SMITE||e==Enchantments.BANE_OF_ARTHROPODS);
    }
}
