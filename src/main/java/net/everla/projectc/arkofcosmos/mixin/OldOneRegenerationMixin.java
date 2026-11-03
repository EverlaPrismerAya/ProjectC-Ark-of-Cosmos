package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=MobEffect.class,remap=false)
abstract class OldOneRegenerationMixin {
    private void heal(LivingEntity entity,float amount){if((Object)this!=MobEffects.REGENERATION||!OldOneEffects.regenBlocked(entity))entity.heal(amount);}
    @Redirect(method="applyEffectTick",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void oldOne$regen(LivingEntity e,float amount){heal(e,amount);}
}
