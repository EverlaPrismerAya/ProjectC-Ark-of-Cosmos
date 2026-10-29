package net.everla.projectc.arkofcosmos.combat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

/** A synchronized health authority shared by every living target hit by the Ark. */
public final class ArkDamage {
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final MethodHandle DIE = method(LivingEntity.class, "m_6667_", DamageSource.class, true);
    private static final MethodHandle PLAYER_DIE = method(ServerPlayer.class, "m_6667_", DamageSource.class, true);
    private static final MethodHandle REMOVE = method(Entity.class, "m_142687_", Entity.RemovalReason.class, true);
    private static final MethodHandle BASE_LOOT = method(LivingEntity.class, "m_6668_", DamageSource.class, true);
    private static final MethodHandle NORMAL_LOOT = method(LivingEntity.class, "m_6668_", DamageSource.class, false);
    private static final ThreadLocal<LivingEntity> FINISHING = new ThreadLocal<>();
    private static final ResourceLocation TARGET_DUMMY = new ResourceLocation("dummmmmmy", "target_dummy");
    private static final ThreadLocal<OrdinaryHit> ORDINARY_HIT = new ThreadLocal<>();
    private record OrdinaryHit(LivingEntity target, DamageSource source, float minimum) {}

    private ArkDamage() {}
    public static void bootstrap() {}

    public static boolean protectedPlayer(Object entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    public static void define(LivingEntity entity) { entity.getEntityData().define(HEALTH, -1f); }

    public static void restore(LivingEntity entity, float health) {
        if (!protectedPlayer(entity) && !ordinaryTarget(entity) && Float.isFinite(health) && health >= 0)
            entity.getEntityData().set(HEALTH, Math.min(health, entity.getMaxHealth()));
    }

    // Object signatures keep the launch plugin independent of game class loading.
    public static float health(Object object) {
        if (!(object instanceof LivingEntity entity)) return -1;
        Float value = entity.getEntityData().get(HEALTH);
        return value == null || value < 0 || protectedPlayer(entity) || ordinaryTarget(entity) ? -1 : value;
    }

    public static int alive(Object object) {
        float value = health(object);
        return value < 0 ? -1 : value > 0 && !((Entity)object).isRemoved() ? 1 : 0;
    }

    public static int dead(Object object) {
        float value = health(object);
        return value < 0 ? -1 : value <= 0 ? 1 : 0;
    }

    public static boolean setHealth(Object object, float value) {
        if (health(object) < 0) return false;
        if (finishing(object)) return true;
        if (Float.isFinite(value)) {
            LivingEntity entity = (LivingEntity)object;
            entity.getEntityData().set(HEALTH, Math.max(0, Math.min(value, entity.getMaxHealth())));
        }
        return true;
    }

    public static boolean finishing(Object object) { return FINISHING.get() == object; }

    /** Immediate removal cannot wait for a subclass's delayed death-loot dispatcher. */
    public static void deathLoot(LivingEntity target, DamageSource source) {
        try {
            (finishing(target) ? BASE_LOOT : NORMAL_LOOT).invoke(target, source);
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not drop death loot", failure);
        }
    }

    private static boolean ordinaryTarget(LivingEntity target) {
        return TARGET_DUMMY.equals(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()));
    }

    /** Run only inside this Ark hit; unrelated hits and nested attacks keep their own amounts. */
    public static float ordinaryMinimum(LivingEntity target, DamageSource source, float amount) {
        OrdinaryHit hit = ORDINARY_HIT.get();
        return hit != null && hit.target == target && hit.source == source ? Math.max(amount, hit.minimum) : amount;
    }

    private static boolean ordinaryHurt(LivingEntity target, DamageSource source, float amount) {
        OrdinaryHit previous = ORDINARY_HIT.get();
        ORDINARY_HIT.set(new OrdinaryHit(target, source, amount * .35f));
        try {
            return target.hurt(source, amount);
        } finally {
            if (previous == null) ORDINARY_HIT.remove();
            else ORDINARY_HIT.set(previous);
        }
    }

    private static float reducedDamage(LivingEntity target, float amount) {
        float reduced = CombatRules.getDamageAfterAbsorb(amount, target.getArmorValue(),
                (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) reduced *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * .2f);
        return Math.max(amount * .35f, reduced);
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (target.level().isClientSide || protectedPlayer(target) || target.isRemoved()
                || !Float.isFinite(amount) || amount <= 0 || !target.isAlive()) return false;
        if (ordinaryTarget(target)) return ordinaryHurt(target, source, amount);
        float before = target.getHealth();
        if (!Float.isFinite(before) || before <= 0) return false;
        amount = reducedDamage(target, amount);
        float after = Math.max(0, before - amount);
        target.getEntityData().set(HEALTH, after);
        target.getCombatTracker().recordDamage(source, Math.min(before, amount));
        if (source.getEntity() instanceof LivingEntity attacker) target.setLastHurtByMob(attacker);
        if (source.getEntity() instanceof Player attacker) {
            target.setLastHurtByPlayer(attacker);
            target.lastHurtByPlayerTime = 100;
        }
        target.hurtTime = target.hurtDuration = 10;
        target.level().broadcastDamageEvent(target, source);
        if (after == 0) {
            LivingEntity previous = FINISHING.get();
            FINISHING.set(target);
            try {
                // Invoke the vanilla implementations, without dispatching to subclass death/removal guards.
                if (target instanceof ServerPlayer) PLAYER_DIE.invoke(target, source);
                else DIE.invoke(target, source);
                if (!(target instanceof Player)) {
                    target.getEntityData().set(HEALTH, 0f);
                    REMOVE.invoke(target, Entity.RemovalReason.KILLED);
                }
            } catch (Throwable failure) {
                throw new IllegalStateException("Could not finish Ark damage", failure);
            } finally {
                if (previous == null) FINISHING.remove();
                else FINISHING.set(previous);
            }
        }
        return true;
    }

    private static MethodHandle method(Class<?> owner, String srgName, Class<?> argument, boolean exact) {
        try {
            String name = ObfuscationReflectionHelper.remapName(INameMappingService.Domain.METHOD, srgName);
            var lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            var type = MethodType.methodType(void.class, argument);
            return exact ? lookup.findSpecial(owner, name, type, owner) : lookup.findVirtual(owner, name, type);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
