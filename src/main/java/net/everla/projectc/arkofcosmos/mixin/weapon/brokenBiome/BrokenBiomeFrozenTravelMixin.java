package net.everla.projectc.arkofcosmos.mixin.weapon.brokenBiome;
import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.everla.projectc.arkofcosmos.combat.BrokenBiomeFreeze;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LivingEntity.class)
public abstract class BrokenBiomeFrozenTravelMixin {
 @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
 private Vec3 brokenBiome$input(Vec3 input){return brokenBiome$frozen()?Vec3.ZERO:input;}
 @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
 private void brokenBiome$jump(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(brokenBiome$frozen())ci.cancel();}
 // Berry trip shares the freeze movement lock so the client-predicted player also stays put.
 private boolean brokenBiome$frozen(){LivingEntity self=(LivingEntity)(Object)this;return BrokenBiomeFreeze.frozen(self)||BerryTrip.tripped(self);}
}

