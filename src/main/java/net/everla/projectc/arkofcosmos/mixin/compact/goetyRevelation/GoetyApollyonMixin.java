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
