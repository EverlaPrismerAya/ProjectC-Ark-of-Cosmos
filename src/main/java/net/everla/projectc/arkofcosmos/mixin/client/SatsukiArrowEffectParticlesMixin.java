package net.everla.projectc.arkofcosmos.mixin.client;

import net.everla.projectc.arkofcosmos.client.SatsukiArrowVisuals;
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Potion-effect swirl particles of Satsuki's disguised (tipped) arrows. */
@Mixin(Arrow.class)
public abstract class SatsukiArrowEffectParticlesMixin {
    @Inject(method="makeParticle",at=@At("HEAD"),cancellable=true)
    private void ark$hideSatsukiArrowEffect(int count,CallbackInfo ci){
        if(SatsukiArrowVisuals.hides((Arrow)(Object)this))ci.cancel();
    }
}
