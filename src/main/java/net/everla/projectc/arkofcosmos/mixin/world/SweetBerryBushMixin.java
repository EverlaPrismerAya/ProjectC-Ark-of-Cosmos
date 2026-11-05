package net.everla.projectc.arkofcosmos.mixin.world;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(SweetBerryBushBlock.class)
public abstract class SweetBerryBushMixin {
    @WrapOperation(method="entityInside",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ark$berryDamage(Entity entity,DamageSource source,float amount,Operation<Boolean> original){
        return BerryTrip.berryDamage(entity,source,amount,()->original.call(entity,source,amount));
    }
}
