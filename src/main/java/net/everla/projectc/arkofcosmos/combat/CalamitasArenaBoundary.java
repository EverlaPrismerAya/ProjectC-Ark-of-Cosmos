package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.calamity.CalamitasArenaCollision;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Only Calamitas arenas affect survival players; ordinary mobs and other bosses keep native movement. */
public final class CalamitasArenaBoundary {
    private CalamitasArenaBoundary() {}
    private record Carried(ServerLevel level, Vec3 before) {}
    private static final java.util.Map<ServerPlayer, Carried> CARRIED = new java.util.HashMap<>();

    private static boolean participates(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** Moving walls carry the whole player body directly, intentionally through blocks.
     * Solve all axes together so two/three simultaneous walls cannot fight each other. */
    public static AABB shrink(SupremeCalamitasEntity boss, AABB previous, AABB proposed) {
        if (!(boss.level() instanceof ServerLevel level)) return proposed;
        var participatingBounds = previous.inflate(1e-7);
        for (var player : level.players()) {
            if (!participates(player) || !participatingBounds.intersects(player.getBoundingBox())) continue;
            Vec3 correction = CalamitasArenaCollision.correction(proposed, player.getBoundingBox());
            if (correction.lengthSqr() > 1e-16) {
                Vec3 before = player.position();
                player.setPos(before.add(correction));
                if (player.position().distanceToSqr(before) > 1e-16) CARRIED.putIfAbsent(player, new Carried(level, before));
            }
        }
        return proposed;
    }

    /** One native reconciliation per carried player per MC tick, after all three source steps.
     * Ordinary wall contact does not issue position corrections. */
    public static void syncCarried(ServerLevel level) {
        var iterator = CARRIED.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var player = entry.getKey();
            if (entry.getValue().level() != level) continue;
            iterator.remove();
            if (player.level() != level || player.isRemoved() || !player.isAlive()
                    || player.position().distanceToSqr(entry.getValue().before()) < 1e-16) continue;
            if (player.connection != null) player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    public static void forgetLevel(net.minecraft.world.level.Level level) {
        CARRIED.entrySet().removeIf(entry -> entry.getValue().level() == level || entry.getKey().isRemoved());
    }

    public static Vec3 clip(Entity entity, Vec3 movement) {
        if (!(entity instanceof Player player) || !participates(player)) return movement;
        var body = entity.getBoundingBox();
        for (var boss : SupremeCalamitasEntity.loaded(entity.level())) {
            var bounds = boss.arenaBounds();
            // Include exact face contact, but do not pull remote observers or teleported players into an arena.
            if (bounds != null && bounds.inflate(1e-7).intersects(body))
                movement = CalamitasArenaCollision.clip(bounds, body, movement);
        }
        return movement;
    }
}
