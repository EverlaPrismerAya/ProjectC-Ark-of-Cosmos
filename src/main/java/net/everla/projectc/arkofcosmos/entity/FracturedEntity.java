package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.FracturedRules;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Calamity Team's Fractured Ark attack rules, with the project's shared Ark parry.
 * Original 60/120 Hz clocks run as 3/6 server substeps per Minecraft tick. */
public final class FracturedEntity extends Entity {
    public static final int BLADE = 0, BEAM = 1, PARRY = 2;
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(FracturedEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final ThreadLocal<LivingEntity> HURTING = new ThreadLocal<>();
    private final AttackRenderSnapshot clientSnapshot = new AttackRenderSnapshot();
    private final Set<Integer> hit = new HashSet<>();
    private UUID ownerUuid;
    private int ownerId, kind, sign = 1, charge, age, left, parriedAt = -1;
    private boolean ended, fired, stepping;
    private float damage, rotation, scale = 1;
    private ItemStack weapon = ItemStack.EMPTY;
    private Vec3 velocity = Vec3.ZERO;
    private AttackFrame frame = new AttackFrame(new Vec3(0,0,1), new Vec3(-1,0,0));
    private ListTag samples = new ListTag(), trail = new ListTag();

    public FracturedEntity(EntityType<? extends FracturedEntity> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public static FracturedEntity swing(Player p, int sign, int charge, AttackFrame frame) {
        return create(p, BLADE, sign, charge, FracturedRules.damage(charge), p.getBoundingBox().getCenter().add(frame.forward().scale(30/16d)), Vec3.ZERO, frame, p.getMainHandItem());
    }
    public static FracturedEntity parry(Player p, float damage, AttackFrame frame) {
        return create(p, PARRY, 1, 0, damage, p.getBoundingBox().getCenter(), Vec3.ZERO, frame, p.getMainHandItem());
    }
    private static FracturedEntity create(Player p, int kind, int sign, int charge, float damage, Vec3 at, Vec3 velocity, AttackFrame frame, ItemStack weapon) {
        FracturedEntity e = new FracturedEntity(ArkOfCosmos.FRACTURED_ATTACK.get(), p.level());
        e.ownerUuid = p.getUUID(); e.ownerId = p.getId(); e.kind = kind; e.sign = sign; e.charge = charge; e.damage = damage;
        e.refreshDimensions();
        e.frame = frame; e.velocity = velocity; e.weapon = weapon.copy(); e.setPos(at);
        e.left = kind == BLADE ? FracturedRules.SWING_UPDATES : kind == BEAM ? FracturedRules.BEAM_FRAMES : 16;
        e.scale = kind == BLADE ? FracturedRules.scale(0, charge) : kind == BEAM ? 2.4f : 1.4f;
        e.rotation = kind == BLADE ? FracturedRules.rotation(0, sign) : 0;
        e.publish(); p.level().addFreshEntity(e);
        if (kind == BLADE) ArkOfCosmos.sound(e, charge > 0 ? "phoenix" : "fractured_swing", 1);
        if (kind == PARRY) FracturedCombat.sound(e,p,"fractured_parry_start",1,false,false);
        return e;
    }
    public static boolean hurting(LivingEntity t) { return HURTING.get() == t; }
    public int kind() { return level().isClientSide ? view().getInt("Kind") : kind; }
    public int ownerId() { return level().isClientSide ? view().getInt("Owner") : ownerId; }
    public LivingEntity curseOwner(){return net.everla.projectc.arkofcosmos.combat.WeaponUser.resolve(level(),ownerUuid);}
    public ItemStack weapon() { return weapon; }
    public Player owner() { return ownerUuid == null ? null : level().getPlayerByUUID(ownerUuid); }
    @Override protected void defineSynchedData() { entityData.define(VIEW, new CompoundTag()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.fixed(kind() == BEAM ? 2 : .5f, kind() == BEAM ? 2 : .5f); }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (clientSnapshot != null && level().isClientSide && key.equals(VIEW) && !view().isEmpty()) {
            setPos(ElementsEntity.vector(view(), "Position")); clientSnapshot.initialize(view());
        }
        super.onSyncedDataUpdated(key);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { clientSnapshot.tick(view(), "Age"); return; }
        Player p = owner();
        if (!WeaponUser.active(p) || kind != BEAM && !FracturedCombat.holding(p)) { discardAttack(); return; }
        if (left <= 0) { discardAttack(); return; }
        samples = new ListTag(); samples.add(snapshot());
        stepping = true;
        for (int step = 0, count = kind == BLADE ? 6 : 3; step < count && left > 0 && !ended; step++) {
            remember();
            switch (kind) { case BLADE -> blade(p); case BEAM -> beam(p); case PARRY -> parry(p); }
            age++; left--; samples.add(snapshot());
        }
        stepping = false;
        if (!ended) publish();
    }
    private void blade(Player p) {
        rotation = FracturedRules.rotation(age, sign); scale = FracturedRules.scale(age, charge);
        Vec3 center = p.getBoundingBox().getCenter(), along = frame.direction(rotation);
        setPos(center.add(frame.forward().scale(30/16d)));
        if (!fired && charge > 0 && FracturedRules.ratio(age) > .5f) {
            create(p, BEAM, 1, charge, FracturedRules.beamDamage(damage), position(), frame.forward().scale(15 * 1.25 / 16), frame, weapon);
            fired = true;
        }
        Vec3 start = center.add(along.scale(30/16d));
        hitLine(p, start, start.add(along.scale(78 * scale / 16)), 24/16d);
    }
    private void beam(Player p) {
        rotation = (float)(Math.PI/4); scale = 2.4f; velocity = velocity.scale(FracturedRules.beamDeceleration(age));
        if (velocity.length() < 1/16d) { finishBeam(); return; }
        Vec3 old = position();
        if (FracturedRules.beamTiles(age)) {
            // The original has a 32x32 tile hitbox, independent of its elongated damage line.
            Vec3 move = Entity.collideBoundingBox(this, velocity, new AABB(old, old).inflate(1), level(), List.of());
            if (move.distanceToSqr(velocity) > 1e-12) { setPos(old.add(move)); finishBeam(); return; }
        }
        setPos(old.add(velocity));
        Vec3 start = position().subtract(frame.forward().scale(8.5/16d));
        hitLine(p, start, start.add(frame.forward().scale(34 * scale / 16)), 24/16d);
        if (!ended && left == 1) finishBeam();
    }
    private void parry(Player p) {
        ArkCombat.ParryPose pose = ArkCombat.parryPose(p, frame, age);
        setPos(pose.position()); scale = pose.scale();
        if (!ArkCombat.controls(p).parried) hitLine(p, position(), position().add(frame.forward().scale(142 * scale / 16)), 44/16d);
    }
    private void hitLine(Player p, Vec3 from, Vec3 to, double width) {
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width / 2 + 2), e -> ArkCombat.canHit(p, e))) {
            if (hit.contains(t.getId()) || !ArkGeometry.line(t.getBoundingBox(), from, to, width, frame)) continue;
            float multiplier = damage / FracturedRules.baseDamage();
            float amount = PlayerWeaponDamage.scale(p, weapon, damage) + EnchantmentHelper.getDamageBonus(weapon, t.getMobType()) * multiplier;
            LivingEntity previous = HURTING.get(); HURTING.set(t);
            boolean success;
            try { success = WeaponDamage.hurt(t, WeaponUser.source(this, p), amount); }
            finally { if (previous == null) HURTING.remove(); else HURTING.set(previous); }
            if (!success) continue;
            hit.add(t.getId()); p.setLastHurtMob(t);
            int fire = weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT); if (fire > 0) t.setSecondsOnFire(fire * 4);
            EnchantmentHelper.doPostHurtEffects(t, p);
            weapon.getAllEnchantments().forEach((enchantment, level) -> enchantment.doPostAttack(p, t, level));
            if (kind == PARRY) { hitEffect(t); ArkCombat.parrySuccess(p); return; }
            if (kind == BEAM) { hitEffect(t); finishBeam(); return; }
        }
    }
    public void parrySucceeded() {
        if (kind != PARRY || parriedAt >= 0) return;
        parriedAt = age;
        CompoundTag data = effect("fractured_parry", position());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
        publish();
    }
    private CompoundTag effect(String event, Vec3 at) {
        CompoundTag data = snapshot(); data.putString("Event", event);
        ElementsEntity.vector(data, "At", at);
        return data;
    }
    private void hitEffect(LivingEntity target) {
        Vec3 size = new Vec3(target.getBbWidth(), target.getBbHeight(), 0);
        Vec3 at = size.length() * 16 < 140 ? target.getBoundingBox().getCenter()
                : position().add(frame.direction(kind == BEAM ? Math.PI/4 : rotation).scale(60/16d));
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, effect("fractured_hit", at));
    }
    private void finishBeam() { if (!ended) ArkOfCosmos.sound(this, "fractured_beam_death", 1); discardAttack(); }
    public void discardAttack() {
        if (!ended) {
            CompoundTag data = effect("fractured_end", position());
            ListTag terminal = samples.copy();
            if (stepping) { CompoundTag last = snapshot(); last.putInt("Age", age + 1); terminal.add(last); }
            data.put("Samples", terminal);
            net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
        }
        ended = true; super.discard();
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !ended && left > 0 && WeaponUser.active(owner())) return;
        super.remove(reason);
    }
    private void remember() {
        trail.add(0, snapshotWithoutTrail());
        int count = kind == BLADE ? 10 : 5; while (trail.size() > count) trail.remove(trail.size() - 1);
    }
    private CompoundTag snapshotWithoutTrail() {
        CompoundTag s = new CompoundTag(); s.putInt("Age", age); s.putFloat("Rotation", rotation); s.putFloat("Scale", scale);
        ElementsEntity.vector(s, "Position", position()); return s;
    }
    private CompoundTag snapshot() {
        CompoundTag s = snapshotWithoutTrail(); s.putInt("Kind", kind); s.putInt("Owner", ownerId); s.putInt("Charge", charge); s.putInt("Sign", sign); s.putInt("ParriedAt", parriedAt); s.putUUID("Attack", getUUID());
        ElementsEntity.vector(s, "Velocity", velocity);
        ElementsEntity.vector(s, "F", frame.forward()); ElementsEntity.vector(s, "R", frame.right());
        Player p = owner(); ElementsEntity.vector(s, "OwnerVelocity", p == null ? Vec3.ZERO : p.getDeltaMovement().scale(1/3d));
        ElementsEntity.vector(s, "Center", p == null ? position() : p.getBoundingBox().getCenter());
        s.put("Trail", trail.copy()); return s;
    }
    private void publish() { CompoundTag s = snapshot(); s.put("Samples", samples.copy()); entityData.set(VIEW, s); }
    public CompoundTag view() { return entityData.get(VIEW); }
    public CompoundTag renderState(float partial) {
        CompoundTag state = clientSnapshot.state().isEmpty() ? view() : clientSnapshot.state();
        ListTag list = state.getList("Samples", 10); if (list.isEmpty()) return state;
        double index = Math.max(0, Math.min(1, partial)) * (list.size() - 1); int i = (int)index;
        CompoundTag a = list.getCompound(i), b = list.getCompound(Math.min(i + 1, list.size() - 1)), s = a.copy(); double t = index - i;
        for (String key : new String[]{"Position", "Center"}) ElementsEntity.vector(s, key, ElementsEntity.vector(a, key).lerp(ElementsEntity.vector(b, key), t));
        for (String key : new String[]{"Age", "Rotation", "Scale"}) s.putDouble(key, a.getDouble(key) + (b.getDouble(key) - a.getDouble(key)) * t);
        return s;
    }
}
