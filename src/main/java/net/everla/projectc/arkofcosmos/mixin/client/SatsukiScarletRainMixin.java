package net.everla.projectc.arkofcosmos.mixin.client;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RF's phase-two Nether scarlet rain is replaced by Satsuki's note circles; other Apollyons keep it. */
@Pseudo
@Mixin(targets="com.mega.revelationfix.safe.level.ClientLevelExpandedContext",remap=false)
public abstract class SatsukiScarletRainMixin {
    @Shadow(remap=false) private Apostle currentNetherApollyon;
    @Inject(method="isNetherScarletRaining",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void ark$noSatsukiRain(CallbackInfoReturnable<Boolean> cir){
        if(currentNetherApollyon instanceof net.everla.projectc.arkofcosmos.combat.SatsukiBoss)cir.setReturnValue(false);
    }
}
