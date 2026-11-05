package net.everla.projectc.arkofcosmos.mixin.client.spear;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LocalPlayer.class)
public abstract class SpearMovementMixin {
    @ModifyExpressionValue(method={"aiStep","canStartSprinting"},at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean spearUseEffects(boolean original) { LocalPlayer player=(LocalPlayer)(Object)this;return original&&!(player.getUseItem().getItem() instanceof SpearItem); }
}
