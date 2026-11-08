package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.AncientsRules;
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

/** Calamity Team's Ark of the Ancients attack rules, with the project's shared Ark parry.
 * Original 60/120 Hz clocks run as 3/6 server substeps per Minecraft tick. */
public final class AncientsEntity extends Entity {
    public static final int BLADE = 0, BEAM = 1, PARRY = 2, STAR = 3, BLAST = 4;
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(AncientsEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot = new AttackRenderSnapshot();
    private final Set<Integer> hit = new HashSet<>();
    private UUID ownerUuid;
    private int ownerId, kind, sign = 1, charge, age, left, parriedAt = -1;
    private boolean ended, fired, stepping, homing;
    private int soundDelay, numHits;
    private float damage, rotation, scale = 1;
    private ItemStack weapon = ItemStack.EMPTY;
    private Vec3 velocity = Vec3.ZERO;
    private AttackFrame frame = new AttackFrame(new Vec3(0,0,1), new Vec3(-1,0,0));
    private ListTag samples = new ListTag(), trail = new ListTag();

    public AncientsEntity(EntityType<? extends AncientsEntity> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public static AncientsEntity swing(Player p, int sign, int charge, AttackFrame frame) {
        return create(p, BLADE, sign, charge, AncientsRules.damage(charge), p.getBoundingBox().getCenter().add(frame.forward().scale(30/16d)), Vec3.ZERO, frame, p.getMainHandItem());
    }
    public static void stars(Player p, int sign, int charge, AttackFrame frame) {
        if (sign != 1) return;
        Vec3 center = p.getBoundingBox().getCenter();
        float damage = AncientsRules.starDamage(AncientsRules.damage(charge));
        if (charge == 0) create(p, STAR, 1, 0, damage, center.add(frame.forward().scale(20/16d)), frame.forward().scale(12/16d), frame, p.getMainHandItem());
        for (int side : new int[]{1,-1}) create(p, STAR, 1, charge > 0 ? 1 : 0, damage,
            center.add(frame.right().scale(side*30/16d)), frame.direction(side*Math.PI*.075).scale(12/16d), frame, p.getMainHandItem());
    }
    public static AncientsEntity blast(Player p, int charge, AttackFrame frame) {
        return create(p, BLAST, 1, charge, AncientsRules.blastDamage(charge,0),
            p.getBoundingBox().getCenter().add(frame.forward().scale(90/16d)), frame.forward().scale(600/16d), frame, p.getMainHandItem());
    }
    public static AncientsEntity parry(Player p, float damage, AttackFrame frame) {
        return create(p, PARRY, 1, 0, damage, p.getBoundingBox().getCenter(), Vec3.ZERO, frame, p.getMainHandItem());
    }
    private static AncientsEntity create(Player p, int kind, int sign, int charge, float damage, Vec3 at, Vec3 velocity, AttackFrame frame, ItemStack weapon) {
        AncientsEntity e = new AncientsEntity(ArkOfCosmos.ANCIENTS_ATTACK.get(), p.level());
        e.ownerUuid = p.getUUID(); e.ownerId = p.getId(); e.kind = kind; e.sign = sign; e.charge = charge; e.damage = damage;
        e.refreshDimensions();
        e.frame = frame; e.velocity = velocity; e.weapon = weapon.copy(); e.setPos(at);
        e.left = kind == BLADE ? AncientsRules.SWING_UPDATES : kind == BEAM ? AncientsRules.BEAM_FRAMES : kind == STAR ? 120 : kind == BLAST ? 20 : 16;
        e.scale = kind == BLADE ? AncientsRules.scale(0, charge) : kind == BEAM ? 2.4f : kind == STAR ? 1 : 1.4f;
        e.rotation = kind == BLADE ? AncientsRules.rotation(0, sign) : 0;
        e.publish(); p.level().addFreshEntity(e);
        if (kind == BLADE) ArkOfCosmos.sound(e, charge > 0 ? "phoenix" : "fractured_swing", 1);
        if (kind == BLAST) ArkOfCosmos.sound(e,"item84",1);
        if (kind == PARRY) FracturedCombat.sound(e,p,"fractured_parry_start",1,false,false);
        return e;
    }
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
        if (!WeaponUser.active(p) || (kind == BLADE || kind == PARRY || kind == BLAST) && !AncientsCombat.holding(p)) { discardAttack(); return; }
        if (left <= 0) { discardAttack(); return; }
        samples = new ListTag(); samples.add(snapshot());
        stepping = true;
        for (int step = 0, count = kind == BLADE ? 6 : 3; step < count && left > 0 && !ended; step++) {
            int updates = kind == STAR && homing ? 2 : 1;
            for (int extra = 0; extra < updates && left > 0 && !ended; extra++) {
            remember();
            switch (kind) { case BLADE -> blade(p); case BEAM -> beam(p); case PARRY -> parry(p); case STAR -> star(p); case BLAST -> blast(p); }
            age++; left--; samples.add(snapshot());
            }
        }
        stepping = false;
        if (!ended) publish();
    }
    private void blade(Player p) {
        rotation = AncientsRules.rotation(age, sign); scale = AncientsRules.scale(age, charge);
        Vec3 center = p.getBoundingBox().getCenter(), along = frame.direction(rotation);
        setPos(center.add(frame.forward().scale(30/16d)));
        if (!fired && charge > 0 && AncientsRules.ratio(age) > .5f) {
            create(p, BEAM, 1, charge, AncientsRules.beamDamage(damage), position(), frame.forward().scale(24/16d), frame, weapon);
            fired = true;
        }
        Vec3 start = center.add(along.scale(30/16d));
        hitLine(p, start, start.add(along.scale(88 * scale / 16)), 24/16d);
    }
    private void beam(Player p) {
        rotation = (float)(Math.PI/4); scale = 2.4f; velocity = velocity.scale(AncientsRules.beamDeceleration(age));
        if (velocity.length() < 1/16d) { finishBeam(); return; }
        Vec3 old = position();
        if (AncientsRules.beamTiles(age)) {
            // The original has a 32x32 tile hitbox, independent of its elongated damage line.
            Vec3 move = Entity.collideBoundingBox(this, velocity, new AABB(old, old).inflate(1), level(), List.of());
            if (move.distanceToSqr(velocity) > 1e-12) { setPos(old.add(move)); finishBeam(); return; }
        }
        setPos(old.add(velocity));
        Vec3 start = position().subtract(frame.forward().scale(16/16d));
        hitLine(p, start, start.add(frame.forward().scale(44 * scale / 16)), 24/16d);
        if (!ended && left == 1) finishBeam();
    }
    private void star(Player p) {
        if (age > 30) velocity = velocity.scale(.95);
        if (soundDelay-- <= 0) { soundDelay = 20 + random.nextInt(40); if (random.nextInt(5)==0) ArkOfCosmos.sound(this,"elements_item9",1); }
        if (velocity.length() < 2/16d) { finishBeam(); return; }
        rotation += (Math.abs(velocity.dot(frame.forward())) + Math.abs(velocity.dot(frame.right()))) * 16 * .01 * sign;
        LivingEntity target = null; double nearest = Double.MAX_VALUE;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(64), e -> ArkCombat.enemy(p,e))) {
            double distance = position().distanceTo(t.getBoundingBox().getCenter());
            if (distance >= 256/16d + (t.getBbWidth()+t.getBbHeight())/2 || distance >= nearest) continue;
            if (age > 5 && level().clip(new ClipContext(position(),t.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) continue;
            target=t; nearest=distance;
        }
        homing=target!=null;
        if (homing) velocity=velocity.scale(20).add(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(12/16d)).scale(1/21d);
        Vec3 old=position();
        if (age>5) {
            Vec3 move=Entity.collideBoundingBox(this,velocity,new AABB(old,old).inflate(10/16d),level(),List.of());
            if(move.distanceToSqr(velocity)>1e-12){setPos(old.add(move));finishBeam();return;}
        }
        setPos(old.add(velocity));
        // Original 20x20 projectile hitbox, extended to the third axis.
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(10/16d),e->ArkCombat.canHit(p,e))) {
            if(hurt(p,t)){hitEffect(t);finishBeam();return;}
        }
        if(left==1)finishBeam();
    }
    private void blast(Player p) {
        scale=1.4f+(float)Math.sin(age/20d*Math.PI)*.6f;
        hitLine(p,position(),position().add(velocity),20/16d);
    }
    private void parry(Player p) {
        ArkCombat.ParryPose pose = ArkCombat.parryPose(p, frame, age);
        setPos(pose.position()); scale = pose.scale();
        if (!ArkCombat.controls(p).parried) hitLine(p, position(), position().add(frame.forward().scale(142 * scale / 16)), 44/16d);
    }
    private void hitLine(Player p, Vec3 from, Vec3 to, double width) {
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width / 2 + 2), e -> ArkCombat.canHit(p, e))) {
            if (hit.contains(t.getId()) || !ArkGeometry.line(t.getBoundingBox(), from, to, width, frame)) continue;
            if (!hurt(p,t)) continue;
            if (kind == PARRY) { hitEffect(t); ArkCombat.parrySuccess(p); return; }
            if (kind == BEAM) { hitEffect(t); finishBeam(); return; }
            if (kind == BLAST) hitEffect(t);
        }
    }
    private boolean hurt(Player p, LivingEntity t) {
        if(hit.contains(t.getId()))return false;
        float falloff=kind==BLAST ? (float)Math.pow(.25,numHits*.1) : 1;
        float amount=(PlayerWeaponDamage.scale(p,weapon,damage)+EnchantmentHelper.getDamageBonus(weapon,t.getMobType())*damage/AncientsRules.baseDamage())*falloff;
        boolean success=WeaponDamage.hurt(t,WeaponUser.source(this,p),amount);
        if(!success)return false;
        hit.add(t.getId());numHits++;p.setLastHurtMob(t);
        int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);if(fire>0)t.setSecondsOnFire(fire*4);
        EnchantmentHelper.doPostHurtEffects(t,p);
        weapon.getAllEnchantments().forEach((enchantment,level)->enchantment.doPostAttack(p,t,level));
        double knockback=kind==BLAST?0:kind==BEAM?2:6.5;
        knockback+=weapon.getEnchantmentLevel(Enchantments.KNOCKBACK);
        if(knockback>0){Vec3 away=t.position().subtract(p.position()).normalize();t.knockback(knockback*.1,-away.x,-away.z);}
        return true;
    }
    public void parrySucceeded() {
        if (kind != PARRY || parriedAt >= 0) return;
        parriedAt = age;
        CompoundTag data = effect("ancients_parry", position());
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
        CompoundTag data=effect("ancients_hit",at);data.putUUID("Target",target.getUUID());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,data);
    }
    private void finishBeam() { if (!ended) FracturedCombat.sound(this,owner(),"fractured_beam_death",kind==STAR?.5f:1,false,false); discardAttack(); }
    public void discardAttack() {
        if (!ended) {
            CompoundTag data = effect("ancients_end", position());
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
        int count = kind == BLADE ? 10 : kind == STAR ? 8 : 5; while (trail.size() > count) trail.remove(trail.size() - 1);
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
