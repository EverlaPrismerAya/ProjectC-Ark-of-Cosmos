package net.everla.projectc.arkofcosmos.antiMobBattle.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccess {
    @Accessor("removalReason") void antiMobBattle$removalReason(Entity.RemovalReason reason);
    @Accessor("levelCallback") EntityInLevelCallback antiMobBattle$levelCallback();
}
