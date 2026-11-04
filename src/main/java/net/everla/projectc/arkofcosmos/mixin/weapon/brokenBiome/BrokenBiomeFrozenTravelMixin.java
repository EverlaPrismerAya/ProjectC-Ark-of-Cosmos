package net.everla.projectc.arkofcosmos.mixin.weapon.brokenBiome;
import net.everla.projectc.arkofcosmos.combat.BrokenBiomeFreeze;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LivingEntity.class)
public abstract class BrokenBiomeFrozenTravelMixin {
 @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
 private Vec3 brokenBiome$input(Vec3 input){return BrokenBiomeFreeze.frozen((LivingEntity)(Object)this)?Vec3.ZERO:input;}
 @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
 private void brokenBiome$jump(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(BrokenBiomeFreeze.frozen((LivingEntity)(Object)this))ci.cancel();}
}

