package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Default weapon damage: normal hurt/death hooks, no vanilla hit cooldown, and a 35% floor. */
public final class WeaponDamage {
    private record Hit(LivingEntity target, DamageSource source, float minimum) {}
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();
    private static final java.util.Map<Player, Long> BOSS_HIT_UNTIL = new java.util.WeakHashMap<>();
    private WeaponDamage() {}

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (target.level().isClientSide || target.isRemoved() || !target.isAlive()
                || !Float.isFinite(amount) || amount <= 0
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        boolean bossHit = target instanceof Player
                && source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
        long now = target.level().getGameTime();
        Long previousUntil = bossHit ? BOSS_HIT_UNTIL.get((Player) target) : null;
        if (previousUntil != null && now < previousUntil) return false;
        // Reserve before hurt() so reentrant damage hooks share the same player cooldown.
        if (bossHit) BOSS_HIT_UNTIL.put((Player) target, now + 5);
        boolean success = false;
        Hit previous = CURRENT.get();
        int cooldown = target.invulnerableTime;
        CURRENT.set(new Hit(target, source, amount * .35f));
        try {
            target.invulnerableTime = 0;
            success = target.hurt(source, amount);
            return success;
        } finally {
            if (bossHit && !success) {
                if (previousUntil == null) BOSS_HIT_UNTIL.remove((Player) target);
                else BOSS_HIT_UNTIL.put((Player) target, previousUntil);
            }
            // Preserve protection against unrelated damage, including cancelled or exceptional hits.
            target.invulnerableTime = Math.max(cooldown, target.invulnerableTime);
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static float minimum(LivingEntity target, DamageSource source, float amount) {
        Hit hit = CURRENT.get();
        if (hit == null || hit.target != target || hit.source != source)
            return target instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity && amount>0 ? Math.max(1,amount) : amount;
        float minimum = hit.minimum;
        if (target instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss)
            minimum *= boss.damageMultiplier();
        if(target instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity warden)
            minimum = Math.max(1,minimum * warden.adaptationMultiplier(source));
        return Math.max(amount, minimum);
    }
}
