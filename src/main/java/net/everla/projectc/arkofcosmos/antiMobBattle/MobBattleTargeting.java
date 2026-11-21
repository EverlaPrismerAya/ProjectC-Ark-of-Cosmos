package net.everla.projectc.arkofcosmos.antiMobBattle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Only punish assignments originating in Mob Battle, not ordinary gameplay targeting. */
public final class MobBattleTargeting {
    private static final StackWalker CALLERS = StackWalker.getInstance();
    private MobBattleTargeting() {}

    public static boolean reject(LivingEntity boss, LivingEntity target, Runnable retreat) {
        if (boss.level().isClientSide || target == null || target instanceof Player) return false;
        if (BossRetreat.active(boss)) return true;
        if (!CALLERS.walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().startsWith("io.github.flemmli97.mobbattle.")))) return false;
        try {
            OrphanObliterator.obliterate(target, boss);
        } finally {
            retreat.run();
        }
        return true;
    }
}
