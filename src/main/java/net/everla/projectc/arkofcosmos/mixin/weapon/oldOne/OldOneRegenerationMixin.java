package net.everla.projectc.arkofcosmos.mixin.weapon.oldOne;
import com.llamalad7.mixinextras.injector.WrapWithCondition;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=MobEffect.class,remap=false)
abstract class OldOneRegenerationMixin {
    @WrapWithCondition(method="applyEffectTick",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private boolean oldOne$regen(LivingEntity e,float amount){return (Object)this!=MobEffects.REGENERATION||!OldOneEffects.regenBlocked(e);}
}
