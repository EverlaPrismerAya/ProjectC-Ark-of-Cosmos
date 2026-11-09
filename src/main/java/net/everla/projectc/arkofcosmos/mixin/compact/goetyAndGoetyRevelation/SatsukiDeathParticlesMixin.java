package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import net.everla.projectc.arkofcosmos.combat.SatsukiDeathParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class SatsukiDeathParticlesMixin {
    @Inject(method="sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",at=@At("HEAD"),cancellable=true)
    private <T extends ParticleOptions> void ark$deathParticles(T particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed,CallbackInfoReturnable<Integer> cir){
        if(SatsukiDeathParticles.active()&&BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()).getNamespace().equals("minecraft"))cir.setReturnValue(0);
    }
}
