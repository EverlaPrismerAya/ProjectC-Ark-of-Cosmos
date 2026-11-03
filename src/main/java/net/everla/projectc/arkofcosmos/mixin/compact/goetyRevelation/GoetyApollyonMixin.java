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
    @Unique
    @Override
    public void arkofcosmos$clearWeaponFrames() {
        var self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        self.invulnerableTime = 0;
        try {
            // The same public invulnerability API used by Valettein's native onAttack.
            // Run before Apostle.hurt(), whose early gates precede LivingAttackEvent.
            try {
                self.getClass().getMethod("revelationfix$setCustomInvulTicks", int.class).invoke(self, 0);
                self.getClass().getMethod("revelationfix$setCustomHurtTicks", int.class).invoke(self, 0);
            } catch (NoSuchMethodException absentFix) {
                self.getClass().getMethod("allTitlesApostle_1_20_1$setHitCooldown", int.class).invoke(self, 0);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not apply Valettein's native frame exemption", failure);
        }
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
