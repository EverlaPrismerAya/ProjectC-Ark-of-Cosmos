package net.everla.projectc.arkofcosmos.mixin;

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
    public void arkofcosmos$setProjectileHealth(float health) {
        var self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        // RevelationFix renames its health field at launch; resolve by the stable Auto type.
        for (Class<?> type = self.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals("com.mega.revelationfix.util.java.Auto")) continue;
                try {
                    field.setAccessible(true);
                    Class<?> auto = field.getType();
                    String[] tokens = (String[]) auto.getField("VRF").get(null);
                    auto.getMethod("operate", Object[].class).invoke(field.get(self),
                            (Object) new Object[]{health, tokens[2]});
                    return;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Could not update Apollyon's native health", failure);
                }
            }
        }
        // Goety Revelation without RevelationFix uses vanilla synchronized health.
        self.getEntityData().set(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID, health);
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
