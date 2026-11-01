package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.OldOneEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Skip only Slow Falling's gravity branch while sneaking; keep the effect and fall protection. */
@Mixin(value=LivingEntity.class,remap=false)
abstract class OldOneGravityMixin {
    private static boolean active(LivingEntity e,MobEffect effect){
        return !(effect==MobEffects.SLOW_FALLING&&e.isShiftKeyDown()&&OldOneEffects.has(e,OldOneEffects.Status.GRAVITY))&&e.hasEffect(effect);
    }
    @Group(name="oldOne$gravity",min=1)
    @Redirect(method={"travel","m_7023_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"),require=0)
    private boolean oldOne$gravityDev(LivingEntity e,MobEffect effect){return active(e,effect);}
    @Group(name="oldOne$gravity",min=1)
    @Redirect(method={"travel","m_7023_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;m_21023_(Lnet/minecraft/world/effect/MobEffect;)Z"),require=0)
    private boolean oldOne$gravitySrg(LivingEntity e,MobEffect effect){return active(e,effect);}
}
