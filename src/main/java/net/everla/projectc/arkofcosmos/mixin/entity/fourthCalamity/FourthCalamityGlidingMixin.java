package net.everla.projectc.arkofcosmos.mixin.entity.fourthCalamity;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FourthCalamityGlidingMixin {
    @Inject(method="updateFallFlying",at=@At("HEAD"),cancellable=true)
    private void fourthGliding(CallbackInfo ci) {
        if((Object)this instanceof FourthCalamityEntity boss){boss.updateGlidingFlag();ci.cancel();}
    }
}
