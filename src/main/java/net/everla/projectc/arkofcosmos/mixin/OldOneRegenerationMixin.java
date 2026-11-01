package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=MobEffect.class,remap=false)
abstract class OldOneRegenerationMixin {
    private void heal(LivingEntity entity,float amount){if((Object)this!=MobEffects.REGENERATION||!OldOneEffects.regenBlocked(entity))entity.heal(amount);}
    @Group(name="oldOne$regeneration",min=1)
    @Redirect(method={"applyEffectTick","m_6742_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V"),require=0)
    private void oldOne$regenDev(LivingEntity e,float amount){heal(e,amount);}
    @Group(name="oldOne$regeneration",min=1)
    @Redirect(method={"applyEffectTick","m_6742_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;m_5634_(F)V"),require=0)
    private void oldOne$regenSrg(LivingEntity e,float amount){heal(e,amount);}
}
