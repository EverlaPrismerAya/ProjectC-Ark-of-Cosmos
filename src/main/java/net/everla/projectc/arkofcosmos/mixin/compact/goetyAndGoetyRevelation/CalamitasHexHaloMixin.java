package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared exemption for halo effect immunity and Odamane's periodic cleansing. */
@Pseudo
@Mixin(targets = "com.mega.revelationfix.common.config.CommonConfig", remap = false)
public abstract class CalamitasHexHaloMixin {
    @Inject(method = "inBypassEffect", at = @At("HEAD"), cancellable = true)
    private static void arkofcosmos$preserveHex(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect instanceof CalamitasHexEffect) cir.setReturnValue(true);
    }
}
