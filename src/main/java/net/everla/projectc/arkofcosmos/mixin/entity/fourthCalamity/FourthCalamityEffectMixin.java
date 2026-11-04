package net.everla.projectc.arkofcosmos.mixin.entity.fourthCalamity;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEffect.class)
public abstract class FourthCalamityEffectMixin {
    @Inject(method="applyInstantenousEffect",at=@At("HEAD"),cancellable=true)
    private void fourthNoInstantEffect(Entity source,Entity owner,LivingEntity target,int amplifier,double strength,CallbackInfo ci) {
        if(target instanceof FourthCalamityEntity)ci.cancel();
    }
}
