package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import java.util.Random;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers.Kind;
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

/** Independent native-health brothers. The journal isolates partner lookup and revival
 * to this battle, including partners whose chunks are currently unloaded. */
public final class CalamitasBrotherEntity extends Monster {
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(CalamitasBrotherEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final Kind kind;
    private SupremeCalamitasBrothers combat;
    private UUID owner;
    private Vec3 planeX = new Vec3(1, 0, 0);
    private double pixels = 16, movement = 1;
    private int sourceAge;
    private boolean deathCommitted;

    public CalamitasBrotherEntity(EntityType<? extends CalamitasBrotherEntity> type, Level level, Kind kind) {
        super(type, level); this.kind = kind; combat = new SupremeCalamitasBrothers(kind, kind == Kind.CATACLYSM);
        noPhysics = true; setNoGravity(true); setPersistenceRequired(); xpReward = 0;
    }
    public Kind kind() { return kind; }
    public UUID ownerUUID() { return owner; }
    public CompoundTag view() { return entityData.get(VIEW); }
    /** 72px forcefield at the renderer's 3 * .75 scale. */
    public static double shieldDiameter(double pixels) { return 162 / pixels; }
    public double shieldDiameter() {
        double units = level().isClientSide ? view().getDouble("Pixels") : pixels;
        return shieldDiameter(units > 0 ? units : 16);
    }
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (kind == null) return super.getDimensions(pose);
        float diameter = (float)shieldDiameter();
        return net.minecraft.world.entity.EntityDimensions.fixed(diameter, diameter);
    }
    private void refreshShieldDimensions() {
        var size = getDimensions(getPose());
        if (size.width == getBbWidth() && size.height == getBbHeight()) return;
        Vec3 center = getBoundingBox().getCenter();
        refreshDimensions();
        setPos(center.x, center.y - getBbHeight() / 2, center.z);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (kind != null && VIEW.equals(key)) refreshShieldDimensions();
    }
    private String role() { return kind == Kind.CATACLYSM ? "cataclysm" : "catastrophe"; }
    private String partnerRole() { return kind == Kind.CATACLYSM ? "catastrophe" : "cataclysm"; }
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

    public static CalamitasBrotherEntity spawn(LivingEntity boss, Kind kind, Vec3 position,
            Vec3 planeX, double pixels, boolean movingUp) {
        return spawn(boss, kind, position, planeX, pixels, movingUp, UUID.randomUUID());
    }
    static CalamitasBrotherEntity spawn(LivingEntity boss, Kind kind, Vec3 position,
            Vec3 planeX, double pixels, boolean movingUp, UUID id) {
        if (!(boss.level() instanceof ServerLevel server)) throw new IllegalArgumentException("Server summon required");
        var journal = CalamitasBattleData.get(server);
        if (!journal.active(boss.getUUID())) return null;
        var brother = CalamitasContent.BROTHERS.get(kind).get().create(server);
        if (brother == null) return null;
        brother.setUUID(id); brother.owner = boss.getUUID(); brother.planeX = planeX; brother.pixels = pixels;
        brother.movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
        brother.combat = new SupremeCalamitasBrothers(kind, movingUp); brother.refreshDimensions(); brother.setPos(position);
        brother.getAttribute(Attributes.MAX_HEALTH).setBaseValue(388500 * ProjectCCommonConfig.calamitasValue("health_multiplier"));
        brother.setHealth(brother.getMaxHealth());
        brother.getAttribute(Attributes.ARMOR).setBaseValue(80 * ProjectCCommonConfig.calamitasValue("armor_multiplier"));
        brother.publish();
        if (!journal.add(brother.owner, id, brother.role())) return null;
        if (!server.addFreshEntity(brother)) { journal.cancelSummon(brother.owner, id); return null; }
        journal.updateBrotherHealth(brother.owner, id, 1);
        return brother;
    }
    /** Called by the boss even if all brother chunks are unloaded. */
    public static void retryRevival(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel server)) return;
        var journal = CalamitasBattleData.get(server);
        CompoundTag tag = journal.pendingBrotherRevival(boss.getUUID());
        if (tag == null) return;
        UUID id = tag.getUUID("UUID");
        if (server.getEntity(id) instanceof CalamitasBrotherEntity existing && boss.getUUID().equals(existing.owner)) {
            journal.completeBrotherRevival(boss.getUUID(), id); return;
        }
        var brother = spawn(boss, Kind.valueOf(tag.getString("Kind")),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ")),
                tag.getDouble("Pixels"), tag.getBoolean("MovingUp"), id);
        if (brother != null) journal.completeBrotherRevival(boss.getUUID(), id);
    }
    @Override public void tick() {
        setDeltaMovement(Vec3.ZERO); super.tick();
        if (owner == null) simulate(0);
        if (isRemoved()) return;
        simulate(level() instanceof ServerLevel server && CalamitasSimulation.managed(server, owner) ? 0 : 3);
    }
    void sourceStep() { simulate(1); }
    private void simulate(int frames) {
        if (!(level() instanceof ServerLevel server) || isDeadOrDying()) return;
        var journal = CalamitasBattleData.get(server);
        if (owner == null) {
            pixels = ProjectCCommonConfig.calamitasValue("pixels_per_block");
            refreshShieldDimensions();
            owner = CalamitasSummons.commandOwner(this, role()).getUUID();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(388500 * ProjectCCommonConfig.calamitasValue("health_multiplier"));
            getAttribute(Attributes.ARMOR).setBaseValue(80 * ProjectCCommonConfig.calamitasValue("armor_multiplier"));
            setHealth(getMaxHealth()); publish();
        }
        if (owner.equals(getUUID())) CalamitasSummons.commandTarget(this);
        if (owner == null || !journal.active(owner)) { discardBrother(); return; }
        if (journal.confirmedDead(owner, getUUID())) { discardBrother(); return; }
        if (!(server.getEntity(owner) instanceof LivingEntity boss) || !boss.isAlive()) return;
        if (frames == 0) return;
        LivingEntity target = boss instanceof Mob mob ? mob.getTarget() : null;
        if (target == null || !target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (int frame = 0; frame < frames; frame++) {
            sourceAge++;
            setHealth(Math.min(getHealth(), (float)(getMaxHealth() * combat.healthCap(journal.brotherRevivalAvailable(owner)))));
            combat.step(getBoundingBox().getCenter().scale(pixels), target.getBoundingBox().getCenter().scale(pixels),
                    target.getDeltaMovement().scale(pixels / 3), planeX, getHealth() / getMaxHealth(),
                    journal.brotherHealth(owner, partnerRole()), journal.living(owner, partnerRole()) > 0,
                    new Random(random.nextLong()), shot -> CalamitasAttackEntity.spawnBrother(boss,
                        kind == Kind.CATACLYSM ? CalamitasAttackEntity.Kind.FIST : CalamitasAttackEntity.Kind.SLASH,
                        shot.position().scale(1 / pixels), shot.velocity().scale(movement / pixels), planeX, pixels,
                        ProjectCCommonConfig.calamitasDamage(kind == Kind.CATACLYSM ? "fist" : "slash", shot.damage()), shot.variant(), shot.hand()));
            for (var cue : combat.cues()) CalamitasContent.sound(this, cue.sound(), getBoundingBox().getCenter(),
                    cue.volume(), (float)Math.pow(2, cue.octaves()));
            setPos(position().add(combat.velocity().scale(movement / pixels)));
            Vec3 aim = target.position().subtract(position());
            setYRot((float)Math.toDegrees(Math.atan2(-aim.x, aim.z))); yBodyRot = getYRot(); yHeadRot = getYRot();
            journal.updateBrotherHealth(owner, getUUID(), getHealth() / getMaxHealth());
        }
        if (frames > 1) publish();
    }
    void publish() {
        var tag = new CompoundTag(); tag.putString("Kind", kind.name()); tag.putInt("SourceAge", sourceAge);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putDouble("Pixels", pixels); tag.put("Combat", combat.save()); entityData.set(VIEW, tag);
    }
    @Override public boolean isAlliedTo(Entity other) {
        return owner != null && (owner.equals(other.getUUID())
                || other instanceof CalamitasBrotherEntity brother && owner.equals(brother.owner)
                || other instanceof CalamitasMinionEntity minion && owner.equals(minion.ownerUUID())
                || other instanceof CalamitasSepulcherEntity worm && owner.equals(worm.ownerUUID())
                || other instanceof CalamitasAttackEntity attack && owner.equals(attack.ownerUUID())) || super.isAlliedTo(other);
    }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return CalamitasContent.sound("brother_hurt");
    }
    @Override public net.minecraft.sounds.SoundEvent getDeathSound() {
        return CalamitasContent.sound("brother_death");
    }
    @Override public float getVoicePitch() { return 1; }
    @Override public boolean hurt(DamageSource source, float amount) {
        return (source.getEntity() == null || !isAlliedTo(source.getEntity())) && super.hurt(source, amount);
    }
    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        boolean enraged = owner != null && level() instanceof ServerLevel server
                && server.getEntity(owner) instanceof SupremeCalamitasEntity boss && boss.protectionBoost();
        return super.getDamageAfterMagicAbsorb(source, damage) * (enraged ? .0001f : .75f);
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!dead || deathCommitted || owner == null || !(level() instanceof ServerLevel server)) return;
        deathCommitted = true;
        var journal = CalamitasBattleData.get(server); journal.died(owner, getUUID());
        if (owner.equals(getUUID())) { journal.end(owner); return; }
        if (!combat.cachedPartnerAlive() && journal.brotherRevivalAvailable(owner)) {
            var tag = new CompoundTag(); tag.putUUID("UUID", UUID.randomUUID());
            tag.putString("Kind", kind == Kind.CATACLYSM ? Kind.CATASTROPHE.name() : Kind.CATACLYSM.name());
            tag.putDouble("X", getX()); tag.putDouble("Y", getY()); tag.putDouble("Z", getZ());
            tag.putDouble("PlaneX", planeX.x); tag.putDouble("PlaneZ", planeX.z); tag.putDouble("Pixels", pixels);
            tag.putBoolean("MovingUp", !combat.movingUp());
            if (journal.queueBrotherRevival(owner, getUUID(), tag)) {
                CalamitasContent.sound(this, "brother_revive", getBoundingBox().getCenter(), .9f, (float)Math.pow(2, .3f));
                if (server.getEntity(owner) instanceof LivingEntity boss) retryRevival(boss);
            }
        }
    }
    public void discardBrother() {
        if (owner != null && level() instanceof ServerLevel server) CalamitasBattleData.get(server).forget(owner, getUUID());
        discard();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public boolean isPushable() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("CalamitasOwner", owner);
        tag.putDouble("Pixels", pixels); tag.putDouble("Movement", movement); tag.putInt("SourceAge", sourceAge);
        tag.putDouble("PlaneX", planeX.x); tag.putDouble("PlaneZ", planeX.z);
        tag.putBoolean("DeathCommitted", deathCommitted); tag.put("Combat", combat.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setNoGravity(true);
        owner = tag.hasUUID("CalamitasOwner") ? tag.getUUID("CalamitasOwner") : null;
        if (!tag.contains("Pixels")) { publish(); return; }
        pixels = tag.getDouble("Pixels"); movement = tag.getDouble("Movement"); sourceAge = tag.getInt("SourceAge");
        planeX = new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ"));
        deathCommitted = tag.getBoolean("DeathCommitted"); combat.load(tag.getCompound("Combat"));
        if (!Double.isFinite(pixels) || pixels <= 0 || !Double.isFinite(movement) || movement <= 0 || sourceAge < 0
                || !Double.isFinite(planeX.lengthSqr()) || Math.abs(planeX.lengthSqr() - 1) > 1e-6)
            throw new IllegalArgumentException("Invalid Calamitas brother save");
        refreshDimensions();
        publish();
    }
}
