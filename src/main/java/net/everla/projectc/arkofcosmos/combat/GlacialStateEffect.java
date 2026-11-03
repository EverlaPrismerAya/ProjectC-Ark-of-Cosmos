package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Source gState movement damping; three 60 Hz source ticks per Minecraft tick. */
public final class GlacialStateEffect extends MobEffect {
    public GlacialStateEffect(){super(MobEffectCategory.HARMFUL,0xA9CFFF);}
    @Override public boolean isDurationEffectTick(int duration,int amplifier){return true;}
    @Override public void applyEffectTick(LivingEntity target,int amplifier){
        boolean player=target instanceof Player;double drag=Math.pow(player?.5:.9,3),gravity=(player?.05:.025)*9/16;
        var v=target.getDeltaMovement();target.setDeltaMovement(v.x*drag,Math.max(-45/16d,v.y-gravity),v.z*drag);
        target.hurtMarked=true;
    }
}
