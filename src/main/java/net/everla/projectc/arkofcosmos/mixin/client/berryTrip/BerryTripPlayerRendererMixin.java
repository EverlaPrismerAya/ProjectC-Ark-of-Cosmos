package net.everla.projectc.arkofcosmos.mixin.client.berryTrip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.FourthCalamityRenderer;
import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class BerryTripPlayerRendererMixin {
    private static final float RISE_TICKS=4;
    @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",at=@At("TAIL"))
    private void berryTrip$fall(AbstractClientPlayer player,PoseStack pose,float age,float yaw,float partial,CallbackInfo ci) {
        float ticks=BerryTrip.ticks(player,partial);
        // Fourth Calamity's 12-tick prone fall, then a short scramble back up before control returns.
        if(ticks>=0)FourthCalamityRenderer.proneFall(pose,Math.min(ticks/FourthCalamityRenderer.FALL_TICKS,(BerryTrip.DURATION-ticks)/RISE_TICKS));
    }
}
