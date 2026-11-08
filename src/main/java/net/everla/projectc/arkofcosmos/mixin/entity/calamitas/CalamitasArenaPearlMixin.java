package net.everla.projectc.arkofcosmos.mixin.entity.calamitas;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrowableProjectile.class)
abstract class CalamitasArenaPearlMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult ark$arenaPearl(HitResult original) {
        return (Object)this instanceof ThrownEnderpearl pearl ? CalamitasArenaBoundary.pearlHit(pearl, original) : original;
    }
}
