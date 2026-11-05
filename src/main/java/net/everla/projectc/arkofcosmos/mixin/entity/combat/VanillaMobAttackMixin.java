package net.everla.projectc.arkofcosmos.mixin.entity.combat;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.everla.projectc.arkofcosmos.item.MaceItem;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Mob.class)
public abstract class VanillaMobAttackMixin {
    @WrapOperation(method="doHurtTarget",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void fourthKnockback(LivingEntity target,double strength,double x,double z,Operation<Void> original) {
        net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.knockback(target,strength,x,z,(Mob)(Object)this,()->original.call(target,strength,x,z));
    }
    @WrapOperation(method="doHurtTarget",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean maceHit(Entity target,DamageSource source,float damage,Operation<Boolean> original) {
        Mob attacker=(Mob)(Object)this;var stack=attacker.getMainHandItem();
        if(!(stack.getItem() instanceof MaceItem)) {
            final var s=source;final float d=damage;
            return net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.hurt(target,source,damage,()->original.call(target,s,d));
        }
        if(MaceCombat.canSmash(attacker)){damage+=MaceCombat.bonus(attacker.fallDistance,VanillaCombat.enchant(stack,VanillaCombat.DENSITY));source=VanillaCombat.source(attacker,"mace_smash");}
        final var hitSource=source;final float hitDamage=damage;
        boolean success=net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.hurt(target,source,damage,()->original.call(target,hitSource,hitDamage));
        if(success&&target instanceof LivingEntity living)stack.getItem().hurtEnemy(stack,living,attacker);
        return success;
    }
}
