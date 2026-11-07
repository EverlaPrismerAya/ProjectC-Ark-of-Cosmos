package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSepulcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Head, body, energy, tail and arms have independent entity IDs and native health.
 * Only the head advances the source clock; loaded children consume its poses.
 */
public final class CalamitasSepulcherEntity extends Monster {
    public enum Kind { HEAD, BODY, ENERGY, TAIL, ARM }
    private static final int CHILDREN = SupremeCalamitasSepulcher.SEGMENTS;
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(CalamitasSepulcherEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final Kind kind;
    private UUID owner, head;
    private int slot = -1, cooldown, sourceAge;
    private double pixels = 16, movement = 1;
    private boolean retiring;
    private float opacity;
    private Vec3 velocity = Vec3.ZERO, planeX = new Vec3(1, 0, 0), forward = new Vec3(0, 1, 0);
    private SupremeCalamitasSepulcher.Trail trail;
    private final UUID[] children = new UUID[CHILDREN];

    public CalamitasSepulcherEntity(EntityType<? extends CalamitasSepulcherEntity> type, Level level, Kind kind) {
        super(type, level); this.kind = kind;
        noPhysics = true; setNoGravity(true); setPersistenceRequired(); xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(CalamitasContent.MAX_HEALTH.get(), 690)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 256);
    }
    @Override public double getAttributeValue(Attribute attribute) { return super.getAttributeValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public AttributeInstance getAttribute(Attribute attribute) { return super.getAttribute(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public double getAttributeBaseValue(Attribute attribute) { return super.getAttributeBaseValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VIEW, new CompoundTag()); }
    public UUID ownerUUID() { return owner; }
    public Kind kind() { return kind; }
    public CompoundTag view() { return entityData.get(VIEW); }
    public boolean chainComplete() {
        for (UUID child : children) if (child == null) return false;
        return true;
    }
    public static Kind childKind(int index) {
        if (index >= SupremeCalamitasSepulcher.SEGMENTS) return Kind.ARM;
        return index == 51 ? Kind.TAIL : SupremeCalamitasSepulcher.energy(index) ? Kind.ENERGY : Kind.BODY;
    }
    public static CalamitasSepulcherEntity spawn(LivingEntity boss, Vec3 center, Vec3 planeX, double pixels) {
        return spawn(boss, center, planeX, pixels, UUID.randomUUID());
    }
    static CalamitasSepulcherEntity spawn(LivingEntity boss, Vec3 center, Vec3 planeX, double pixels, UUID id) {
        if (!(boss.level() instanceof ServerLevel server)) throw new IllegalArgumentException("Server summon required");
        var entity = CalamitasContent.SEPULCHER.get(Kind.HEAD).get().create(server);
        if (entity == null) return null;
        entity.setUUID(id);
        entity.owner = boss.getUUID(); entity.head = entity.getUUID(); entity.pixels = pixels; entity.planeX = planeX;
        entity.movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
        entity.centerAt(center);
        entity.forward = boss.getBoundingBox().getCenter().subtract(center).normalize();
        entity.trail = new SupremeCalamitasSepulcher.Trail(center, entity.forward, pixels);
        entity.initializeHealth(); entity.publish();
        var journal = CalamitasBattleData.get(server);
        if (!journal.add(entity.owner, entity.getUUID(), "worm")) return null;
        if (!server.addFreshEntity(entity)) { journal.cancelSummon(entity.owner, entity.getUUID()); return null; }
        return entity;
    }
    private void initializeHealth() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(345000 * ProjectCCommonConfig.calamitasValue("health_multiplier"));
        setHealth(getMaxHealth());
    }
    private void centerAt(Vec3 center) { setPos(center.subtract(0, getBbHeight() / 2, 0)); }
    private void spawnChildren(ServerLevel server) {
        var journal = CalamitasBattleData.get(server);
        for (int i = 0; i < CHILDREN; i++) {
            if (children[i] != null) continue;
            var child = CalamitasContent.SEPULCHER.get(childKind(i)).get().create(server);
            if (child == null) return;
            child.owner = owner; child.head = getUUID(); child.slot = i; child.pixels = pixels;
            child.movement = movement; child.planeX = planeX; child.forward = forward;
            child.centerAt(getBoundingBox().getCenter()); child.initializeHealth();
            child.publish();
            if (!journal.add(owner, child.getUUID(), "worm_segment")) return;
            if (!server.addFreshEntity(child)) { journal.cancelSummon(owner, child.getUUID()); return; }
            children[i] = child.getUUID();
        }
    }
    @Override public void tick() {
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (owner == null) simulate(0, 0);
        if (isRemoved()) return;
        simulate(level() instanceof ServerLevel server && CalamitasSimulation.managed(server, owner) ? 0 : 3, 0);
    }
    void sourceStep(int phase) { simulate(1, phase); }
    private void simulate(int frames, int phase) {
        if (!(level() instanceof ServerLevel server) || isDeadOrDying()) return;
        var journal = CalamitasBattleData.get(server);
        if (kind == Kind.ARM) { retire(false); return; }
        if (owner == null) {
            pixels = ProjectCCommonConfig.calamitasValue("pixels_per_block");
            var commandOwner = CalamitasSummons.commandOwner(this, kind == Kind.HEAD ? "worm" : "worm_segment");
            owner = commandOwner.getUUID();
            head = getUUID(); opacity = 1; initializeHealth();
            if (kind == Kind.HEAD) {
                forward = commandOwner == this ? new Vec3(1, 0, 0) : commandOwner.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
                if (forward.lengthSqr() < 1e-12) forward = new Vec3(1, 0, 0);
                trail = new SupremeCalamitasSepulcher.Trail(getBoundingBox().getCenter(), forward, pixels);
            }
            publish();
        }
        if (owner.equals(getUUID())) CalamitasSummons.commandTarget(this);
        if (owner == null || !journal.active(owner)) { retire(false); return; }
        if (head != null && journal.confirmedDead(owner, head)) { retire(true); return; }
        if (!(server.getEntity(owner) instanceof LivingEntity boss) || !boss.isAlive()) return;
        if (frames == 0) return;
        if (kind != Kind.HEAD) return;
        if (!owner.equals(getUUID()) && journal.living(owner, "heart") == 0) { retireChain(server, true); return; }
        if (!(boss instanceof Mob mob) || mob.getTarget() == null || !mob.getTarget().isAlive()) return;
        spawnChildren(server);
        if (!chainComplete()) return; // cancelled joins retry without starting a shorter worm
        var loaded = new CalamitasSepulcherEntity[CHILDREN];
        for (int i = 0; i < CHILDREN; i++)
            if (server.getEntity(children[i]) instanceof CalamitasSepulcherEntity child && !child.isDeadOrDying()) loaded[i] = child;
        for (int frame = 0; frame < frames && !isRemoved(); frame++) {
            if (cooldown > 0) cooldown--;
            sourceAge++; opacity = Math.min(1, opacity + .165f);
            Vec3 center = getBoundingBox().getCenter();
            // Source seeks SCal's top-left plus half the targeted player's size.
            LivingEntity target = mob.getTarget();
            LivingEntity anchor = owner.equals(getUUID()) ? target : boss;
            Vec3 destination = anchor.getBoundingBox().getCenter()
                    .add(planeX.scale((target.getBbWidth() * pixels / 2 - 22) / pixels))
                    .add(0, (22 - target.getBbHeight() * pixels / 2) / pixels, 0);
            velocity = SupremeCalamitasSepulcher.steer(velocity, center.scale(pixels), destination.scale(pixels), cooldown);
            Vec3 next = center.add(velocity.scale(movement / pixels));
            if (velocity.lengthSqr() > 1e-12) forward = velocity.normalize();
            centerAt(next); orient(forward); trail.append(next);
            // Trigger distance is measured before source engine movement is applied.
            if (center.distanceTo(anchor.getBoundingBox().getCenter()) * pixels <= 110 && cooldown <= 0) {
                cooldown = 150;
                var sample = new CompoundTag(); sample.putBoolean("SepulcherBurst", true);
                sample.putInt("SourcePhase", phase + frame);
                sample.putDouble("X", center.x); sample.putDouble("Y", center.y); sample.putDouble("Z", center.z);
                var samples = new ListTag(); samples.add(sample);
                var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
                data.putInt("Owner", boss.getId()); data.putUUID("OwnerUUID", boss.getUUID());
                data.putDouble("Pixels", pixels); data.put("Samples", samples);
                net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
                CalamitasContent.sound(this, "sepulcher_burst", center, 1.8f, (float)Math.pow(2, -.65));
                double damage = ProjectCCommonConfig.calamitasDamage("dart", 80);
                for (int k = 0; k < 30; k++) {
                    Vec3 direction = net.everla.projectc.arkofcosmos.calamity.CalamitasSpace.direction(random);
                    CalamitasAttackEntity.spawn(boss, CalamitasAttackEntity.Kind.DART, center,
                            direction.scale(movement / pixels), planeX, pixels, damage, damage, 15 * movement / pixels, 3);
                }
            }
            var positions = trail.segments(pixels);
            Vec3 ahead = next;
            for (int i = 0; i < SupremeCalamitasSepulcher.SEGMENTS; i++) {
                Vec3 at = positions.get(i), tangent = ahead.subtract(at).normalize();
                if (loaded[i] != null) loaded[i].follow(at, tangent, sourceAge, opacity);
                ahead = at;
            }

        }
        if (frames > 1) {
            publish();
            for (var child : loaded) if (child != null) child.publish();
        }
    }
    private void follow(Vec3 center, Vec3 direction, int age, float alpha) {
        centerAt(center); forward = direction; sourceAge = age; opacity = alpha; orient(direction);
    }
    private void orient(Vec3 direction) {
        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, direction.y)))));
        yBodyRot = getYRot(); yHeadRot = getYRot();
    }
    private void retireChain(ServerLevel server, boolean effects) {
        for (UUID child : children) if (child != null && server.getEntity(child) instanceof CalamitasSepulcherEntity segment) segment.retire(effects);
        retire(effects);
    }
    /** Internal heart-controlled disappearance is recorded explicitly; unloading never is. */
    public void retire(boolean effects) {
        if (retiring || isRemoved()) return;
        retiring = true;
        if (owner != null && level() instanceof ServerLevel server) {
            if (effects && kind == Kind.HEAD && server.getEntity(owner) instanceof Mob boss && boss.getTarget() != null)
                CalamitasContent.sound(this, "sepulcher_death", boss.getTarget().getBoundingBox().getCenter(), 1, 1);
            if (effects && kind == Kind.ENERGY && server.getEntity(owner) instanceof LivingEntity boss && boss.isAlive()) {
                // The source re-evaluates Next(1,4) in the loop condition.
                for (int i = 0; i < random.nextInt(3) + 1; i++) if (random.nextInt(3) == 0) {
                    double angle = (random.nextDouble() * 2 - 1) * .53;
                    double azimuth = random.nextDouble() * Math.PI * 2;
                    Vec3 direction = new Vec3(Math.cos(azimuth) * Math.sin(angle), Math.cos(angle), Math.sin(azimuth) * Math.sin(angle));
                    CalamitasAttackEntity.spawn(boss, CalamitasAttackEntity.Kind.SOUL, getBoundingBox().getCenter(),
                            direction.scale((2.5 + random.nextDouble() * 1.5) * movement / pixels), planeX, pixels, 0, 0, 0, 0);
                }
            }
            var journal = CalamitasBattleData.get(server);
            journal.died(owner, getUUID()); journal.forget(owner, getUUID());
            if (owner.equals(getUUID())) journal.end(owner);
        }
        discard();
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (dead && owner != null && level() instanceof ServerLevel server) {
            CalamitasBattleData.get(server).died(owner, getUUID());
            if (kind == Kind.HEAD) retireChain(server, true);
            else if (kind == Kind.ENERGY) retire(true);
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (kind == Kind.ARM || source.getEntity() != null && isAlliedTo(source.getEntity())) return false;
        return super.hurt(source, amount);
    }
    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) { return super.getDamageAfterMagicAbsorb(source, damage) * .000001f; }
    @Override public boolean isAlliedTo(Entity other) {
        return owner != null && (owner.equals(other.getUUID())
                || other instanceof CalamitasSepulcherEntity worm && owner.equals(worm.owner)
                || other instanceof CalamitasMinionEntity minion && owner.equals(minion.ownerUUID())
                || other instanceof CalamitasBrotherEntity brother && owner.equals(brother.ownerUUID())
                || other instanceof CalamitasAttackEntity attack && owner.equals(attack.ownerUUID())) || super.isAlliedTo(other);
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public boolean isPushable() { return false; }
    void publish() {
        var tag = new CompoundTag();
        if (owner != null) tag.putUUID("Owner", owner);
        if (head != null) tag.putUUID("Head", head);
        tag.putInt("Slot", slot); tag.putInt("SourceAge", sourceAge); tag.putFloat("Opacity", opacity);
        tag.putDouble("FX", forward.x); tag.putDouble("FY", forward.y); tag.putDouble("FZ", forward.z);
        entityData.set(VIEW, tag);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("CalamitasOwner", owner);
        if (head != null) tag.putUUID("Head", head);
        tag.putInt("Slot", slot); tag.putInt("Cooldown", cooldown); tag.putInt("SourceAge", sourceAge);
        tag.putDouble("Pixels", pixels); tag.putDouble("Movement", movement); tag.putFloat("Opacity", opacity);
        tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
        tag.putDouble("PlaneX", planeX.x); tag.putDouble("PlaneZ", planeX.z);
        tag.putDouble("FX", forward.x); tag.putDouble("FY", forward.y); tag.putDouble("FZ", forward.z);
        if (kind == Kind.HEAD) {
            if (trail != null) tag.put("Trail", trail.save());
            var list = new ListTag();
            for (int i = 0; i < CHILDREN; i++) if (children[i] != null) {
                var child = new CompoundTag(); child.putInt("Slot", i); child.putUUID("Id", children[i]); list.add(child);
            }
            tag.put("Children", list);
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setNoGravity(true);
        owner = tag.hasUUID("CalamitasOwner") ? tag.getUUID("CalamitasOwner") : null;
        if (!tag.contains("Pixels")) { publish(); return; }
        head = tag.hasUUID("Head") ? tag.getUUID("Head") : null;
        slot = tag.getInt("Slot"); cooldown = tag.getInt("Cooldown"); sourceAge = tag.getInt("SourceAge");
        pixels = tag.getDouble("Pixels"); movement = tag.getDouble("Movement"); opacity = tag.getFloat("Opacity");
        velocity = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
        planeX = new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ"));
        forward = new Vec3(tag.getDouble("FX"), tag.getDouble("FY"), tag.getDouble("FZ"));
        if (!Double.isFinite(pixels) || pixels <= 0 || !Double.isFinite(movement) || movement <= 0 || cooldown < 0 || cooldown > 150
                || sourceAge < 0 || !Float.isFinite(opacity) || opacity < 0 || opacity > 1 || !Double.isFinite(velocity.lengthSqr())
                || !Double.isFinite(planeX.lengthSqr()) || Math.abs(planeX.lengthSqr() - 1) > 1e-6
                || !Double.isFinite(forward.lengthSqr())
                || kind == Kind.HEAD && slot != -1 || kind != Kind.HEAD && !(slot == -1 && getUUID().equals(head)) && (slot < 0 || slot >= SupremeCalamitasSepulcher.SEGMENTS + SupremeCalamitasSepulcher.LEGACY_ARMS || childKind(slot) != kind))
            throw new IllegalArgumentException("Invalid Sepulcher entity save");
        if (kind == Kind.HEAD) {
            trail = new SupremeCalamitasSepulcher.Trail(getBoundingBox().getCenter(), forward, pixels);
            if (tag.contains("Trail")) trail.load(tag.getCompound("Trail"));
            for (Tag raw : tag.getList("Children", Tag.TAG_COMPOUND)) {
                var child = (CompoundTag) raw; int index = child.getInt("Slot");
                if (index >= 0 && index < CHILDREN && child.hasUUID("Id")) children[index] = child.getUUID("Id");
            }
        }
        publish();
    }
}
