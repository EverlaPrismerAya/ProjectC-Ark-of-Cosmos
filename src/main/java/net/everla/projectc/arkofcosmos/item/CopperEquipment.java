package net.everla.projectc.arkofcosmos.item;
import java.util.EnumMap;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
public final class CopperEquipment {
 private CopperEquipment() {}
 public static final Tier TIER=new Tier(){
  public int getUses(){return 190;} public float getSpeed(){return 5f;} public float getAttackDamageBonus(){return 1f;}
  public int getLevel(){return 2;} public int getEnchantmentValue(){return 13;}
  public Ingredient getRepairIngredient(){return Ingredient.of(Items.COPPER_INGOT);}
 };
 public enum Armor implements ArmorMaterial {
  INSTANCE;
  public int getDurabilityForType(ArmorItem.Type t){return new int[]{13,15,16,11}[t.ordinal()]*15;}
  public int getDefenseForType(ArmorItem.Type t){return new int[]{2,5,6,2}[t.ordinal()];}
  public int getEnchantmentValue(){return 13;}
  public net.minecraft.sounds.SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_IRON;}
  public Ingredient getRepairIngredient(){return Ingredient.of(Items.COPPER_INGOT);}
  public String getName(){return "arkofcosmos:copper";}
  public float getToughness(){return 0f;}
  public float getKnockbackResistance(){return 0f;}
 }
}

