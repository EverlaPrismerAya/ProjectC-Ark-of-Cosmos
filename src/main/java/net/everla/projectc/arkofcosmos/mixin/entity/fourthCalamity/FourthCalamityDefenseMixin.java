package net.everla.projectc.arkofcosmos.mixin.entity.fourthCalamity;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(LivingEntity.class)
public abstract class FourthCalamityDefenseMixin {
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void fourthCommitDamage(LivingEntity entity,float health,Operation<Void> original,net.minecraft.world.damagesource.DamageSource source,float amount) {
        if(entity instanceof FourthCalamityEntity boss)boss.commitDamageHealth(source,health);
        else original.call(entity,health);
    }
}
