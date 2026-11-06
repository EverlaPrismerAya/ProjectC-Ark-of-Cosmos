package net.everla.projectc.arkofcosmos.mixin.client.calamitas;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve source drone highs and paired-fist echo lows outside vanilla pitch limits. */
@Mixin(SoundEngine.class)
public abstract class CalamitasDronePitchMixin {
    @Unique private static final ResourceLocation DRONE = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "calamitas_vortex_drone");
    @Unique private static final ResourceLocation ECHO = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "calamitas_brother_echo");
    @ModifyReturnValue(method="calculatePitch", at=@At("RETURN"))
    private float ark$calamitasDronePitch(float original, SoundInstance sound) {
        return (DRONE.equals(sound.getLocation()) || ECHO.equals(sound.getLocation())) && Float.isFinite(sound.getPitch()) && sound.getPitch() > 0
                ? sound.getPitch() : original;
    }
}
