package net.everla.projectc.arkofcosmos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class ArkAttackEntity extends Entity {
    public enum Kind { BLADE, NEEDLE, BOLT, CHAIN, PARRY, BLAST }
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(ArkAttackEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private Kind attackKind = Kind.BLADE;
    private ItemStack weapon = ItemStack.EMPTY;
    private int lastStitches;
    private UUID ownerUuid;
    private int ownerEntityId, mode, charge, left, lifetime, steps, hits, targetId = -1;
    private float damage, rotation, scale = 1, homing = (float)Math.PI / 20;
    private AttackFrame frame = new AttackFrame(new Vec3(0, 0, 1), new Vec3(-1, 0, 0));
    private Vec3 velocity = Vec3.ZERO, direction = Vec3.ZERO, aim = Vec3.ZERO;
    private Vec3 smoothAim;
    private float snapStartRotation;
    private boolean missed, chainCreated, dashing, dashStarted, snapChargeGranted;
    private final Map<Integer, Integer> immunity = new HashMap<>();
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
    ItemStack weapon() { return weapon; }
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
    CompoundTag effectSnapshot() { return renderView(); }
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

    private void blade(Player owner) {
        Vec3 center = owner.getBoundingBox().getCenter();
        if (mode < 2) {
            float completion = (lifetime - left) / (float)lifetime;
            rotation = mode == 0 ? ArkCurves.lerp((float)Math.PI * 3 / 8, -(float)Math.PI * 3 / 8, ArkCurves.swing(completion))
                    : ArkCurves.lerp(-(float)Math.PI * 3 / 4, 7.4612827f, ArkCurves.swirl(completion));
            setPos(center.add(frame.forward().scale(30 * ArkCombat.PIXEL)));
            scale = mode == 1 ? 1.6f + (float)Math.sin(ArkCurves.swirl(completion) * Math.PI) + charge / 10f * .05f
                    : 1.2f + (float)Math.sin(ArkCurves.swing(completion) * Math.PI) * .6f + charge / 10f * .2f;
            if (mode == 1 && (left - 1) % 10 == 0) {
                double angle = rotation - Math.PI * 23 / 80;
                spawnBolt(owner, center.add(frame.direction(angle).scale(10 * ArkCombat.PIXEL)), frame.direction(angle).scale(20 * ArkCombat.PIXEL), .7f / 6, homing);
            }
            return;
        }
        float completion = (140 - left) / 140f;
        Vec3 desiredAim = ArkCombat.aim(owner);
        smoothAim = smoothAim == null ? desiredAim : smoothAim.lerp(desiredAim, 1 - Math.exp(-(attackKind == Kind.BLADE ? 1d / 120 : 1d / 60) / .10));
        aim = smoothAim;
        if (!chainCreated && Math.abs(completion - .25f) <= .005f && !missed) {
            ArkAttackEntity chain = create(owner, Kind.CHAIN, 0, 0, truncateDamage(.1f), center, Vec3.ZERO, frame);
            chain.weapon = weapon.copy();
            chain.left = chain.lifetime = left / 2;
            chainCreated = true;
            ArkOfCosmos.sound(this, "item4", 1);
        }
        float ratio = ArkCurves.throwing(completion);
        setPos(ArkGeometry.towards(position().lerp(aim, .025 * ratio), aim, 20 * ArkCombat.PIXEL * ratio));
        Vec3 offset = position().subtract(center);
        if (offset.length() > ArkCombat.MAX_REACH) setPos(center.add(offset.normalize().scale(ArkCombat.MAX_REACH)));
        rotation -= .23561947f;
        scale = 1 + ArkCurves.throwScale(completion) * .5f;
        if (Math.abs(completion - .75f) <= .005f) direction = position().subtract(center);
        if (completion > .75f) setPos(center.add(direction.scale(ratio)));
        if (!ArkCombat.channeling(owner) && mode == 2 && !missed) {
            if (completion >= .25f && completion < .75f) {
                double start = random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < 3; i++) {
                    Vec3 radial = frame.direction(start + Math.PI * 2 * i / 3);
                    spawnBolt(owner, position().add(radial.scale(30 * ArkCombat.PIXEL)), radial.scale(20 * ArkCombat.PIXEL), .1f, homing);
                }
                immunity.clear();
                mode = 3;
                direction = position().subtract(center);
                snapStartRotation = rotation;
                left = 35;
            } else missed = true;
        }
        if (mode == 3) {
            float t = (35 - left) / 35f;
            float shrink = ArkCurves.lerp(1, .8f, 1 - (float)Math.sqrt(1 - t * t));
            setPos(center.add(direction.scale(shrink)));
            scale = 1.5f;
            double targetAngle = frame.angle(direction);
            // Terraria's atan2 always normalizes the saved velocity angle to [-pi, pi].
            float source = (float)Math.atan2(Math.sin(snapStartRotation), Math.cos(snapStartRotation));
            float turn = (float)(targetAngle + (targetAngle + Math.PI / 4 > source ? -Math.PI * 2 : 0));
            float amount = (float)Math.sqrt(1 - Math.pow(ArkCurves.clamp(t + .2f) - 1, 2));
            rotation = ArkCurves.lerp(source, turn, amount);
        }
    }

    private void needle() {
        float timer = 30 - left;
        scale = 2.4f;
        velocity = velocity.scale(1 - Math.pow(timer / 30, 3) * .3);
        rotation = (float)frame.angle(velocity);
        setPos(position().add(velocity));
        if (velocity.length() < ArkCombat.PIXEL) discard();
    }

    private void bolt(Player owner) {
        if (targetId < 0) {
            LivingEntity target = ArkCombat.nearest(owner, position(), 812 * ArkCombat.PIXEL);
            if (target != null) targetId = target.getId();
        } else {
            Entity target = level().getEntity(targetId);
            if (target instanceof LivingEntity living && ArkCombat.enemy(owner, living)) {
                Vec3 offset = target.getBoundingBox().getCenter().subtract(position());
                if (velocity.normalize().dot(offset.normalize()) > -1 + 1e-10)
                    velocity = ArkGeometry.turn(velocity, offset, homing, frame.normal());
            } else targetId = -1;
        }
        rotation = (float)frame.angle(velocity);
        setPos(position().add(velocity));
    }

    private void chain(Player owner) {
        setPos(owner.getBoundingBox().getCenter());
        Vec3 desiredAim = ArkCombat.aim(owner);
        smoothAim = smoothAim == null ? desiredAim : smoothAim.lerp(desiredAim, 1 - Math.exp(-(attackKind == Kind.BLADE ? 1d / 120 : 1d / 60) / .10));
        aim = smoothAim;
        if (!ArkCombat.channeling(owner) && left > 20) left = 20;
    }

    private void parry(Player owner) {
        float progress = steps / 15f;
        setPos(owner.getBoundingBox().getCenter().add(frame.forward().scale((10 + 10 * ArkCurves.parryThrust(progress)) * ArkCombat.PIXEL)));
        scale = 1.4f + ArkCurves.parryThrust(progress) * .2f;
        aim = owner.getBoundingBox().getCenter().add(frame.forward().scale((15 + 50 * ArkCurves.parryThrust(progress)) * ArkCombat.PIXEL));
    }

    private void blast(Player owner) {
        int timer = 70 - left;
        float snap = ArkCurves.clamp(timer / 25f);
        scale = 1.4f;
        aim = position().add(frame.forward().scale(ArkCurves.blastThrust(snap) * 200 * ArkCombat.PIXEL));
        float stitchProgress = ArkCurves.clamp((timer - 32.5f) / 37.5f);
        int stitches = (int)Math.ceil((1 - Math.sqrt(1 - Math.pow(Math.min(stitchProgress * 3, 1), 2))) * 8);
        while (lastStitches < stitches) { ArkOfCosmos.sound(this, "item" + (63 + lastStitches % 3), .5f); lastStitches++; }
        if (timer == 26 && !dashStarted && charge >= 5 && ArkCombat.controls(owner).alt && ArkCombat.controls(owner).right) {
            dashStarted = true;
            dashing = true;
            ArkCombat.controls(owner).dashInvulnerableUntil = level().getGameTime() + 4; // ceil(10/60*20).
        }
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

    private void spawnBolt(Player owner, Vec3 position, Vec3 velocity, float multiplier, float strength) {
        ArkAttackEntity bolt = create(owner, Kind.BOLT, 0, 0, truncateDamage(multiplier), position, velocity, frame);
        bolt.weapon = weapon.copy();
        bolt.homing = strength;
    }
    private float truncateDamage(float multiplier) { return ArkCombat.scaled(damage, multiplier); }

    private void hit(Player owner) {
        if (damage <= 0 || attackKind == Kind.PARRY && (steps > 15 || hits > 0 || ArkCombat.controls(owner).parried) || attackKind == Kind.BLAST && 70 - left <= 25) return;
        AABB search = getBoundingBox().inflate(4);
        if (attackKind == Kind.BLADE || attackKind == Kind.CHAIN) search = search.minmax(owner.getBoundingBox()).inflate(34);
        if (attackKind == Kind.PARRY) search = search.inflate(16);
        if (attackKind == Kind.BLAST) search = search.minmax(new AABB(position(), aim)).inflate(12);
        List<LivingEntity> targets;
        if (attackKind == Kind.BLADE && mode == 3) {
            // The DLL's unnormalized snap line can be thousands of blocks long. Like Terraria's
            // NPC array, inspect loaded entities, rather than iterating an enormous chunk region.
            targets = new ArrayList<>();
            for (Entity entity : ((ServerLevel)level()).getAllEntities())
                if (entity instanceof LivingEntity living && ArkCombat.canHit(owner, living)) targets.add(living);
        } else targets = level().getEntitiesOfClass(LivingEntity.class, search, e -> ArkCombat.canHit(owner, e));
        for (LivingEntity target : targets) {
            if (steps < immunity.getOrDefault(target.getId(), -1) || !collides(target.getBoundingBox(), owner)) continue;
            int cooldown = switch (attackKind) {
                case BLADE -> mode == 3 ? 35 : mode == 2 ? 10 : mode == 1 ? 8 : 35;
                case CHAIN -> 15;
                case BLAST -> 72;
                default -> Integer.MAX_VALUE / 2;
            };
            immunity.put(target.getId(), steps + cooldown);
            float amount = damage;
            if (attackKind == Kind.BLADE && mode == 3) amount *= 1.2f;
            if (attackKind == Kind.BLAST) amount *= (float)Math.pow(.25, hits * .1f);
            // Damage enchantments follow this attack's original multiplier, including small derived bolts.
            amount += EnchantmentHelper.getDamageBonus(weapon, target.getMobType()) * (amount / ArkCombat.BASE_DAMAGE);
            if (!hurtTarget(owner, target, amount, 0)) continue;
            Vec3 effectPosition=target.getBoundingBox().getCenter();
            if((attackKind==Kind.NEEDLE||attackKind==Kind.PARRY)&&Math.hypot(target.getBbWidth(),target.getBbHeight())*16>=140)
                effectPosition=position().add(frame.direction(rotation+(attackKind==Kind.NEEDLE?Math.PI/2:0)).scale(60d/16));
            effect("hit", effectPosition);
            // Award once per snap, only after its blade actually damages a target.
            if (attackKind == Kind.BLADE && mode == 3 && !snapChargeGranted) {
                ArkItem.charge(owner.getMainHandItem(), ArkItem.charge(owner.getMainHandItem()) + 5);
                snapChargeGranted = true;
            }
            sweep(owner, target, amount, cooldown);
            hits++;
            if (attackKind == Kind.PARRY) { ArkCombat.parrySuccess(owner); return; }
            if (attackKind == Kind.BLADE && mode == 3) level().playSound(null, blockPosition(), ArkOfCosmos.SNAP.get(), SoundSource.PLAYERS, 1.3f, 1);
            if (attackKind == Kind.BOLT || attackKind == Kind.NEEDLE && hits >= 3) { discard(); return; }
        }
    }

    private boolean hurtTarget(Player owner, LivingEntity target, float amount, double sweepKnockback) {
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

    private void sweep(Player owner, LivingEntity primary, float amount, int cooldown) {
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

    private boolean collides(AABB box, Player owner) {
        Vec3 center = owner.getBoundingBox().getCenter();
        if (attackKind == Kind.BLADE) {
            double length = 172 * scale * ArkCombat.PIXEL;
            if (mode >= 2) {
                boolean square = ArkGeometry.box(box, position(), frame.forward(), frame.right(), frame.normal(), length / 2, length / 2, 30 * ArkCombat.PIXEL);
                if (mode == 2 || square) return square;
                float progress = ArkCurves.clamp((35 - left) / 35f + .25f);
                double smooth = progress * progress * (3 - 2 * progress);
                Vec3 start = center.lerp(position(), smooth);
                // Explicitly confirmed by the user: preserve raw pixel vector * raw pixel length.
                // direction is stored in blocks, so restore the second operand's pixel units here.
                return ArkGeometry.line(box, start, start.add(direction.scale(length / ArkCombat.PIXEL)), 0, frame);
            }
            Vec3 along = frame.direction(rotation);
            Vec3 start = center.add(along.scale(30 * ArkCombat.PIXEL));
            return ArkGeometry.line(box, start, start.add(along.scale(length)), 24 * ArkCombat.PIXEL, frame);
        }
        if (attackKind == Kind.PARRY) {
            return ArkGeometry.line(box, position(), position().add(frame.forward().scale(142 * scale * ArkCombat.PIXEL)), 44 * ArkCombat.PIXEL, frame);
        }
        if (attackKind == Kind.NEEDLE) {
            Vec3 along = velocity.normalize(), start = position().subtract(along.scale(16 * ArkCombat.PIXEL));
            return ArkGeometry.line(box, start, start.add(along.scale(44 * scale * ArkCombat.PIXEL)), 24 * ArkCombat.PIXEL, frame);
        }
        if (attackKind == Kind.BOLT) return box.intersects(new AABB(position(), position()).inflate(15 * ArkCombat.PIXEL));
        if (attackKind == Kind.CHAIN) return ArkGeometry.line(box, position(), aim, 30 * ArkCombat.PIXEL, frame);
        if (attackKind == Kind.BLAST) {
            double length = ArkCurves.blastThrust(ArkCurves.clamp((70 - left) / 25f)) * 242 * ArkCombat.PIXEL;
            return ArkGeometry.line(box, position(), position().add(frame.forward().scale(length)), 30 * ArkCombat.PIXEL, frame);
        }
        return false;
    }

    @Override public void remove(RemovalReason reason) {
        if (!isRemoved() && !level().isClientSide) {
            effect("death", position());
            if (attackKind == Kind.NEEDLE) ArkOfCosmos.sound(this, "needle_death", 1);
            if (attackKind == Kind.BLADE && mode == 3) ArkOfCosmos.sound(this, "item84", 1);
        }
        super.remove(reason);
    }
    private void effect(String event, Vec3 at) {
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
    private void finish(Player owner) {
        if (dashing) { owner.setDeltaMovement(owner.getDeltaMovement().scale(.1)); owner.hurtMarked = true; }
        discard();
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
