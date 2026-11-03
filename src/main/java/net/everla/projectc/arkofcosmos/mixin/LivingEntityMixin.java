package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
abstract class LivingEntityMixin {
    @Inject(method={"onSyncedDataUpdated","m_7350_"},at=@At("HEAD"))
    private void ark$healingData(net.minecraft.network.syncher.EntityDataAccessor<?> key,CallbackInfo ci){
        net.everla.projectc.arkofcosmos.combat.BossHealingLock.synced((LivingEntity)(Object)this,key);
    }
    @Inject(method = {"hurt", "m_6469_"}, at = @At("HEAD"), cancellable = true)
    private void ark$wardenDamage(DamageSource source,float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir){
        LivingEntity target=(LivingEntity)(Object)this;
          if(target instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity)return;
        if((source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity
                ||net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard.damageOwner(source)!=null)
                &&!net.everla.projectc.arkofcosmos.combat.WeaponDamage.handling(target,source)){
            cir.setReturnValue(net.everla.projectc.arkofcosmos.combat.BossDamage.hurt(target,source,amount));return;
        }
        if(source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity warden){
            if(warden.attacksPaused())cir.setReturnValue(false);
            else if(target instanceof net.minecraft.world.entity.player.Player
                    &&!net.everla.projectc.arkofcosmos.combat.WeaponDamage.handling(target,source))
                cir.setReturnValue(net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(target,source,amount));
        }
    }

    @Redirect(method = "die", remap = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void ark$deathLoot(LivingEntity entity, DamageSource source) { ArkDamage.deathLoot(entity, source); }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void ark$bootstrap(CallbackInfo ci) { ArkDamage.bootstrap(); }

    @Inject(method = {"defineSynchedData", "m_8097_"}, at = @At("TAIL"))
    private void ark$define(CallbackInfo ci) { ArkDamage.define((LivingEntity)(Object)this); }

    @Inject(method = {"addAdditionalSaveData", "m_7380_"}, at = @At("TAIL"))
    private void ark$save(CompoundTag tag, CallbackInfo ci) {
        float health = ArkDamage.health(this);
        if (health >= 0) tag.putFloat("ArkHealth", health);
    }

    @Inject(method = {"readAdditionalSaveData", "m_7378_"}, at = @At("TAIL"))
    private void ark$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("ArkHealth", 99)) {
            LivingEntity entity = (LivingEntity)(Object)this;
            // Activate the authority before applying the normal finite/clamped setter.
            ArkDamage.restore(entity, tag.getFloat("ArkHealth"));
        }
    }
}
