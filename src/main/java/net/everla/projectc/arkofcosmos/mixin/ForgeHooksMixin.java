package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, remap = false)
abstract class ForgeHooksMixin {
    @Inject(method = "onLivingDamage", at = @At("RETURN"), cancellable = true)
    private static void ark$ordinaryMinimum(LivingEntity entity, DamageSource source, float amount,
                                            CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(net.everla.projectc.arkofcosmos.combat.WeaponDamage.minimum(entity, source, cir.getReturnValueF()));
    }

    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private static void ark$finishDeath(LivingEntity entity, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (ArkDamage.finishing(entity) && !ArkDamage.protectedPlayer(entity)) cir.setReturnValue(false);
    }
}
