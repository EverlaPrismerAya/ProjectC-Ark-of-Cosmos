package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Recognize both wand calls and the team target goals without a hard mod dependency. */
final class MobBattleTargeting {
    private static final StackWalker CALLERS = StackWalker.getInstance();
    private MobBattleTargeting() {}

    static boolean reject(LivingEntity boss, LivingEntity target, Runnable retreat) {
        if (boss.level().isClientSide || target == null || target instanceof Player) return false;
        if (!CALLERS.walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().startsWith("io.github.flemmli97.mobbattle.")))) return false;
        try {
            ArkDamage.obliterate(target, boss);
        } finally {
            retreat.run();
        }
        return true;
    }
}
