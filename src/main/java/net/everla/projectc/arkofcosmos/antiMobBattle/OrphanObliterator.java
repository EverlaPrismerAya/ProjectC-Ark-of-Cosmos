package net.everla.projectc.arkofcosmos.antiMobBattle;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import net.everla.projectc.arkofcosmos.antiMobBattle.mixin.EntityAccess;
import net.everla.projectc.arkofcosmos.antiMobBattle.mixin.EntityDataAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Independent erasure policy; shares only the vanilla death dispatcher with weapon damage. */
@Mod.EventBusSubscriber(modid = "arkofcosmos")
public final class OrphanObliterator {
    private static final UUID MAX_HEALTH = UUID.fromString("f69a56cd-31c0-41aa-9064-e00b30f1e8e2");
    private static final Set<Entity> PROCESSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Pending> PENDING = new ArrayList<>();
    // Locks belong to this entity instance, never to a UUID shared with a respawned player.
    private static final ReferenceQueue<Object> RETIRED = new ReferenceQueue<>();
    private static final Set<InstanceKey> TERMINATED = new HashSet<>();
    private static final class InstanceKey extends WeakReference<Object> {
        private final int hash;
        InstanceKey(Object target) { super(target, RETIRED); hash = System.identityHashCode(target); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof InstanceKey key && get() != null && get() == key.get();
        }
    }
    private record Pending(ServerLevel level, LivingEntity target, long tick, List<Runnable> writes) {}
    private record Candidate(double error, Runnable write) {}
    private OrphanObliterator() {}

    public static synchronized boolean terminated(Object target) {
        for (var key = RETIRED.poll(); key != null; key = RETIRED.poll()) TERMINATED.remove(key);
        return target != null && !TERMINATED.isEmpty() && TERMINATED.contains(new InstanceKey(target));
    }

    private static synchronized void terminate(LivingEntity target) { TERMINATED.add(new InstanceKey(target)); }

    public static void obliterate(LivingEntity target, LivingEntity attacker) {
        obliterate(target, attacker, false);
    }

    public static void obliterate(LivingEntity target, LivingEntity attacker, boolean procedureSword) {
        if (!(target.level() instanceof ServerLevel level) || target instanceof Player && !procedureSword || target.isRemoved() || terminated(target)) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Obliteration requires the server thread");
        if (!PROCESSING.add(target)) return;
        try {
            stage(target, "notification", () -> AntiMobBattleNetwork.popup(target, procedureSword));
            if (target instanceof EnderDragon dragon) {
                // A freshly loaded dragon may not have bound itself to its fight yet.
                var fight = level.getDragonFight();
                if (dragon.getDragonFight() == null && fight != null && dragon.getUUID().equals(fight.getDragonUUID())) {
                    dragon.setDragonFight(fight);
                }
                terminate(dragon);
                dragon.kill(); // Vanilla instant kill also completes the tracked dragon fight.
                return;
            }
            // Capture before any zeroing: otherwise the subsequent search would have no positive reference.
            List<Runnable> writes = new ArrayList<>();
            stage(target, "health field search", () -> writes.addAll(healthFields(target)));
            terminate(target);
            stage(target, "native health", () -> rawHealth(target, .1f));
            stage(target, "native damage", () -> {
                target.setInvulnerable(false);
                target.invulnerableTime = 0;
                target.hurt(target.damageSources().genericKill(), Float.MAX_VALUE / 2);
            });
            stage(target, "NBT health", () -> {
                CompoundTag data = new CompoundTag();
                target.addAdditionalSaveData(data);
                data.putFloat("Health", 0);
                target.readAdditionalSaveData(data);
            });
            stage(target, "maximum health", () -> {
                var attribute = target.getAttribute(Attributes.MAX_HEALTH);
                if (attribute != null && !(target instanceof Player)) {
                    attribute.removeModifier(MAX_HEALTH);
                    attribute.addTransientModifier(new AttributeModifier(MAX_HEALTH, "antiMobBattle", -attribute.getValue(), AttributeModifier.Operation.ADDITION));
                }
            });
            for (Runnable write : writes) stage(target, "custom health", write);
            stage(target, "final health", () -> rawHealth(target, 0));
            // Bypass virtual death guards and cancellable Forge death events. Players retain
            // their connection and world indexes so vanilla can replace them on respawn.
            stage(target, "forced death", () -> net.everla.projectc.arkofcosmos.combat.ArkDamage.finishObliteration(target));
            stage(target, "health synchronization", () -> {
                var values = target.getEntityData().getNonDefaultValues();
                if (values != null) level.getChunkSource().broadcastAndSend(target,
                        new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(target.getId(), values));
                if (target instanceof net.minecraft.server.level.ServerPlayer player)
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                            0, player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            });
            if (!(target instanceof Player)) remove(level, target);
            // Repair backing fields during the death window; permanent getter/setter locks
            // use weak instance keys and require no background thread or retained field closures.
            PENDING.add(new Pending(level, target, level.getGameTime() + 40, writes));
        } finally {
            PROCESSING.remove(target);
        }
    }

    private static void rawHealth(LivingEntity target, float value) {
        var data = (EntityDataAccess)target.getEntityData();
        var item = data.antiMobBattle$items().get(LivingEntity.DATA_HEALTH_ID.getId());
        put(item, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void put(SynchedEntityData.DataItem item, Object value) {
        item.setValue(value);
        item.setDirty(true);
    }

    private static List<Runnable> healthFields(LivingEntity target) {
        double[] references = {target.getHealth(), target.getMaxHealth(), target.getEntityData().get(LivingEntity.DATA_HEALTH_ID)};
        var candidates = new ArrayList<Candidate>();
        for (var item : ((EntityDataAccess)target.getEntityData()).antiMobBattle$items().values()) {
            Object value = item.getValue();
            double error = match(value, references);
            if (error <= .05) candidates.add(new Candidate(error, () -> put(item, zero(value))));
        }
        collect(target, true, references, candidates);
        candidates.sort(Comparator.comparingDouble(Candidate::error));
        var writes = new ArrayList<Runnable>();
        int close = 0;
        for (Candidate candidate : candidates) {
            if (candidate.error <= .001 || close++ < 5) writes.add(candidate.write);
        }
        return writes;
    }

    private static void collect(Object object, boolean nested, double[] references, List<Candidate> candidates) {
        // Keep vanilla navigation, coordinates, IDs and timers outside the heuristic search.
        for (Class<?> type = object.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (type.getName().startsWith("net.minecraft.")) continue;
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (name.contains("uuid") || name.equals("id") || name.contains("tick") || name.contains("position") || name.contains("cooldown")) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    double error = match(value, references);
                    if (error <= .05 && !Modifier.isFinal(field.getModifiers())) candidates.add(new Candidate(error, () -> {
                        try { field.set(object, zero(value)); }
                        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
                    }));
                    else if (nested && value != null && !(value instanceof Entity) && !(value instanceof Number)
                            && (name.contains("health") || name.contains("hp") || name.contains("life") || name.contains("combat") || name.contains("progress"))
                            && !value.getClass().getName().startsWith("java.")) collect(value, false, references, candidates);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Some third-party containers cannot be inspected; direct removal remains mandatory.
                }
            }
        }
    }

    private static double match(Object value, double[] references) {
        if (!(value instanceof Float || value instanceof Double || value instanceof Integer)) return Double.POSITIVE_INFINITY;
        double number = ((Number)value).doubleValue(), error = Double.POSITIVE_INFINITY;
        if (!Double.isFinite(number) || number <= 0) return error;
        for (double reference : references) if (Double.isFinite(reference) && reference > 0)
            error = Math.min(error, Math.abs(number - reference) / Math.max(1, Math.abs(reference)));
        return error;
    }

    private static Object zero(Object value) {
        if (value instanceof Float) return 0f;
        if (value instanceof Double) return 0d;
        return 0;
    }

    private static void remove(ServerLevel level, LivingEntity target) {
        stage(target, "entity removal", () -> {
            target.stopRiding();
            target.ejectPassengers();
            target.remove(Entity.RemovalReason.KILLED);
        });
        // Bypass a subclass remove override and cancellable setRemoved injections. The real level
        // callback retires the section, UUID/ID indexes, ticking, tracking, navigation and parts.
        stage(target, "level removal callback", () -> {
            var access = (EntityAccess)target;
            access.antiMobBattle$removalReason(Entity.RemovalReason.KILLED);
            if (level.getEntity(target.getUUID()) == target)
                access.antiMobBattle$levelCallback().onRemove(Entity.RemovalReason.KILLED);
        });
        stage(target, "capability retirement", target::invalidateCaps);
        stage(target, "disable updates", () -> target.canUpdate(false));
        stage(target, "client removal", () -> {
            var ids = new ArrayList<Integer>(); ids.add(target.getId());
            if (target.isMultipartEntity()) for (var part : target.getParts()) ids.add(part.getId());
            var packet = new ClientboundRemoveEntitiesPacket(ids.stream().mapToInt(Integer::intValue).toArray());
            for (var player : level.players()) player.connection.send(packet);
        });
    }

    private static void stage(Entity target, String stage, Runnable action) {
        try { action.run(); }
        catch (Exception | LinkageError failure) {
            LogUtils.getLogger().warn("Obliteration {} failed for {} ({})", stage, target.getType(), target.getUUID(), failure);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var iterator = PENDING.iterator(); iterator.hasNext();) {
            var pending = iterator.next();
            if (pending.target instanceof Player && pending.level.getEntity(pending.target.getUUID()) != pending.target
                    || pending.level.getGameTime() > pending.tick) { iterator.remove(); continue; }
            for (Runnable write : pending.writes) stage(pending.target, "health follow-up", write);
            stage(pending.target, "final health follow-up", () -> rawHealth(pending.target, 0));
            if (!(pending.target instanceof Player) && pending.level.getEntity(pending.target.getUUID()) == pending.target) remove(pending.level, pending.target);
        }
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) { PENDING.clear(); PROCESSING.clear(); TERMINATED.clear(); }
}
