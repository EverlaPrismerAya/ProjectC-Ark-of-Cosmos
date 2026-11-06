package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import java.util.random.RandomGenerator;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One persisted Calamitas summon batch. Accepted slots never respawn, including
 * when unloaded or killed while a later Forge-cancelled slot is pending. */
public final class CalamitasSummons {
    /** Command-created members join the nearest encounter, or form an independent native test group. */
    static LivingEntity commandOwner(net.minecraft.world.entity.Mob entity, String role) {
        var server = (ServerLevel)entity.level();
        var boss = server.getEntitiesOfClass(SupremeCalamitasEntity.class, entity.getBoundingBox().inflate(256),
                e -> e.isAlive() && e.phase() != net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Phase.FINISHED)
                .stream().min(java.util.Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
        var owner = boss == null ? entity : boss;
        var journal = CalamitasBattleData.get(server); journal.begin(owner.getUUID()); journal.add(owner.getUUID(), entity.getUUID(), role);
        commandTarget(entity);
        return owner;
    }
    static void commandTarget(net.minecraft.world.entity.Mob entity) {
        entity.setTarget(entity.level().getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), 256,
                e -> e instanceof net.minecraft.world.entity.player.Player p && p.isAlive() && !p.isCreative() && !p.isSpectator()));
    }
    public static boolean commandRoot(net.minecraft.world.entity.Entity entity) {
        UUID owner = entity instanceof CalamitasBrotherEntity e ? e.ownerUUID()
                : entity instanceof CalamitasMinionEntity e ? e.ownerUUID()
                : entity instanceof CalamitasSepulcherEntity e ? e.ownerUUID() : null;
        return entity.getUUID().equals(owner);
    }
    public static void removeCommandRoot(net.minecraft.world.entity.Entity entity) {
        var server = (ServerLevel)entity.level(); var journal = CalamitasBattleData.get(server);
        var members = new java.util.HashSet<>(journal.members(entity.getUUID(), false));
        members.addAll(journal.members(entity.getUUID(), true)); journal.end(entity.getUUID());
        for (UUID id : members) {
            var child = server.getEntity(id);
            if (child instanceof CalamitasAttackEntity e) e.discardAttack();
            else if (child instanceof CalamitasBrotherEntity e) e.discardBrother();
            else if (child instanceof CalamitasMinionEntity e) e.discardMinion();
            else if (child instanceof CalamitasSepulcherEntity e) e.retire(false);
        }
    }
    public enum Kind { HEARTS, BROTHERS, SEEKERS }
    private final Kind kind;
    private final Vec3 origin, planeX;
    private final double pixels;
    private final UUID[] ids;
    private final Vec3[] centers;
    private final CompoundTag[] connections;
    private final boolean[] accepted;
    private final boolean cataclysmUp;
    private final UUID wormId;
    private boolean wormAccepted;

    private CalamitasSummons(Kind kind, Vec3 origin, Vec3 planeX, double pixels, boolean up, UUID wormId) {
        if (!finite(origin) || !finite(planeX) || Math.abs(planeX.y) > 1e-6
                || Math.abs(planeX.lengthSqr() - 1) > 1e-6 || !Double.isFinite(pixels) || pixels <= 0)
            throw new IllegalArgumentException("Invalid summon frame");
        this.kind = kind; this.origin = origin; this.planeX = planeX; this.pixels = pixels;
        this.cataclysmUp = up; this.wormId = wormId;
        int count = kind == Kind.BROTHERS ? 2 : 10;
        ids = new UUID[count]; centers = new Vec3[count]; connections = new CompoundTag[count]; accepted = new boolean[count];
    }
    public static CalamitasSummons begin(Kind kind, Vec3 origin, Vec3 planeX, double pixels, RandomGenerator random) {
        var batch = new CalamitasSummons(kind, origin, planeX, pixels, kind == Kind.BROTHERS && random.nextBoolean(), UUID.randomUUID());
        for (int i = 0; i < batch.ids.length; i++) {
            batch.ids[i] = UUID.randomUUID();
            batch.connections[i] = new CompoundTag();
            if (kind == Kind.HEARTS) {
                int row = i / 2;
                double x = (i % 2 == 0 ? -1 : 1) * (950 - row * 200);
                // NPC.NewNPC takes the bottom coordinate; the 24px source heart's
                // center is 12px above the cast marker.
                batch.centers[i] = origin.add(planeX.scale(x / pixels)).add(0, (812 - row * 100) / pixels, 0);
            } else if (kind == Kind.BROTHERS) batch.centers[i] = origin.add(planeX.scale((i == 0 ? 500 : -500) / pixels));
            else {
                // Preserve upstream initial spawn's literal sin/cos argument. The
                // seeker's next source frame switches to its degree-based orbit.
                double angle = i * 36;
                batch.centers[i] = origin.add(planeX.scale(Math.sin(angle) * 225 / pixels)).add(0, -Math.cos(angle) * 225 / pixels, 0);
            }
        }
        if (kind == Kind.HEARTS) batch.connectHearts(random);
        return batch;
    }
    private void connectHearts(RandomGenerator random) {
        // Source sorts by descending distance from safeBox.Left before consuming
        // endpoint randomness. Interleaved creation order remains unchanged.
        Integer[] order = new Integer[10];
        for (int i = 0; i < 10; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(centers[b].subtract(origin).dot(planeX), centers[a].subtract(origin).dot(planeX)));
        for (int i : order) {
            double x = centers[i].subtract(origin).dot(planeX) * pixels;
            var endpoints = new ListTag();
            for (int side = 0; side < 2; side++) {
                double endpoint = x; int tries = 0;
                do { endpoint = x + (side == 0 ? 1 : -1) * random.nextDouble(75, 250); tries++; }
                while (tries < 100 && Math.abs(endpoint) > 960);
                if (tries >= 100) endpoint = Math.max(-1000, Math.min(1000, endpoint));
                endpoints.add(point(origin.add(planeX.scale(endpoint / pixels)).add(0, 1000 / pixels, 0)));
            }
            if (random.nextBoolean()) {
                double endpoint = x + (random.nextBoolean() ? 1 : -1) * random.nextDouble(45, 360);
                endpoints.add(point(origin.add(planeX.scale(Math.max(-1000, Math.min(1000, endpoint)) / pixels)).add(0, 1000 / pixels, 0)));
            }
            connections[i].put("Endpoints", endpoints);
        }
    }
    public Kind kind() { return kind; }
    public Vec3 center(int slot) { return centers[slot]; }
    public int acceptedCount() { int n = 0; for (boolean value : accepted) if (value) n++; return n; }
    public boolean complete() { return acceptedCount() == ids.length && (kind != Kind.HEARTS || wormAccepted); }
    /** Returns false when a join was cancelled. The encounter must pause until
     * this returns true; it may persist the batch and retry after server restart. */
    public boolean retry(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel server)) throw new IllegalArgumentException("Server summon required");
        var journal = CalamitasBattleData.get(server);
        if (!journal.active(boss.getUUID())) return false;
        for (int i = 0; i < ids.length; i++) {
            if (accepted[i]) continue;
            // Native entity loading may precede the boss's batch restore. Never
            // replace an already joined or already dead stable UUID.
            if (journal.knownMember(boss.getUUID(), ids[i])) { accepted[i] = true; continue; }
            if (kind == Kind.BROTHERS) {
                var type = i == 0 ? SupremeCalamitasBrothers.Kind.CATACLYSM : SupremeCalamitasBrothers.Kind.CATASTROPHE;
                var entity = CalamitasBrotherEntity.spawn(boss, type, centers[i].subtract(0, 1.95 / 2, 0), planeX, pixels,
                        i == 0 ? cataclysmUp : !cataclysmUp, ids[i]);
                if (entity == null) return false;
            } else {
                var type = kind == Kind.HEARTS ? CalamitasMinionEntity.Kind.HEART : CalamitasMinionEntity.Kind.SEEKER;
                double height = kind == Kind.HEARTS ? 1.5 : .8;
                var entity = CalamitasMinionEntity.spawn(boss, type, centers[i].subtract(0, height / 2, 0), planeX, pixels, i * 36, ids[i]);
                if (entity == null) return false;
                if (kind == Kind.HEARTS) entity.setConnections(connections[i]);
            }
            accepted[i] = true;
        }
        if (kind == Kind.HEARTS && !wormAccepted) {
            if (journal.knownMember(boss.getUUID(), wormId)) wormAccepted = true;
            // NPC.NewNPC's Y is the head's bottom, while spawn expects its center.
            else wormAccepted = CalamitasSepulcherEntity.spawn(boss, origin.add(0, (-1000 + 32) / pixels, 0), planeX, pixels, wormId) != null;
        }
        return complete();
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.lengthSqr()); }
    private static CompoundTag point(Vec3 position) {
        var tag = new CompoundTag(); tag.putDouble("X", position.x); tag.putDouble("Y", position.y); tag.putDouble("Z", position.z); return tag;
    }
    private static Vec3 point(CompoundTag tag) {
        Vec3 v = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        if (!finite(v)) throw new IllegalArgumentException("Invalid summon position"); return v;
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("Kind", kind.name()); tag.put("Origin", point(origin)); tag.put("Plane", point(planeX));
        tag.putDouble("Pixels", pixels); tag.putBoolean("CataclysmUp", cataclysmUp); tag.putUUID("WormId", wormId); tag.putBoolean("WormAccepted", wormAccepted);
        var slots = new ListTag();
        for (int i = 0; i < ids.length; i++) {
            var slot = point(centers[i]); slot.putUUID("Id", ids[i]); slot.putBoolean("Accepted", accepted[i]); slot.put("Connections", connections[i].copy()); slots.add(slot);
        }
        tag.put("Slots", slots); return tag;
    }
    public static CalamitasSummons load(CompoundTag tag) {
        var batch = new CalamitasSummons(Kind.valueOf(tag.getString("Kind")), point(tag.getCompound("Origin")), point(tag.getCompound("Plane")),
                tag.getDouble("Pixels"), tag.getBoolean("CataclysmUp"), tag.getUUID("WormId"));
        var slots = tag.getList("Slots", Tag.TAG_COMPOUND);
        if (slots.size() != batch.ids.length) throw new IllegalArgumentException("Invalid summon slot count");
        var unique = new java.util.HashSet<UUID>(); unique.add(batch.wormId);
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.getCompound(i); batch.ids[i] = slot.getUUID("Id");
            if (!unique.add(batch.ids[i])) throw new IllegalArgumentException("Duplicate summon UUID");
            batch.centers[i] = point(slot); batch.accepted[i] = slot.getBoolean("Accepted"); batch.connections[i] = slot.getCompound("Connections").copy();
            for (Tag endpoint : batch.connections[i].getList("Endpoints", Tag.TAG_COMPOUND)) point((CompoundTag)endpoint);
        }
        batch.wormAccepted = tag.getBoolean("WormAccepted"); return batch;
    }
}
