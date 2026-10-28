package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.calamity.entity.CalamityAttackEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.SweepingEdgeEnchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;
import java.util.List;

public final class ArkAttackEntity extends CalamityAttackEntity {
    public enum Kind { BLADE, NEEDLE, BOLT, CHAIN, PARRY, BLAST }
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(ArkAttackEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private UUID ownerUuid;
    private int ownerEntityId;
    private net.minecraft.nbt.ListTag samples = new net.minecraft.nbt.ListTag();
    private CompoundTag clientSnapshot = new CompoundTag();

    public ArkAttackEntity(EntityType<? extends ArkAttackEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static ArkAttackEntity create(Player owner, Kind kind, int mode, int charge, float damage,
                                          Vec3 position, Vec3 velocity, AttackFrame frame) {
        ArkAttackEntity attack = new ArkAttackEntity(ArkOfCosmos.ATTACK.get(), owner.level());
        attack.weapon = owner.getMainHandItem().copy();
        attack.ownerUuid = owner.getUUID();
        attack.ownerEntityId = owner.getId();
        attack.attackKind = kind;
        attack.mode = mode;
        attack.charge = charge;
        attack.damage = damage;
        attack.velocity = velocity;
        attack.direction = frame.forward();
        attack.frame = frame;
        attack.lifetime = switch (kind) {
            case BLADE -> mode == 2 ? 140 : mode == 1 ? 55 : 35;
            case NEEDLE -> 30;
            case BOLT -> 100;
            case CHAIN -> 52;
            case PARRY -> 16;
            case BLAST -> 70;
        };
        attack.left = attack.lifetime;
        attack.setPos(position);
        attack.aim = position;
        attack.publish();
        owner.level().addFreshEntity(attack);
        if (kind == Kind.BLADE) ArkOfCosmos.sound(attack, charge > 0 || mode == 2 ? "phoenix" : "item71", 1);
        if (kind == Kind.PARRY || kind == Kind.BLAST) ArkOfCosmos.sound(attack, "item84", .3f);
        return attack;
    }

    @Override protected void defineSynchedData() { entityData.define(VIEW, new CompoundTag()); }
    public CompoundTag view() { return entityData.get(VIEW); }
    public Kind kind() { return level().isClientSide ? Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, view().getInt("Kind")))] : attackKind; }
    public ItemStack weapon() { return weapon; }
    public int ownerId() { return level().isClientSide ? view().getInt("Owner") : ownerEntityId; }
    public AttackFrame attackFrame() { return level().isClientSide ? new AttackFrame(vector(view(), "F"), vector(view(), "R")) : frame; }
    private CompoundTag sample() {
        CompoundTag tag = new CompoundTag();
        vector(tag, "Position", position()); vector(tag, "Aim", aim); vector(tag, "Direction", direction); vector(tag, "Velocity", velocity);
        tag.putFloat("Rotation", rotation); tag.putFloat("Scale", scale);
        tag.putInt("Mode", mode); tag.putInt("Left", left); tag.putInt("Steps", steps);
        Player owner = ownerUuid == null ? null : level().getPlayerByUUID(ownerUuid);
        if (owner != null) {
            vector(tag, "Center", owner.getBoundingBox().getCenter());
            vector(tag, "OwnerVelocity", owner.getDeltaMovement().scale(1d / 3));
        }
        return tag;
    }
    private CompoundTag renderView() { return level().isClientSide && !clientSnapshot.isEmpty() ? clientSnapshot : view(); }
    private CompoundTag sample(float partial, boolean next) {
        net.minecraft.nbt.ListTag path = renderView().getList("Samples", 10);
        if (path.isEmpty()) return renderView();
        double index = Math.max(0, Math.min(1, partial)) * (path.size() - 1);
        return path.getCompound(Math.min(path.size() - 1, (int)index + (next ? 1 : 0)));
    }
    private float fraction(float partial) {
        double index = Math.max(0, Math.min(1, partial)) * Math.max(0, renderView().getList("Samples", 10).size() - 1);
        return (float)(index - Math.floor(index));
    }
    public CompoundTag renderState(float partial) {
        CompoundTag state = renderView().copy(), sample = sample(partial, false);
        state.putInt("Mode", sample.getInt("Mode")); state.putInt("Left", sample.getInt("Left")); return state;
    }
    public float renderRotation(float partial) { return net.minecraft.util.Mth.lerp(fraction(partial), sample(partial, false).getFloat("Rotation"), sample(partial, true).getFloat("Rotation")); }
    public float renderScale(float partial) { return net.minecraft.util.Mth.lerp(fraction(partial), sample(partial, false).getFloat("Scale"), sample(partial, true).getFloat("Scale")); }
    private Vec3 renderVector(String key, float partial) { return vector(sample(partial, false), key).lerp(vector(sample(partial, true), key), fraction(partial)); }
    public Vec3 renderPosition(float partial) { return renderVector("Position", partial); }
    public Vec3 renderAim(float partial) { return renderVector("Aim", partial); }
    public Vec3 renderVelocity(float partial) { return renderVector("Velocity", partial); }
    public Vec3 renderDirection(float partial) { return renderVector("Direction", partial); }
    public Vec3[] renderSamples(float partial) {
        net.minecraft.nbt.ListTag path = renderView().getList("Samples", 10);
        Vec3[] result = new Vec3[path.size()];
        for (int i = 0; i < result.length; i++) result[i] = vector(path.getCompound(i), "Position");
        return result;
    }
    public CompoundTag effectSnapshot() { return renderView(); }
    public float renderLeft(float partial) { return net.minecraft.util.Mth.lerp(fraction(partial), sample(partial, false).getInt("Left"), sample(partial, true).getInt("Left")); }
    public static Vec3 vector(CompoundTag tag, String key) { return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z")); }
    private static void vector(CompoundTag tag, String key, Vec3 v) { tag.putDouble(key + "X", v.x); tag.putDouble(key + "Y", v.y); tag.putDouble(key + "Z", v.z); }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (level().isClientSide && key.equals(VIEW) && !view().isEmpty()) {
            setPos(vector(view(), "Position"));
            if (clientSnapshot.isEmpty()) clientSnapshot = view().copy();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int increments, boolean teleport) {
        // Synced snapshots include exact substep positions; vanilla movement interpolation must not overwrite them.
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            boolean fresh = clientSnapshot.isEmpty() || view().getInt("Steps") != clientSnapshot.getInt("Steps");
            clientSnapshot = view().copy();
            // Commit snapshots on client tick boundaries. Late packets must not change the path mid-frame,
            // and an unchanged snapshot must hold its endpoint rather than replay its last trajectory.
            if (!fresh) clientSnapshot.remove("Samples");
            return;
        }
        Player owner = ownerUuid == null ? null : level().getPlayerByUUID(ownerUuid);
        if (owner == null || !owner.isAlive() || owner.isSpectator()) { discard(); return; }
        samples = new net.minecraft.nbt.ListTag();
        samples.add(sample());
        for (int i = 0, count = attackKind == Kind.BLADE ? 6 : 3; i < count && !isRemoved(); i++) {
            switch (attackKind) {
                case BLADE -> blade(owner);
                case NEEDLE -> needle();
                case BOLT -> bolt(owner);
                case CHAIN -> chain(owner);
                case PARRY -> parry(owner);
                case BLAST -> blast(owner);
            }
            if (isRemoved()) break;
            hit(owner);
            steps++;
            --left;
            samples.add(sample());
            if (left <= 0) finish(owner);
        }
        if (!isRemoved()) publish();
    }

    @Override protected boolean hurtTarget(Player owner, LivingEntity target, float amount, double sweepKnockback) {
        int fire = weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);
        boolean startedFire = fire > 0 && !target.isOnFire();
        // As in Player.attack, ignite before lethal damage so fire-aspect kills cook drops.
        if (startedFire) target.setSecondsOnFire(1);
        int oldImmunity = target.invulnerableTime;
        target.invulnerableTime = 0;
        DamageSource source = new DamageSource(damageSources().playerAttack(owner).typeHolder(), this, owner);
        applyArcOfCosmosDamage(target, source, amount);
        boolean hurt = target.hurt(source, amount);
        target.invulnerableTime = Math.max(oldImmunity, target.invulnerableTime);
        if (!hurt) {
            if (startedFire) target.clearFire();
            return false;
        }
        if (fire > 0) target.setSecondsOnFire(fire * 4);
        double knockback = sweepKnockback + weapon.getEnchantmentLevel(Enchantments.KNOCKBACK) * .5;
        if (knockback > 0) {
            Vec3 toward = target.position().subtract(owner.position());
            if (toward.horizontalDistanceSqr() < 1e-8) toward = frame.forward();
            target.knockback(knockback, -toward.x, -toward.z);
        }
        owner.setLastHurtMob(target);
        EnchantmentHelper.doPostHurtEffects(target, owner);
        // Use the casting sword, even if the player changes slots while it is airborne.
        postAttack(weapon, owner, target);
        for (EquipmentSlot slot : EquipmentSlot.values())
            if (slot != EquipmentSlot.MAINHAND) postAttack(owner.getItemBySlot(slot), owner, target);
        return true;
    }

    /** Applies the ArcOfCosmos one-third chance for an additional half-damage hit. */
    private void applyArcOfCosmosDamage(LivingEntity target, DamageSource source, float amount) {
        if (target instanceof Player player && player.isCreative()) return;
        if (target.getRandom().nextInt(3) == 0) {
            int oldInvulnerability = target.invulnerableTime;
            target.invulnerableTime = 0;
            target.hurt(source, amount * .5f);
            target.invulnerableTime = Math.max(oldInvulnerability, target.invulnerableTime);
        }
    }

    private static void postAttack(ItemStack stack, Player owner, LivingEntity target) {
        stack.getAllEnchantments().forEach((enchantment, level) -> {
            if (enchantment != Enchantments.UNBREAKING && enchantment != Enchantments.MENDING)
                enchantment.doPostAttack(owner, target, level);
        });
    }

    @Override protected void sweep(Player owner, LivingEntity primary, float amount, int cooldown) {
        int level = weapon.getEnchantmentLevel(Enchantments.SWEEPING_EDGE);
        if (level <= 0) return;
        float sweepDamage = 1 + SweepingEdgeEnchantment.getSweepingDamageRatio(level) * amount;
        for (LivingEntity secondary : level().getEntitiesOfClass(LivingEntity.class, weapon.getSweepHitBox(owner, primary), e -> ArkCombat.canHit(owner, e))) {
            // Targets inside the original attack geometry still receive their full direct hit.
            if (secondary == primary || collides(secondary.getBoundingBox(), owner)
                    || steps < immunity.getOrDefault(secondary.getId(), -1)) continue;
            immunity.put(secondary.getId(), steps + cooldown);
            hurtTarget(owner, secondary, sweepDamage, .4);
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (!isRemoved() && !level().isClientSide) {
            effect("death", position());
            if (attackKind == Kind.NEEDLE) ArkOfCosmos.sound(this, "needle_death", 1);
            if (attackKind == Kind.BLADE && mode == 3) ArkOfCosmos.sound(this, "item84", 1);
        }
        super.remove(reason);
    }
    @Override protected void effect(String event, Vec3 at) {
        CompoundTag tag = view().copy();
        tag.putInt("Charge",charge);tag.putInt("Life",lifetime);tag.putInt("Steps",steps);tag.putInt("Left",left);
        net.minecraft.nbt.ListTag path=samples.copy();
        if(event.equals("death")&&(path.isEmpty()||path.getCompound(path.size()-1).getInt("Steps")<steps+1)&&left>0){
            CompoundTag terminal=sample();terminal.putInt("Steps",steps+1);terminal.putInt("Left",left-1);path.add(terminal);
        }
        tag.put("Samples",path);
        tag.putInt("Kind", attackKind.ordinal()); tag.putInt("Owner", ownerEntityId);
        tag.putInt("Mode", mode); tag.putInt("Id", getId()); tag.putString("Event", event);
        vector(tag, "At", at); vector(tag, "Position", position()); vector(tag, "Velocity", velocity);
        vector(tag, "Direction", direction); vector(tag, "F", frame.forward()); vector(tag, "R", frame.right());
        ArkNetwork.effect(this, tag);
    }
    @Override protected void advanceDash(Player owner) {
        if (!dashing || !(owner instanceof ServerPlayer server)) return;
        Vec3 feetTarget = aim.subtract(0, owner.getBbHeight() / 2, 0);
        Vec3 offset = feetTarget.subtract(owner.position());
        double distance = offset.length();
        Vec3 movement = offset.normalize().scale(Math.min(60 * ArkCombat.PIXEL, distance));
        AABB destinationBox = owner.getBoundingBox().move(offset).deflate(1e-5);
        boolean clearDestination = level().getBlockCollisions(owner, destinationBox).iterator().hasNext() == false;
        Vec3 before = owner.position();
        if (clearDestination) server.teleportTo(owner.getX() + movement.x, owner.getY() + movement.y, owner.getZ() + movement.z);
        else {
            // Dash collision considers blocks only: sneaking and entities must not stop it.
            Vec3 allowed = Entity.collideBoundingBox(owner, movement, owner.getBoundingBox(), level(), java.util.List.of());
            server.teleportTo(before.x + allowed.x, before.y + allowed.y, before.z + allowed.z);
        }
        owner.fallDistance = 0;
        // Position is advanced at the original 60 Hz, not multiplied again by vanilla movement.
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        boolean reached = owner.position().distanceTo(feetTarget) < 60 * ArkCombat.PIXEL;
        boolean blocked = !clearDestination && owner.position().distanceToSqr(before.add(movement)) > 1e-6;
        if (reached || blocked) {
            dashing = false;
            if (reached) {
                ArkOfCosmos.sound(this, "meaty", 1);
                if (clearDestination) server.teleportTo(feetTarget.x, feetTarget.y, feetTarget.z);
                owner.setDeltaMovement(offset.normalize().scale(60 * ArkCombat.PIXEL * 3 * .1));
                for (int i = 0; i < 5; i++) spawnBolt(owner, owner.getBoundingBox().getCenter(), frame.direction(random.nextDouble() * Math.PI * 2).scale(28 * ArkCombat.PIXEL), .2f, .21991149f);
            }
        }
    }

    private void publish() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Kind", attackKind.ordinal()); tag.putInt("Owner", ownerEntityId);
        tag.putInt("Mode", mode); tag.putInt("Charge", charge); tag.putInt("Left", left); tag.putInt("Life", lifetime);
        tag.putInt("Steps", steps); tag.putFloat("Rotation", rotation); tag.putFloat("Scale", scale);
        vector(tag, "Position", position()); vector(tag, "Aim", aim); vector(tag, "Direction", direction); vector(tag, "Velocity", velocity);
        vector(tag, "F", frame.forward()); vector(tag, "R", frame.right());
        tag.put("Samples", samples.copy());
        entityData.set(VIEW, tag);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean isPickable() { return false; }
}
