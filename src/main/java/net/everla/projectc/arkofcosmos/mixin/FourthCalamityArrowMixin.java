package net.everla.projectc.arkofcosmos.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(AbstractArrow.class)
public abstract class FourthCalamityArrowMixin {
    @Redirect(method="onHitEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean fourthArrowDamage(net.minecraft.world.entity.Entity target,net.minecraft.world.damagesource.DamageSource source,float amount) {
        return net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.hurt(target,source,amount);
    }
    @Redirect(method="onHitEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D"))
    private double fourthArrowResistance(LivingEntity victim,Attribute attribute) {
        if(!(victim instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity)&&attribute==Attributes.KNOCKBACK_RESISTANCE
            &&((AbstractArrow)(Object)this).getOwner() instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity)return 0;
        return victim.getAttributeValue(attribute);
    }
}
