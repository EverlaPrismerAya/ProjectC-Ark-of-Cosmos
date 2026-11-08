package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.enchant.WitchSouls;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class NecromanticWeaponItem extends Item {
    public final boolean rancor;
    public NecromanticWeaponItem(boolean rancor){super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());this.rancor=rancor;}
    public int soulCost(){return (int)net.everla.projectc.arkofcosmos.ProjectCCommonConfig.value("weapons."+(rancor?"rancor":"gruesome_eminence")+".soul_cost");}
    public float damage(){return net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage(rancor?"rancor":"gruesome_eminence");}
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
    @Override public Component getName(ItemStack stack){return Component.translatable(getDescriptionId(stack)).withStyle(style->style.withColor(0x6C2DC7));}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return rancor?UseAnim.BOW:UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            if(NecromanticAttackEntity.casting(player)||!WitchSouls.pay(player,soulCost(stack)))return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);NecromanticAttackEntity.cast(player,this,hand);
        }else player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flags){
        lines.add(Component.translatable("item.arkofcosmos.necromantic.damage",java.math.BigDecimal.valueOf((double)Math.round(damage(stack)*10)/10).stripTrailingZeros().toPlainString()).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        lines.add(Component.translatable("item.arkofcosmos.necromantic.souls",soulCost(stack)).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.appendTooltip(stack,lines);
        // These source tooltips use Terraria's default white description color.
        lines.add(Component.translatable(getDescriptionId()+".description.1").withStyle(net.minecraft.ChatFormatting.WHITE));
        lines.add(Component.translatable(getDescriptionId()+".description.2").withStyle(net.minecraft.ChatFormatting.WHITE));
    }
}
