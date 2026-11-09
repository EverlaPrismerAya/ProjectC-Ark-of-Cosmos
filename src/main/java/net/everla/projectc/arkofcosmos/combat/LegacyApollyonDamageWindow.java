package net.everla.projectc.arkofcosmos.combat;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.mega.revelationfix.common.apollyon.common.AttackDamageChangeHandler;
import com.mega.revelationfix.safe.entity.Apollyon2Interface;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import z1gned.goetyrevelation.config.ModConfig;
import z1gned.goetyrevelation.util.ApollyonAbilityHelper;

/** Backported from RevelationFix 4.9; keeps its damage-window accounting and settlement order. */
public final class LegacyApollyonDamageWindow {
    public interface Access {
        LegacyApollyonDamageWindow ark$damageWindow();
        void ark$startDamageWindow(int ticks);
        void revelaionfix$setHitCooldown(int ticks);
    }
    private final Apostle apostle;
    private final ApollyonAbilityHelper helper;
    private float lastHurt;
    private float lastDirectDamage;
    private double spentDamage;
    private Attack attack;
    private Attack pendingAttack;
    private Attack settlementAttack;
    private HealthWrite healthWrite;

    public LegacyApollyonDamageWindow(Apostle apostle) {
        this.apostle = apostle;
        this.helper = (ApollyonAbilityHelper)apostle;
    }

    @Nullable
    public static LegacyApollyonDamageWindow get(LivingEntity entity) {
        if (!(entity instanceof net.everla.projectc.arkofcosmos.combat.SatsukiBoss) && entity instanceof Access && entity instanceof Apostle) {
            Apostle apostle = (Apostle)entity;
            if (!apostle.level().isClientSide && apostle.isInNether() && ((ApollyonAbilityHelper)apostle).allTitlesApostle_1_20_1$isApollyon()) {
                return ((Access)apostle).ark$damageWindow();
            }
        }
        return null;
    }

    public float getLastHurt() {
        return this.lastHurt;
    }

    public void setLastHurt(float value) {
        this.lastHurt = value;
    }

    public void onCooldownChanged(int previous, int next) {
        if (next <= 0 || previous <= 0) {
            this.lastHurt = 0.0f;
            this.lastDirectDamage = 0.0f;
            this.spentDamage = 0.0;
        }
    }

    public boolean hurt(BooleanSupplier original) {
        Attack current;
        Attack previousAttack = this.attack;
        Attack previousPending = this.pendingAttack;
        Attack previousSettlement = this.settlementAttack;
        HealthWrite previousWrite = this.healthWrite;
        this.attack = current = new Attack();
        this.pendingAttack = null;
        this.settlementAttack = null;
        this.healthWrite = null;
        try {
            boolean bl = original.getAsBoolean() && current.lostHealth;
            return bl;
        }
        finally {
            this.attack = previousAttack;
            this.pendingAttack = previousPending;
            this.settlementAttack = previousSettlement;
            this.healthWrite = previousWrite;
        }
    }

    public void dispatchActuallyHurt(float rawDamage, Runnable original) {
        Attack previous = this.pendingAttack;
        if (this.attack != null) {
            this.attack.rawDamage = rawDamage;
        }
        this.pendingAttack = this.attack;
        try {
            original.run();
        }
        finally {
            this.pendingAttack = previous;
        }
    }

    public void actuallyHurt(Runnable original) {
        Attack previous = this.settlementAttack;
        this.settlementAttack = this.pendingAttack;
        this.pendingAttack = null;
        try {
            original.run();
        }
        finally {
            this.settlementAttack = previous;
        }
    }

    public void writeHealth(float requestedHealth, Runnable original) {
        HealthWrite previous = this.healthWrite;
        this.healthWrite = new HealthWrite(this.settlementAttack, requestedHealth);
        try {
            original.run();
        }
        finally {
            this.healthWrite = previous;
        }
    }

    public HealthChange prepareHealthChange(float before, float requested) {
        double remaining;
        boolean active;
        Attack owner;
        HealthWrite write = this.healthWrite;
        this.healthWrite = null;
        Attack attack = owner = write != null && write.attack == this.attack && Float.compare(write.requestedHealth, requested) == 0 ? write.attack : null;
        if (!(requested < before)) {
            return new HealthChange(before, requested, 0.0f, owner);
        }
        float protectedHealth = ((Apollyon2Interface)this.apostle).revelaionfix$apollyonEC().attackDamageReducer.redirectSetHealth(requested);
        if (!(protectedHealth < before)) {
            return new HealthChange(before, protectedHealth, 0.0f, owner);
        }
        float limit = Math.max(0.0f, ((Double)ModConfig.APOLLYON_HURT_LIMIT.get()).floatValue());
        float candidate = Math.max(0.0f, Math.min(before - protectedHealth, limit));
        boolean bl = active = this.helper.allTitlesApostle_1_20_1$getHitCooldown() > 0;
        float damage = owner != null ? (!active || owner.rawDamage > this.lastHurt ? candidate : 0.0f) : (active ? Math.max(0.0f, candidate - this.lastDirectDamage) : candidate);
        double allowed = Math.min((double)damage, remaining = Math.max(0.0, (double)limit - (active ? this.spentDamage : 0.0)));
        float target = (float)((double)before - allowed);
        if ((double)before - (double)target > allowed) {
            target = Math.nextUp(target);
        }
        return new HealthChange(before, target, candidate, owner);
    }

    public void finishHealthChange(HealthChange change, float after) {
        double loss = (double)change.before - (double)after;
        if (!(change.candidate > 0.0f && change.health < change.before && loss > 0.0)) {
            return;
        }
        if (this.helper.allTitlesApostle_1_20_1$getHitCooldown() <= 0) {
            ((Access)this.apostle).ark$startDamageWindow(AttackDamageChangeHandler.vanillaLimitTime);
        }
        this.spentDamage += loss;
        if (change.owner != null) {
            this.lastHurt = Math.max(this.lastHurt, change.owner.rawDamage);
            change.owner.lostHealth = true;
        } else {
            this.lastDirectDamage = Math.max(this.lastDirectDamage, change.candidate);
        }
    }

    private static final class Attack {
        private float rawDamage;
        private boolean lostHealth;

        private Attack() {
        }
    }

    private record HealthWrite(Attack attack, float requestedHealth) {
    }

    public static final class HealthChange {
        private final float before;
        private final float health;
        private final float candidate;
        private final Attack owner;

        private HealthChange(float before, float health, float candidate, Attack owner) {
            this.before = before;
            this.health = health;
            this.candidate = candidate;
            this.owner = owner;
        }

        public float health() {
            return this.health;
        }
    }
}

