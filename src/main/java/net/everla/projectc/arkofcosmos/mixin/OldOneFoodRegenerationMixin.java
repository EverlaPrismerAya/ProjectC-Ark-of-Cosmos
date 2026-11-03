package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=FoodData.class,remap=false)
abstract class OldOneFoodRegenerationMixin {
    @Redirect(method="tick",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void oldOne$regen(Player e,float amount){if(!OldOneEffects.regenBlocked(e))e.heal(amount);}
}
