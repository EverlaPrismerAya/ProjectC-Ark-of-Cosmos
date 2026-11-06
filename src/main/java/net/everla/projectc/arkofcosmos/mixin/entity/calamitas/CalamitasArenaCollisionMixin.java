package net.everla.projectc.arkofcosmos.mixin.entity.calamitas;

import net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clip after block collision/step-up, before native Entity.move computes collision flags. */
@Mixin(Entity.class)
abstract class CalamitasArenaCollisionMixin {
    @ModifyVariable(method = "collide", at = @At("HEAD"), argsOnly = true)
    private Vec3 ark$calamitasRequestedMovement(Vec3 requested) {
        return CalamitasArenaBoundary.clip((Entity)(Object)this, requested);
    }
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void ark$calamitasArena(Vec3 requested, CallbackInfoReturnable<Vec3> callback) {
        var entity = (Entity)(Object)this;
        Vec3 allowed = CalamitasArenaBoundary.clip(entity, callback.getReturnValue());
        if (!allowed.equals(callback.getReturnValue())) {
            // Step-up may add vertical movement after the initial clip. Sweep again
            // when that movement hits the ceiling so clipping cannot step into a block.
            var collisions = entity.level().getEntityCollisions(entity, entity.getBoundingBox().expandTowards(allowed));
            allowed = Entity.collideBoundingBox(entity, allowed, entity.getBoundingBox(), entity.level(), collisions);
            callback.setReturnValue(allowed);
        }
    }
}
