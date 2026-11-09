package net.everla.projectc.arkofcosmos.item;

import java.util.*;
import net.everla.projectc.arkofcosmos.entity.BejeweledBoltEntity;
import net.everla.projectc.arkofcosmos.enchant.WitchSouls;
import net.everla.projectc.arkofcosmos.combat.InnateEnchantment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class BejeweledStaffItem extends SoulMagicWeaponItem {
    // Absolute source clock preserves the 22-frame cadence across releases and repeated use packets.
    private static final Map<Player,Long> NEXT=new WeakHashMap<>();
    public BejeweledStaffItem(){super("bejeweled_staff",new Properties().stacksTo(1).rarity(Rarity.RARE));}
    private boolean volley(Player player,ItemStack stack,boolean first){
        long now=player.level().getGameTime()*3,next=NEXT.getOrDefault(player,now);
        if(now<next)return true;
        if(!WitchSouls.pay(player,soulCost(stack)))return false;
        NEXT.put(player,(first?now:Math.max(next,now-2))+22);
        BejeweledBoltEntity.cast(player,stack,damage(stack,player));
        return true;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        player.startUsingItem(hand);
        // A failed server cast must also synchronize the stopped-use flag to the predicting client.
        if(!level.isClientSide&&!volley(player,stack,true)){player.stopUsingItem();return InteractionResultHolder.fail(stack);}
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BOW;}
    @Override public void onUseTick(Level level,LivingEntity user,ItemStack stack,int remaining){
        if(!level.isClientSide&&user instanceof Player player){
            if(!player.isAlive()||player.isSpectator()||player.getItemInHand(player.getUsedItemHand())!=stack||!volley(player,stack,false))player.stopUsingItem();
        }
    }
    @Override public Component getName(ItemStack stack){return Component.translatable(getDescriptionId()).withStyle(s->s.withColor(0xFF80D0));}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flags){
        lines.add(Component.translatable("item.arkofcosmos.necromantic.damage",java.math.BigDecimal.valueOf(Math.round(damage(stack)*100d)/100d).stripTrailingZeros().toPlainString()).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        lines.add(Component.translatable("item.arkofcosmos.necromantic.souls",soulCost(stack)).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
        InnateEnchantment.appendTooltip(stack,lines);
        lines.add(Component.translatable(getDescriptionId()+".description").withStyle(net.minecraft.ChatFormatting.WHITE));
        lines.add(Component.translatable(getDescriptionId()+".controls",Component.keybind("key.use")).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
