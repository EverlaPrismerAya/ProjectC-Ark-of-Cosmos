package net.everla.projectc.arkofcosmos.antiMobBattle.mixin;

import net.everla.projectc.arkofcosmos.antiMobBattle.client.ObliterationText;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class FrameLimitMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void antiMobBattle$limit(CallbackInfoReturnable<Integer> result) {
        if (ObliterationText.locksFrames()) result.setReturnValue(20);
    }
}
