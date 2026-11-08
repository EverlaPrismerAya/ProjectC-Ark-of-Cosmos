package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Selected by the target's bytecode signature before Mixin's permissive matching. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.items.magic.DarkWand",remap=false)
public abstract class WitchLegacyTouchSoulMixin {
    @WrapMethod(method="canCastTouch",require=1)
    private boolean touchPayment(ItemStack item,Level level,LivingEntity caster,Operation<Boolean> original){
        if(caster instanceof Player player)return WitchSoulEvents.casting(player,item,()->original.call(item,level,caster));
        return original.call(item,level,caster);
    }
}
