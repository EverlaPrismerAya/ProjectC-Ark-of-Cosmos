package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class FourthCalamityDefenseMixin {
    @Redirect(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void fourthCommitDamage(LivingEntity entity,float health) {
        if(entity instanceof FourthCalamityEntity boss)boss.commitDamageHealth(health);
        else entity.setHealth(health);
    }
}
