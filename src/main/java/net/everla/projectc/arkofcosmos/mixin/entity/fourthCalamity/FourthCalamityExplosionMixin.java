package net.everla.projectc.arkofcosmos.mixin.entity.fourthCalamity;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Explosion.class)
public abstract class FourthCalamityExplosionMixin {
    @WrapOperation(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean fourthBlastDamage(Entity entity,net.minecraft.world.damagesource.DamageSource source,float amount,Operation<Boolean> original) {
        var boss=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(source);
        // Marked crystals may be detonated by someone other than their owner.
        if(boss==null) {
            Entity direct=((Explosion)(Object)this).getDirectSourceEntity();
            if(direct!=null)boss=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(
                new net.minecraft.world.damagesource.DamageSource(source.typeHolder(),direct,source.getEntity(),source.getSourcePosition()));
        }
        if(boss!=null&&entity!=boss&&entity instanceof net.minecraft.world.entity.LivingEntity target)
            return net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(target,
                new net.minecraft.world.damagesource.DamageSource(source.typeHolder(),((Explosion)(Object)this).getDirectSourceEntity(),boss,source.getSourcePosition()),amount);
        return original.call(entity,source,amount);
    }
    @WrapOperation(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/enchantment/ProtectionEnchantment;getExplosionKnockbackAfterDampener(Lnet/minecraft/world/entity/LivingEntity;D)D"))
    private double fourthBlastKnockback(net.minecraft.world.entity.LivingEntity entity,double strength,Operation<Double> original) {
        Explosion explosion=(Explosion)(Object)this;
        var source=explosion.getDamageSource();
        var boss=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(
            new net.minecraft.world.damagesource.DamageSource(source.typeHolder(),explosion.getDirectSourceEntity(),source.getEntity(),source.getSourcePosition()));
        return boss!=null&&entity!=boss?strength:
            entity instanceof net.everla.projectc.arkofcosmos.combat.SatsukiBoss?0:original.call(entity,strength);
    }
    @WrapWithCondition(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean fourthNoBlastKnockback(Entity entity,Vec3 velocity) {
        return !(entity instanceof FourthCalamityEntity);
    }
}
