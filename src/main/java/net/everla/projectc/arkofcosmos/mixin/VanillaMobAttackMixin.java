package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.everla.projectc.arkofcosmos.item.MaceItem;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Mob.class)
public abstract class VanillaMobAttackMixin {
    @Redirect(method="doHurtTarget",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean maceHit(Entity target,DamageSource source,float damage) {
        Mob attacker=(Mob)(Object)this;var stack=attacker.getMainHandItem();
        if(!(stack.getItem() instanceof MaceItem))return target.hurt(source,damage);
        if(MaceCombat.canSmash(attacker)){damage+=MaceCombat.bonus(attacker.fallDistance,VanillaCombat.enchant(stack,VanillaCombat.DENSITY));source=VanillaCombat.source(attacker,"mace_smash");}
        boolean success=target.hurt(source,damage);
        if(success&&target instanceof LivingEntity living)stack.getItem().hurtEnemy(stack,living,attacker);
        return success;
    }
}
