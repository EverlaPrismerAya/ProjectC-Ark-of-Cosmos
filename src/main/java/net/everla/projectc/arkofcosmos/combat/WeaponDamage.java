package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Native weapon dispatch and hit context; BossDamage owns the two bosses' damage policy. */
public final class WeaponDamage {
    private record Hit(LivingEntity target, DamageSource source, float minimum) {}
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();
    private static final java.util.Map<LivingEntity, Long> BOSS_HIT_UNTIL = new java.util.WeakHashMap<>();
    private WeaponDamage() {}
    public static boolean handling(LivingEntity target,DamageSource source){
        Hit hit=CURRENT.get();return hit!=null&&hit.target==target&&hit.source==source;
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        amount=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.bossDamage(target,source,amount);
        if(amount<=0)return false;
        // Calamitas summons retain native hurt frames and mitigation even when
        // struck by an Ark weapon; do not install this dispatcher's 35% floor.
        if(target instanceof net.everla.projectc.arkofcosmos.entity.WitchCurseEntity
                || target instanceof net.everla.projectc.arkofcosmos.entity.BrimstoneWitchEntity
                || target instanceof net.everla.projectc.arkofcosmos.entity.CalamitasMinionEntity
                || target instanceof net.everla.projectc.arkofcosmos.entity.CalamitasBrotherEntity
                || target instanceof net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity
                || target instanceof net.everla.projectc.arkofcosmos.entity.CalamitasSepulcherEntity)
            return target.hurt(source,amount);
        // Keep native boss mitigation, but preserve player/servant weapons' hurt-frame bypass.
        if(target instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity) {
            if(!WeaponUser.playerOwnedDamage(source))return target.hurt(source,amount);
            int cooldown=target.invulnerableTime;
            try {
                target.invulnerableTime=0;
                return target.hurt(source,amount);
            } finally { target.invulnerableTime=Math.max(cooldown,target.invulnerableTime); }
        }
        if (target.level().isClientSide || target.isRemoved() || !target.isAlive()
                || !Float.isFinite(amount) || amount <= 0
                || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        boolean mahouHit=source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
        boolean calamitasHit=BossDamage.calamitas(source);
        var fourth=CalamityAttackGuard.damageOwner(source);
        boolean fourthHit=fourth!=null&&target!=fourth;
        if(fourthHit&&fourth.combat.selfDefending())amount*=6;
        boolean bossHit = mahouHit || calamitasHit || fourthHit || target instanceof Player
                && source.getEntity() instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity;
        long now = target.level().getGameTime();
        Long previousUntil = bossHit ? BOSS_HIT_UNTIL.get(target) : null;
        if (previousUntil != null && now < previousUntil) return false;
        // Reserve before dispatch; all Fourth Calamity attack kinds share ten ticks.
        if (bossHit) BOSS_HIT_UNTIL.put(target, now + (fourthHit ? 10 : 5));
        boolean success = false;
        Hit previous = CURRENT.get();
        int cooldown = target.invulnerableTime;
        CURRENT.set(new Hit(target, source, fourthHit ? 0 : net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect.outgoing(source, amount) * .35f));
        try {
            target.invulnerableTime = 0;
            success = fourthHit || mahouHit || calamitasHit ? BossDamage.hurt(target,source,amount) : target.hurt(source, amount);
            if(success&&fourthHit&&!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                &&source.getDirectEntity()==fourth&&!source.is(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","spear"))))
                CalamityAttackGuard.knockback(target,.4,fourth.getX()-target.getX(),fourth.getZ()-target.getZ(),fourth);
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
        float minimum = net.everla.projectc.arkofcosmos.enchant.CurseCombat.modify(target,source,hit.minimum);
        if (target instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss)
            minimum *= boss.damageMultiplier();
        return Math.max(amount, minimum);
    }
}
