package net.everla.projectc.arkofcosmos.mixin.entity.combat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Replaces method ATs: widening overridden protected methods breaks ForgeGradle's Minecraft recompile. */
@Mixin(LivingEntity.class)
public interface LivingEntitySoundInvoker {
    @Invoker("getDeathSound")
    SoundEvent arkofcosmos$getDeathSound();

    @Invoker("getSoundVolume")
    float arkofcosmos$getSoundVolume();
}
