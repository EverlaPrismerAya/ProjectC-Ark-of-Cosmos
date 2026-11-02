package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Explosion.class)
public abstract class FourthCalamityExplosionMixin {
    @Redirect(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean fourthBlastDamage(Entity entity,net.minecraft.world.damagesource.DamageSource source,float amount) {
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
        return entity.hurt(source,amount);
    }
    @Redirect(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/enchantment/ProtectionEnchantment;getExplosionKnockbackAfterDampener(Lnet/minecraft/world/entity/LivingEntity;D)D"))
    private double fourthBlastKnockback(net.minecraft.world.entity.LivingEntity entity,double strength) {
        Explosion explosion=(Explosion)(Object)this;
        var source=explosion.getDamageSource();
        var boss=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(
            new net.minecraft.world.damagesource.DamageSource(source.typeHolder(),explosion.getDirectSourceEntity(),source.getEntity(),source.getSourcePosition()));
        return boss!=null&&entity!=boss?strength:
            net.minecraft.world.item.enchantment.ProtectionEnchantment.getExplosionKnockbackAfterDampener(entity,strength);
    }
    @Redirect(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void fourthNoBlastKnockback(Entity entity,Vec3 velocity) {
        if(!(entity instanceof FourthCalamityEntity))entity.setDeltaMovement(velocity);
    }
}
