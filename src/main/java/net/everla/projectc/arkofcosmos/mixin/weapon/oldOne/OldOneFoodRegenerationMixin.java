package net.everla.projectc.arkofcosmos.mixin.weapon.oldOne;
import com.llamalad7.mixinextras.injector.WrapWithCondition;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=FoodData.class,remap=false)
abstract class OldOneFoodRegenerationMixin {
    @WrapWithCondition(method="tick",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private boolean oldOne$regen(Player e,float amount){return !OldOneEffects.regenBlocked(e);}
}
