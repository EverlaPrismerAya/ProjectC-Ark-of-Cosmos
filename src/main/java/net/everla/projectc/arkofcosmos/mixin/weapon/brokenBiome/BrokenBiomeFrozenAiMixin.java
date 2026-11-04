package net.everla.projectc.arkofcosmos.mixin.weapon.brokenBiome;
import net.everla.projectc.arkofcosmos.combat.BrokenBiomeFreeze;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mob.class)
public abstract class BrokenBiomeFrozenAiMixin {
 @Inject(method="serverAiStep",at=@At("HEAD"),cancellable=true)
 private void brokenBiome$ai(CallbackInfo ci){if(BrokenBiomeFreeze.frozen((Mob)(Object)this)){((Mob)(Object)this).setJumping(false);ci.cancel();}}
}
