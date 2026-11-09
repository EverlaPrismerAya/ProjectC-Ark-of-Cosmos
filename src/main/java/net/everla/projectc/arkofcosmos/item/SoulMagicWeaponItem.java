package net.everla.projectc.arkofcosmos.item;

import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

/** Shared Goety damage, soul fees and focus enchantment rules. Loaded only with Goety. */
public abstract class SoulMagicWeaponItem extends Item {
    private final String weaponId;
    protected SoulMagicWeaponItem(String weaponId, Properties properties){super(properties);this.weaponId=weaponId;}
    public int soulCost(){return (int)net.everla.projectc.arkofcosmos.ProjectCCommonConfig.value("weapons."+weaponId+".soul_cost");}
    public float damage(){return net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage(weaponId);}
    public int soulCost(ItemStack stack){return Math.max(1,(int)Math.ceil(soulCost()*Math.max(0,1-.1*EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DURATION.get(),stack))));}
    public float damage(ItemStack stack){return damage()*(1+.1f*EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.POTENCY.get(),stack));}
    public float damage(ItemStack stack,Player player){
        var attack=new net.minecraft.world.entity.ai.attributes.AttributeInstance(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,attribute->{});
        attack.replaceFrom(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        // Replace the main-hand weapon contribution, preserving player bonuses and all operations.
        attack.removeModifier(BASE_ATTACK_DAMAGE_UUID);
        attack.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",damage()-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        return (float)attack.getValue()*(1+.1f*EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.POTENCY.get(),stack));
    }
    @Override public int getEnchantmentValue(){return 15;}
    @Override public boolean isEnchantable(ItemStack stack){return stack.getCount()==1;}
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack,Enchantment enchantment){return enchantment==ModEnchantments.POTENCY.get()||enchantment==ModEnchantments.DURATION.get();}
}
