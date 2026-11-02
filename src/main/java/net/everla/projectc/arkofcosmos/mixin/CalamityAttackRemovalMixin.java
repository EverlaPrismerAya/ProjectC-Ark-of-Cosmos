package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.CalamityAttackGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CalamityAttackRemovalMixin {
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void fourthRemoval(Entity.RemovalReason reason,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity boss&&boss.protectRemoval(reason)
                ||CalamityAttackGuard.blockRemoval(entity,reason))ci.cancel();
    }
    @Inject(method="setRemoved",at=@At("HEAD"),cancellable=true)
    private void fourthFinalRemoval(Entity.RemovalReason reason,CallbackInfo ci) {
        if((Object)this instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity boss&&boss.protectRemoval(reason))ci.cancel();
    }
}
