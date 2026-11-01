package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=FoodData.class,remap=false)
abstract class OldOneFoodRegenerationMixin {
    @Group(name="oldOne$foodRegen",min=1)
    @Redirect(method={"tick","m_38710_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"),require=0)
    private void oldOne$regenDev(Player e,float amount){if(!OldOneEffects.regenBlocked(e))e.heal(amount);}
    @Group(name="oldOne$foodRegen",min=1)
    @Redirect(method={"tick","m_38710_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;m_5634_(F)V"),require=0)
    private void oldOne$regenSrg(Player e,float amount){if(!OldOneEffects.regenBlocked(e))e.heal(amount);}
}
