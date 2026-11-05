package net.everla.projectc.arkofcosmos.mixin.client.berryTrip;
import net.everla.projectc.arkofcosmos.client.FourthCalamityRenderer;
import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class BerryTripPlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void berryTrip$pose(LivingEntity e,float walk,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(BerryTrip.tripped(e))FourthCalamityRenderer.pronePose((PlayerModel<?>)(Object)this);
    }
}
