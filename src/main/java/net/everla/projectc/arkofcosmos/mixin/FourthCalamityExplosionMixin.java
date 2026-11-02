package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Explosion.class)
public abstract class FourthCalamityExplosionMixin {
    @Redirect(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void fourthNoBlastKnockback(Entity entity,Vec3 velocity) {
        if(!(entity instanceof FourthCalamityEntity))entity.setDeltaMovement(velocity);
    }
}
