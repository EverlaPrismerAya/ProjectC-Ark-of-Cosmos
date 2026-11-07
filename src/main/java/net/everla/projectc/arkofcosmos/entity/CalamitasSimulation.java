package net.everla.projectc.arkofcosmos.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Calamitas-only source clock. Native entity ticks still handle vanilla health,
 * tracking and death; authoritative attack AI runs here in three ordered frames.
 * NPC shots and recursively derived projectiles receive their updates in the same
 * source frame, never an accidental full Minecraft tick at creation. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class CalamitasSimulation {
    private CalamitasSimulation() {}
    private record ProjectileFrame(UUID owner, ArrayDeque<CalamitasAttackEntity> queue, int phase) {}
    private static final ThreadLocal<ProjectileFrame> PROJECTILES = new ThreadLocal<>();
    static int projectilePhase() { var frame = PROJECTILES.get(); return frame == null ? 2 : frame.phase; }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            SupremeCalamitasEntity.forgetLevel(level);
            net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary.forgetLevel(level);
        }
    }

    static boolean managed(ServerLevel level, UUID owner) {
        return owner != null && level.getEntity(owner) instanceof SupremeCalamitasEntity;
    }
    /** Called only after a successful native join. During the projectile pass,
     * newly split darts/slash trails/mini fists join the current source frame. */
    static void joined(CalamitasAttackEntity attack) {
        var frame = PROJECTILES.get();
        if (frame != null && frame.owner.equals(attack.ownerUUID())) frame.queue.addLast(attack);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED) return;
        var bosses = new ArrayList<SupremeCalamitasEntity>();
        for (Entity entity : level.getAllEntities())
            if (entity instanceof SupremeCalamitasEntity boss && boss.isAlive() && !boss.isRemoved()) bosses.add(boss);
        bosses.sort(Comparator.comparingInt(Entity::getId));
        var updated = new LinkedHashSet<Entity>();
        for (int frame = 0; frame < 3; frame++) {
            var active = new ArrayList<SupremeCalamitasEntity>();
            for (var boss : bosses) if (!boss.isRemoved() && boss.isAlive()) {
                boolean advanced = boss.sourceStep();
                updated.add(boss);
                if (advanced && CalamitasBattleData.get(level).active(boss.getUUID())) active.add(boss);
            }
            for (var boss : active) {
                var members = members(level, boss.getUUID(), false);
                // The head alone advances all worm body/arm poses. Members created
                // by the boss this frame are already in this snapshot.
                for (Entity entity : members) if (!entity.isRemoved()) {
                    if (entity instanceof CalamitasBrotherEntity brother) brother.sourceStep();
                    else if (entity instanceof CalamitasMinionEntity minion) minion.sourceStep(frame);
                    else if (entity instanceof CalamitasSepulcherEntity worm) worm.sourceStep(frame);
                    updated.add(entity);
                }
            }
            for (var boss : active) {
                // Snapshot after NPC AI so their shots update in this frame too.
                var queue = new ArrayDeque<CalamitasAttackEntity>();
                for (Entity entity : members(level, boss.getUUID(), true))
                    if (entity instanceof CalamitasAttackEntity attack && !attack.isRemoved()) queue.addLast(attack);
                PROJECTILES.set(new ProjectileFrame(boss.getUUID(), queue, frame));
                try {
                    while (!queue.isEmpty()) {
                        var attack = queue.removeFirst();
                        if (!attack.isRemoved()) {
                            attack.sourceStep();
                            updated.add(attack);
                        }
                    }
                } finally { PROJECTILES.remove(); }
            }
        }
        // Keep simulation, collision and child-spawn order at 60 Hz, but build
        // only the final 20 Hz metadata snapshot. Spawn snapshots stay immediate.
        // A head can create and pose its segments after this frame's NPC snapshot.
        for (var boss : bosses) for (var id : CalamitasBattleData.get(level).members(boss.getUUID(), false)) {
            Entity entity = level.getEntity(id);
            if (entity != null) updated.add(entity);
        }
        for (var entity : updated) if (!entity.isRemoved()) {
            if (entity instanceof CalamitasAttackEntity attack) attack.publish();
            else if (entity instanceof SupremeCalamitasEntity boss) boss.publish();
            else if (entity instanceof CalamitasBrotherEntity brother) brother.publish();
            else if (entity instanceof CalamitasMinionEntity minion) minion.publish();
            else if (entity instanceof CalamitasSepulcherEntity worm) worm.publish();
        }
        for (var boss : bosses) if (!boss.isRemoved()) boss.sendCastSparks();
        net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary.syncCarried(level);
    }
    private static ArrayList<Entity> members(ServerLevel level, UUID owner, boolean projectiles) {
        var entities = new ArrayList<Entity>();
        for (UUID id : CalamitasBattleData.get(level).members(owner, projectiles)) {
            Entity entity = level.getEntity(id);
            if (entity != null) entities.add(entity);
        }
        entities.sort(Comparator.comparingInt(Entity::getId));
        return entities;
    }
}
