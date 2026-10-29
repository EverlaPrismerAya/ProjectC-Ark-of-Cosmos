package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
abstract class LivingEntityMixin {
    @Group(name = "ark$deathLoot", min = 1, max = 1)
    @Redirect(method = {"die", "m_6667_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V"), require = 0)
    private void ark$deathLootDev(LivingEntity entity, DamageSource source) { ArkDamage.deathLoot(entity, source); }

    @Group(name = "ark$deathLoot", min = 1, max = 1)
    @Redirect(method = {"die", "m_6667_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;m_6668_(Lnet/minecraft/world/damagesource/DamageSource;)V"), require = 0)
    private void ark$deathLootSrg(LivingEntity entity, DamageSource source) { ArkDamage.deathLoot(entity, source); }

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
