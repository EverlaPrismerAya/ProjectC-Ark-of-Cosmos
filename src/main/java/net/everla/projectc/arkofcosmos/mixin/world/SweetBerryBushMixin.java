package net.everla.projectc.arkofcosmos.mixin.world;

import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SweetBerryBushBlock.class)
public abstract class SweetBerryBushMixin {
    @Redirect(method="entityInside",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ark$berryDamage(Entity entity,DamageSource source,float amount){
        return BerryTrip.berryDamage(entity,source,amount);
    }
}
