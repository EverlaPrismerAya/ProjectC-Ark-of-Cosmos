package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.enchant.WitchSouls;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class NecromanticWeaponItem extends Item {
    public final boolean rancor;
    public NecromanticWeaponItem(boolean rancor){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));this.rancor=rancor;}
    public int soulCost(){return rancor?25:30;}
    public float damage(){return rancor?18:36;}
    @Override public Component getName(ItemStack stack){return Component.translatable(getDescriptionId(stack)).withStyle(style->style.withColor(0x6C2DC7));}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return rancor?UseAnim.BOW:UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            if(NecromanticAttackEntity.casting(player)||!WitchSouls.pay(player,soulCost()))return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);NecromanticAttackEntity.cast(player,this,hand);
        }else player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flags){
        lines.add(Component.translatable("item.arkofcosmos.necromantic.damage",(int)damage()).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        lines.add(Component.translatable("item.arkofcosmos.necromantic.souls",soulCost()).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.appendTooltip(stack,lines);
        // These source tooltips use Terraria's default white description color.
        lines.add(Component.translatable(getDescriptionId()+".description.1").withStyle(net.minecraft.ChatFormatting.WHITE));
        lines.add(Component.translatable(getDescriptionId()+".description.2").withStyle(net.minecraft.ChatFormatting.WHITE));
    }
}
