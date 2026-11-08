package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.calamity.CalamitasArenaCollision;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Survival players and the surrendering arena owner use the arena's physical boundary. */
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
        if (boss.phase() == net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Phase.ACCEPTANCE) carry(boss, proposed);
        var participatingBounds = previous.inflate(1e-7);
        for (var player : level.players()) {
            if (!participates(player) || !participatingBounds.intersects(player.getBoundingBox())) continue;
            Vec3 before = player.position();
            carry(player, proposed);
            if (player.position().distanceToSqr(before) > 1e-16) CARRIED.putIfAbsent(player, new Carried(level, before));
        }
        return proposed;
    }

    private static void carry(Entity entity, AABB bounds) {
        Vec3 correction = CalamitasArenaCollision.correction(bounds, entity.getBoundingBox());
        if (correction.lengthSqr() <= 1e-16) return;
        entity.setPos(entity.position().add(correction));
        entity.setDeltaMovement(CalamitasArenaCollision.clip(bounds, entity.getBoundingBox(), entity.getDeltaMovement()));
        if (correction.y > 0) { entity.fallDistance = 0; entity.setOnGround(true); }
    }

    /** Apply the same carry when the client receives a newer boundary. No camera or teleport handshake. */
    public static void clientBoundsChanged(net.minecraft.world.level.Level level, AABB previous, AABB proposed,
            net.minecraft.nbt.ListTag participants) {
        if (!level.isClientSide || proposed == null) return;
        for (var player : level.players())
            if (player.isLocalPlayer() && participates(player)
                    && (participants.stream().anyMatch(tag -> net.minecraft.nbt.NbtUtils.loadUUID(tag).equals(player.getUUID()))
                        || previous != null && previous.inflate(1e-7).intersects(player.getBoundingBox()))) carry(player, proposed);
    }

    public static net.minecraft.nbt.ListTag participants(ServerLevel level, AABB bounds) {
        var result = new net.minecraft.nbt.ListTag();
        for (var player : level.players())
            if (participates(player) && bounds.inflate(1e-7).intersects(player.getBoundingBox()))
                result.add(net.minecraft.nbt.NbtUtils.createUUID(player.getUUID()));
        return result;
    }

    /** Rebase native movement validation after all three source steps. The synchronized
     * boundary carries the client; repeated absolute teleports would starve acknowledgements. */
    public static void syncCarried(ServerLevel level) {
        var iterator = CARRIED.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var player = entry.getKey();
            if (entry.getValue().level() != level) continue;
            iterator.remove();
            if (player.level() != level || player.isRemoved() || !player.isAlive()
                    || player.position().distanceToSqr(entry.getValue().before()) < 1e-16) continue;
            if (player.connection != null) player.connection.resetPosition();
        }
    }

    public static void forgetLevel(net.minecraft.world.level.Level level) {
        CARRIED.entrySet().removeIf(entry -> entry.getValue().level() == level || entry.getKey().isRemoved());
    }

    public static Vec3 clip(Entity entity, Vec3 movement) {
        if (entity instanceof SupremeCalamitasEntity boss
                && boss.phase() == net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Phase.ACCEPTANCE) {
            var bounds = boss.arenaBounds();
            return bounds == null ? movement : CalamitasArenaCollision.clip(bounds, entity.getBoundingBox(), movement);
        }
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

    public static Vec3 constrainDestination(Player player, Vec3 requested) {
        if (!participates(player)) return requested;
        Vec3 constrained = requested;
        for (var boss : SupremeCalamitasEntity.loaded(player.level())) {
            var bounds = boss.arenaBounds();
            if (bounds != null && bounds.inflate(1e-7).intersects(player.getBoundingBox())) {
                var body = player.getBoundingBox().move(constrained.subtract(player.position()));
                constrained = constrained.add(CalamitasArenaCollision.correction(bounds, body));
            }
        }
        return constrained;
    }

    public static net.minecraft.world.phys.HitResult pearlHit(net.minecraft.world.entity.projectile.ThrownEnderpearl pearl,
            net.minecraft.world.phys.HitResult original) {
        if (!(pearl.getOwner() instanceof Player player) || !participates(player)) return original;
        Vec3 movement = pearl.getDeltaMovement();
        double fraction = 1;
        net.minecraft.core.Direction face = net.minecraft.core.Direction.UP;
        for (var boss : SupremeCalamitasEntity.loaded(pearl.level())) {
            var bounds = boss.arenaBounds();
            if (bounds == null || !bounds.inflate(1e-7).intersects(pearl.getBoundingBox())) continue;
            Vec3 allowed = CalamitasArenaCollision.clip(bounds, pearl.getBoundingBox(), movement);
            for (var axis : net.minecraft.core.Direction.Axis.values()) {
                double delta = movement.get(axis);
                if (delta == 0) continue;
                double ratio = allowed.get(axis) / delta;
                if (ratio < fraction) {
                    fraction = ratio;
                    face = net.minecraft.core.Direction.fromAxisAndDirection(axis, delta > 0
                            ? net.minecraft.core.Direction.AxisDirection.NEGATIVE : net.minecraft.core.Direction.AxisDirection.POSITIVE);
                }
            }
        }
        if (fraction >= 1) return original;
        Vec3 hit = pearl.position().add(movement.scale(fraction));
        if (original.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && original.getLocation().distanceToSqr(pearl.position()) <= hit.distanceToSqr(pearl.position())) return original;
        return new net.minecraft.world.phys.BlockHitResult(hit, face, net.minecraft.core.BlockPos.containing(hit), false);
    }

    /** Vanilla finally commits the packet's absolute coordinates after collision. Clamp
     * those too, so a pre-carry packet cannot put the player back behind a moving wall. */
    public static net.minecraft.network.protocol.game.ServerboundMovePlayerPacket constrainPacket(ServerPlayer player,
            net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet) {
        if (!participates(player) || !packet.hasPosition()) return packet;
        Vec3 requested = new Vec3(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
        if (!Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)) return packet;
        Vec3 constrained = constrainDestination(player, requested);
        if (constrained.distanceToSqr(requested) <= 1e-16) return packet;
        return packet.hasRotation()
                ? new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(constrained.x, constrained.y, constrained.z,
                        packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()), packet.isOnGround())
                : new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(constrained.x, constrained.y, constrained.z, packet.isOnGround());
    }
}
