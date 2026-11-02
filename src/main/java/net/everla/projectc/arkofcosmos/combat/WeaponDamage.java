package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Native weapon damage with a 35% floor; Mahou attacks share Ark authority and a five-tick victim cooldown. */
public final class WeaponDamage {
    private record Hit(LivingEntity target, DamageSource source, float minimum) {}
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();
    private static final java.util.Map<LivingEntity, Long> BOSS_HIT_UNTIL = new java.util.WeakHashMap<>();
    private WeaponDamage() {}
    public static boolean handling(LivingEntity target,DamageSource source){
        Hit hit=CURRENT.get();return hit!=null&&hit.target==target&&hit.source==source;
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        // This PvP boss explicitly retains native mitigation and hurt frames, even for Ark tag fallback.
        if(target instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity)return target.hurt(source,amount);
        if (target.level().isClientSide || target.isRemoved() || !target.isAlive()
                || !Float.isFinite(amount) || amount <= 0
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        boolean mahouHit=source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
        boolean bossHit = mahouHit || target instanceof Player
                && source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity;
        long now = target.level().getGameTime();
        Long previousUntil = bossHit ? BOSS_HIT_UNTIL.get(target) : null;
        if (previousUntil != null && now < previousUntil) return false;
        // Reserve before dispatch so all boss attacks share the victim's five-tick cooldown.
        if (bossHit) BOSS_HIT_UNTIL.put(target, now + 5);
        boolean success = false;
        Hit previous = CURRENT.get();
        int cooldown = target.invulnerableTime;
        CURRENT.set(new Hit(target, source, amount * .35f));
        try {
            target.invulnerableTime = 0;
            success = mahouHit ? ArkDamage.hurt(target,source,amount) : target.hurt(source, amount);
            if(success&&mahouHit&&!ElementsCombat.isResonanceDamage(target))BossHealingLock.apply(target,(LivingEntity)source.getEntity());
            return success;
        } finally {
            if (bossHit && !success) {
                if (previousUntil == null) BOSS_HIT_UNTIL.remove(target);
                else BOSS_HIT_UNTIL.put(target, previousUntil);
            }
            // Preserve protection against unrelated damage, including cancelled or exceptional hits.
            target.invulnerableTime = Math.max(cooldown, target.invulnerableTime);
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static float minimum(LivingEntity target, DamageSource source, float amount) {
        if(target instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity)return amount;
        Hit hit = CURRENT.get();
        if (hit == null || hit.target != target || hit.source != source)
            return amount;
        float minimum = hit.minimum;
        if (target instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss)
            minimum *= boss.damageMultiplier();
        return Math.max(amount, minimum);
    }
}
