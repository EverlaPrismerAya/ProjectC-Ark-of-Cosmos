package net.everla.projectc.arkofcosmos.mixin.entity.combat;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.everla.projectc.arkofcosmos.item.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class VanillaLivingCombatMixin {
    @Redirect(method="knockback",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D"))
    private double fourthResistance(LivingEntity victim,net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        if(!(victim instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity)&&attribute==net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE
            &&Boolean.TRUE.equals(net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.FULL_KNOCKBACK.get()))return 0;
        return victim.getAttributeValue(attribute);
    }
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void spearBaseKnockback(LivingEntity victim,double power,double x,double z,DamageSource source,float damage) {
        var key=source.typeHolder().unwrapKey().orElse(null);
        if(key==null||!key.location().toString().equals("arkofcosmos:spear"))victim.knockback(power,x,z);
    }
    @Inject(method="getCurrentSwingDuration",at=@At("HEAD"),cancellable=true)
    private void spearDuration(CallbackInfoReturnable<Integer> ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(self.getItemInHand(self.swingingArm==null?net.minecraft.world.InteractionHand.MAIN_HAND:self.swingingArm).getItem() instanceof SpearItem spear) {
            int duration=(int)(spear.material.duration()*20);
            if(net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(self))duration-=1+net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(self);
            else if(self.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN))duration+=(1+self.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN).getAmplifier())*2;
            ci.setReturnValue(duration);
        }
    }
    @Inject(method="getDamageAfterArmorAbsorb",at=@At("RETURN"),cancellable=true)
    private void breach(DamageSource source,float damage,CallbackInfoReturnable<Float> ci) {
        if(source.getDirectEntity()!=source.getEntity()||!(source.getEntity() instanceof LivingEntity attacker)
            ||!(attacker.getMainHandItem().getItem() instanceof MaceItem)||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)||damage<=0)return;
        var key=source.typeHolder().unwrapKey().orElse(null);
        if(key==null||!java.util.Set.of("minecraft:player_attack","minecraft:mob_attack","minecraft:mob_attack_no_aggro","arkofcosmos:mace_smash").contains(key.location().toString()))return;
        int level=VanillaCombat.enchant(attacker.getMainHandItem(),VanillaCombat.BREACH);if(level==0)return;
        float fraction=1-ci.getReturnValueF()/damage;
        ci.setReturnValue(damage*(1-Mth.clamp(fraction-.15f*level,0,1)));
    }
}
