package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.Polarice3.Goety.api.magic.ISpell;
import net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keep soul context around the final method body, including other mods' overwrites. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.items.magic.DarkWand",remap=false)
public abstract class WitchWandSoulMixin {
    @WrapMethod(method="MagicResults(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lcom/Polarice3/Goety/api/magic/ISpell;)V",require=1)
    private void spellPayment(ItemStack item,Level level,LivingEntity caster,ISpell spell,Operation<Void> original){
        if(caster instanceof Player player)WitchSoulEvents.spell(player,item,()->original.call(item,level,caster,spell));
        else original.call(item,level,caster,spell);
    }
    @WrapMethod(method="canCastTouch",require=1)
    private boolean touchPayment(ItemStack item,Level level,LivingEntity caster,LivingEntity target,Operation<Boolean> original){
        if(caster instanceof Player player)return WitchSoulEvents.casting(player,item,()->original.call(item,level,caster,target));
        return original.call(item,level,caster,target);
    }
    @WrapMethod(method={"onUseTick","m_5929_"},require=1)
    private void channelPayment(Level level,LivingEntity caster,ItemStack item,int time,Operation<Void> original){
        if(caster instanceof Player player)WitchSoulEvents.spell(player,item,()->original.call(level,caster,item,time));
        else original.call(level,caster,item,time);
    }
}
