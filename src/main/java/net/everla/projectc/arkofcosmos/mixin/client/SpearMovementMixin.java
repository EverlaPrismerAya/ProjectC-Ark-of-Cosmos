package net.everla.projectc.arkofcosmos.mixin.client;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LocalPlayer.class)
public abstract class SpearMovementMixin {
    @Redirect(method={"aiStep","canStartSprinting"},at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean spearUseEffects(LocalPlayer player) { return player.isUsingItem()&&!(player.getUseItem().getItem() instanceof SpearItem); }
}
