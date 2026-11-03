package net.everla.projectc.arkofcosmos.mixin.compact.goetyRevelation;

import net.everla.projectc.arkofcosmos.combat.ApollyonCompatTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Pseudo;

/** 用于兼容下亚，防止在2.3.6及以后给牢亚前额叶切除 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
@Pseudo
public abstract class GoetyApollyonMixin implements ApollyonCompatTarget {
    // Both native cooldowns must be exempted before hurt() can reach the weapon callbacks.
    @org.spongepowered.asm.mixin.injection.Inject(
            method={"allTitlesApostle_1_20_1$getHitCooldown", "revelaionfix$getHitCooldown"},
            at=@org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable=true, require=0)
    private void arkofcosmos$weaponFrames(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if (net.everla.projectc.arkofcosmos.combat.ArkDamage.apollyonHit(this)) cir.setReturnValue(0);
    }

    @Unique
    @Override
    public boolean arkofcosmos$isNetherApollyon() {
        Entity self = (Entity) (Object) this;
        try {
            return self.level().dimension() == Level.NETHER
                    && (boolean) self.getClass().getMethod("allTitlesApostle_1_20_1$isApollyon").invoke(self);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
