package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Default weapon damage: normal hurt/death hooks, no vanilla hit cooldown, and a 35% floor. */
public final class WeaponDamage {
    private record Hit(LivingEntity target, DamageSource source, float minimum) {}
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();
    private WeaponDamage() {}

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (target.level().isClientSide || target.isRemoved() || !target.isAlive()
                || !Float.isFinite(amount) || amount <= 0
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        Hit previous = CURRENT.get();
        int cooldown = target.invulnerableTime;
        CURRENT.set(new Hit(target, source, amount * .35f));
        try {
            target.invulnerableTime = 0;
            return target.hurt(source, amount);
        } finally {
            // Preserve protection against unrelated damage, including cancelled or exceptional hits.
            target.invulnerableTime = Math.max(cooldown, target.invulnerableTime);
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static float minimum(LivingEntity target, DamageSource source, float amount) {
        Hit hit = CURRENT.get();
        if (hit == null || hit.target != target || hit.source != source) return amount;
        float minimum = hit.minimum;
        if (target instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss)
            minimum *= boss.damageMultiplier();
        return Math.max(amount, minimum);
    }
}
