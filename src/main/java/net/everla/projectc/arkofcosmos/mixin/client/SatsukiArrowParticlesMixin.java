package net.everla.projectc.arkofcosmos.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.everla.projectc.arkofcosmos.client.SatsukiArrowVisuals;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Satsuki's disguised arrows show only Bejeweled gem dust: drop vanilla crit/bubble particles from AbstractArrow.tick. */
@Mixin(AbstractArrow.class)
public abstract class SatsukiArrowParticlesMixin {
    @WrapWithCondition(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean ark$hideSatsukiArrowParticles(Level level,ParticleOptions particle,double x,double y,double z,double vx,double vy,double vz){
        return !level.isClientSide||!SatsukiArrowVisuals.hides((AbstractArrow)(Object)this);
    }
}
