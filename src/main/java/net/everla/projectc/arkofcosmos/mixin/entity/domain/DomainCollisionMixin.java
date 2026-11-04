package net.everla.projectc.arkofcosmos.mixin.entity.domain;

import net.everla.projectc.arkofcosmos.combat.DomainBoundary;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class DomainCollisionMixin {
    @Inject(method="collide",at=@At("RETURN"),cancellable=true)
    private void ark$domainCollision(Vec3 requested,CallbackInfoReturnable<Vec3> cir){
        cir.setReturnValue(DomainBoundary.clip((Entity)(Object)this,cir.getReturnValue()));
    }
}
