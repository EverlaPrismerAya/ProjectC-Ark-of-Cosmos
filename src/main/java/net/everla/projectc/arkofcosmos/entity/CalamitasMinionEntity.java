package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Independent heart/seeker types: no vanilla vex goals or global NPC lookup. */
public final class CalamitasMinionEntity extends Monster {
    public enum Kind { HEART, SEEKER }
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(CalamitasMinionEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final Kind kind;
    private UUID owner;
    private double pixels = 16, angle;
    private Vec3 planeX = new Vec3(1, 0, 0);
    private int timer, sourceAge;
    private CompoundTag connections = new CompoundTag();
    private boolean heartDeathEffects;
    private boolean seekerDeathEffects;

    public CalamitasMinionEntity(EntityType<? extends CalamitasMinionEntity> type, Level level, Kind kind) {
        super(type, level); this.kind = kind;
        noPhysics = true; setNoGravity(true); setPersistenceRequired(); xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(CalamitasContent.MAX_HEALTH.get(), 100)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 256);
    }
    @Override public double getAttributeValue(Attribute attribute) { return super.getAttributeValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public AttributeInstance getAttribute(Attribute attribute) { return super.getAttribute(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public double getAttributeBaseValue(Attribute attribute) { return super.getAttributeBaseValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VIEW, new CompoundTag()); }
    public Kind kind() { return kind; }
    public UUID ownerUUID() { return owner; }
    public CompoundTag view() { return entityData.get(VIEW); }

    /** Null means Forge rejected the spawn. The caller must retry the summon transaction. */
    public static CalamitasMinionEntity spawn(LivingEntity boss, Kind kind, Vec3 position, Vec3 planeX, double pixels, double degrees) {
        return spawn(boss, kind, position, planeX, pixels, degrees, UUID.randomUUID());
    }
    static CalamitasMinionEntity spawn(LivingEntity boss, Kind kind, Vec3 position, Vec3 planeX, double pixels, double degrees, UUID id) {
        if (!(boss.level() instanceof ServerLevel server)) throw new IllegalArgumentException("Server summon required");
        var journal = CalamitasBattleData.get(server);
        if (!journal.active(boss.getUUID())) return null;
        var minion = CalamitasContent.MINIONS.get(kind).get().create(server);
        if (minion == null) return null;
        minion.setUUID(id);
        minion.owner = boss.getUUID(); minion.pixels = pixels; minion.angle = degrees; minion.planeX = planeX;
        minion.setPos(position);
        minion.getAttribute(Attributes.MAX_HEALTH).setBaseValue((kind == Kind.HEART ? 30000 : 66500) * ProjectCCommonConfig.calamitasValue("health_multiplier"));
        minion.setHealth(minion.getMaxHealth());
        minion.getAttribute(Attributes.ARMOR).setBaseValue((kind == Kind.SEEKER ? 60 : 0) * ProjectCCommonConfig.calamitasValue("armor_multiplier"));
        minion.publish();
        // Record before joining: callbacks can observe a fully owned entity. Roll back
        // a cancelled join without counting a required summon as a confirmed death.
        if (!journal.add(minion.owner, minion.getUUID(), kind == Kind.HEART ? "heart" : "seeker")) return null;
        if (!server.addFreshEntity(minion)) { journal.cancelSummon(minion.owner, minion.getUUID()); return null; }
        return minion;
    }
    public void setConnections(CompoundTag endpoints) { connections = endpoints.copy(); publish(); }
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
        if (owner == null) {
            pixels = ProjectCCommonConfig.calamitasValue("pixels_per_block");
            owner = CalamitasSummons.commandOwner(this, kind == Kind.HEART ? "heart" : "seeker").getUUID();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue((kind == Kind.HEART ? 30000 : 66500) * ProjectCCommonConfig.calamitasValue("health_multiplier"));
            setHealth(getMaxHealth()); publish();
        }
        if (owner.equals(getUUID())) CalamitasSummons.commandTarget(this);
        if (owner == null || !journal.active(owner)) { discardMinion(); return; }
        if (journal.confirmedDead(owner, getUUID())) { discardMinion(); return; }
        if (!(server.getEntity(owner) instanceof LivingEntity boss) || !boss.isAlive()) return;
        if (frames == 0) return;
        LivingEntity target = boss instanceof Mob mob ? mob.getTarget() : null;
        if (target == null || !target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (int frame = 0; frame < frames; frame++) {
            sourceAge++;
            if (kind == Kind.HEART) continue;
            // Upstream shoots from the previous orbit position, then advances orbit.
            if (++timer > 180) {
                double distance = getBoundingBox().getCenter().distanceTo(target.getBoundingBox().getCenter()) * pixels;
                boolean fired = distance > 160 && distance < 1952;
                Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
                var sample = new CompoundTag(); sample.putBoolean("SeekerShotDust", true); sample.putBoolean("Fired", fired);
                sample.putInt("SourcePhase", phase + frame);
                Vec3 at = getBoundingBox().getCenter(), shootVelocity = direction.scale(5 / pixels);
                sample.putDouble("X", at.x); sample.putDouble("Y", at.y); sample.putDouble("Z", at.z);
                sample.putDouble("VX", shootVelocity.x); sample.putDouble("VY", shootVelocity.y); sample.putDouble("VZ", shootVelocity.z);
                var samples = new net.minecraft.nbt.ListTag(); samples.add(sample);
                var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
                data.putInt("Owner", boss.getId()); data.putUUID("OwnerUUID", boss.getUUID());
                data.putDouble("Pixels", pixels); data.put("Samples", samples);
                net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
                if (fired) {
                    double movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
                    CalamitasAttackEntity.spawn(boss, CalamitasAttackEntity.Kind.DART, getBoundingBox().getCenter(),
                            direction.scale(2.5 / pixels * movement), planeX, pixels,
                            ProjectCCommonConfig.calamitasDamage("dart", 80), ProjectCCommonConfig.calamitasDamage("dart", 80), 15 / pixels * movement, 3);
                }
                timer = 0;
            }
            if (timer >= 145) {
                var sample = new CompoundTag(); sample.putBoolean("SeekerWarning", true); sample.putBoolean("Pulse", timer == 145);
                sample.putInt("SourcePhase", phase + frame);
                Vec3 at = getBoundingBox().getCenter();
                Vec3 shootVelocity = net.everla.projectc.arkofcosmos.calamity.CalamitasSpace.unit(
                        target.getBoundingBox().getCenter().subtract(at), new Vec3(0, -1, 0)).scale(5 / pixels);
                sample.putDouble("X", at.x); sample.putDouble("Y", at.y); sample.putDouble("Z", at.z);
                sample.putDouble("VX", shootVelocity.x); sample.putDouble("VY", shootVelocity.y); sample.putDouble("VZ", shootVelocity.z);
                var samples = new net.minecraft.nbt.ListTag(); samples.add(sample);
                var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
                data.putInt("Owner", boss.getId()); data.putUUID("OwnerUUID", boss.getUUID());
                data.putDouble("Pixels", pixels); data.put("Samples", samples);
                net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
            }
            double radians = Math.toRadians(angle);
            Vec3 center = boss.getBoundingBox().getCenter().subtract(planeX.scale(Math.cos(radians) * 225 / pixels)).add(0, Math.sin(radians) * 225 / pixels, 0);
            setPos(center.subtract(0, getBbHeight() / 2, 0));
            Vec3 aim = target.getBoundingBox().getCenter().subtract(center);
            setYRot((float)Math.toDegrees(Math.atan2(-aim.x, aim.z))); yBodyRot = getYRot(); yHeadRot = getYRot();
            angle = (angle + .5) % 360;
        }
        if (frames > 1) publish();
    }
    void publish() {
        var tag = new CompoundTag();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Timer", timer); tag.putInt("SourceAge", sourceAge);
        tag.putDouble("Angle", angle); tag.putDouble("Pixels", pixels);
        tag.put("Connections", connections.copy());
        entityData.set(VIEW, tag);
    }
    @Override public boolean isAlliedTo(Entity other) {
        return owner != null && (owner.equals(other.getUUID())
                || other instanceof CalamitasMinionEntity minion && owner.equals(minion.owner)
                || other instanceof CalamitasBrotherEntity brother && owner.equals(brother.ownerUUID())
                || other instanceof CalamitasSepulcherEntity worm && owner.equals(worm.ownerUUID())
                || other instanceof CalamitasAttackEntity attack && owner.equals(attack.ownerUUID())) || super.isAlliedTo(other);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        return (source.getEntity() == null || !isAlliedTo(source.getEntity())) && super.hurt(source, amount);
    }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return kind == Kind.HEART ? CalamitasContent.sound("heart_hurt") : null;
    }
    @Override public net.minecraft.sounds.SoundEvent getDeathSound() {
        return CalamitasContent.sound(kind == Kind.HEART ? "heart_death" : "seeker_death");
    }
    @Override public float getVoicePitch() {
        return kind == Kind.HEART ? (float)Math.pow(2, (random.nextFloat() - .5f) * .2f) : 1;
    }
    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        float reduced = super.getDamageAfterMagicAbsorb(source, damage);
        boolean enraged = owner != null && level() instanceof ServerLevel server
                && server.getEntity(owner) instanceof SupremeCalamitasEntity boss && boss.protectionBoost();
        return kind == Kind.SEEKER ? reduced * (enraged ? .0001f : .75f) : reduced;
    }
    @Override public void die(DamageSource source) {
        LivingEntity deathTarget = level() instanceof ServerLevel server && owner != null && server.getEntity(owner) instanceof Mob mob
                ? mob.getTarget() : getTarget();
        Vec3 deathTargetPosition = deathTarget == null ? null : deathTarget.getBoundingBox().getCenter();
        super.die(source);
        // Forge may cancel death. Only commit after native death actually began.
        if (dead && owner != null && level() instanceof ServerLevel server) {
            if (kind == Kind.HEART ? !heartDeathEffects : !seekerDeathEffects) {
                if (kind == Kind.HEART) heartDeathEffects = true; else seekerDeathEffects = true;
                var sample = new CompoundTag(); sample.putBoolean(kind == Kind.HEART ? "HeartTendrils" : "SeekerDeath", true);
                sample.putInt("SourcePhase", CalamitasSimulation.projectilePhase());
                var center = getBoundingBox().getCenter();
                sample.putDouble("X", center.x); sample.putDouble("Y", center.y); sample.putDouble("Z", center.z);
                sample.putDouble("PlaneX", planeX.x); sample.putDouble("PlaneZ", planeX.z);
                if (kind == Kind.HEART) sample.put("Endpoints", connections.getList("Endpoints", 10).copy());
                else if (deathTarget != null) {
                    sample.putInt("Target", deathTarget.getId()); sample.putUUID("TargetUUID", deathTarget.getUUID());
                    sample.putDouble("TargetX", deathTargetPosition.x); sample.putDouble("TargetY", deathTargetPosition.y); sample.putDouble("TargetZ", deathTargetPosition.z);
                }
                var samples = new net.minecraft.nbt.ListTag(); samples.add(sample);
                var event = new CompoundTag(); event.putString("Event", "calamitas_cast_sparks");
                var eventOwner = server.getEntity(owner); if (eventOwner == null) eventOwner = this;
                event.putInt("Owner", eventOwner.getId()); event.putUUID("OwnerUUID", eventOwner.getUUID());
                event.putDouble("Pixels", pixels); event.put("Samples", samples);
                net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, event);
            }
            var journal = CalamitasBattleData.get(server); journal.died(owner, getUUID());
            if (owner.equals(getUUID())) journal.end(owner);
        }
    }
    public void discardMinion() {
        if (owner != null && level() instanceof ServerLevel server) CalamitasBattleData.get(server).forget(owner, getUUID());
        discard();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public boolean isPushable() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("CalamitasOwner", owner);
        tag.putDouble("Pixels", pixels); tag.putDouble("Angle", angle); tag.putInt("Timer", timer); tag.putInt("SourceAge", sourceAge);
        tag.putDouble("PlaneX", planeX.x); tag.putDouble("PlaneZ", planeX.z); tag.put("Connections", connections.copy());
        tag.putBoolean("HeartDeathEffects", heartDeathEffects);
        tag.putBoolean("SeekerDeathEffects", seekerDeathEffects);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setNoGravity(true);
        heartDeathEffects = tag.getBoolean("HeartDeathEffects");
        seekerDeathEffects = tag.getBoolean("SeekerDeathEffects");
        owner = tag.hasUUID("CalamitasOwner") ? tag.getUUID("CalamitasOwner") : null;
        if (!tag.contains("Pixels")) { publish(); return; }
        pixels = tag.getDouble("Pixels"); angle = tag.getDouble("Angle"); timer = tag.getInt("Timer"); sourceAge = tag.getInt("SourceAge");
        planeX = new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ")).normalize();
        connections = tag.getCompound("Connections").copy();
        if (!Double.isFinite(pixels) || pixels <= 0 || !Double.isFinite(angle) || timer < 0 || timer > 180 || sourceAge < 0
                || !Double.isFinite(planeX.x) || !Double.isFinite(planeX.z) || planeX.lengthSqr() < .99)
            throw new IllegalArgumentException("Invalid Calamitas minion save");
        publish();
    }
}
