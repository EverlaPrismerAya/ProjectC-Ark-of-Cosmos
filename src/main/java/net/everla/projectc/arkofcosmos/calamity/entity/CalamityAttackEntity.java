package net.everla.projectc.arkofcosmos.calamity.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.item.ArkItem;
import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.util.ArkGeometry;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;


/** Calamity Team attack phases, timing and hit rules, adapted to Minecraft units.
 * Minecraft lifecycle, synchronization, enchantments and dash collision live in ArkAttackEntity.
 * Derived code retains the terms in THIRD_PARTY_NOTICES.md; this is not a standalone MIT file.
 */
public abstract class CalamityAttackEntity extends Entity {
    protected Kind attackKind = Kind.BLADE;
    protected ItemStack weapon = ItemStack.EMPTY;
    protected int lastStitches;
    protected int mode, charge, left, lifetime, steps, hits, targetId = -1;
    protected float damage, rotation, scale = 1, homing = (float)Math.PI / 20;
    protected AttackFrame frame = new AttackFrame(new Vec3(0, 0, 1), new Vec3(-1, 0, 0));
    protected Vec3 velocity = Vec3.ZERO, direction = Vec3.ZERO, aim = Vec3.ZERO;
    protected Vec3 smoothAim;
    protected float snapStartRotation;
    protected boolean missed, chainCreated, dashing, dashStarted, snapChargeGranted;
    protected final Map<Integer, Integer> immunity = new HashMap<>();

    protected CalamityAttackEntity(EntityType<?> type, Level level) { super(type, level); }

    protected abstract boolean hurtTarget(Player owner, LivingEntity target, float amount, double sweepKnockback);
    protected abstract void sweep(Player owner, LivingEntity primary, float amount, int cooldown);
    protected abstract void effect(String event, Vec3 at);
    protected abstract void advanceDash(Player owner);

    protected void blade(Player owner) {
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
            CalamityAttackEntity chain = ArkAttackEntity.create(owner, Kind.CHAIN, 0, 0, truncateDamage(.1f), center, Vec3.ZERO, frame);
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

    protected void needle() {
        float timer = 30 - left;
        scale = 2.4f;
        velocity = velocity.scale(1 - Math.pow(timer / 30, 3) * .3);
        rotation = (float)frame.angle(velocity);
        setPos(position().add(velocity));
        if (velocity.length() < ArkCombat.PIXEL) discard();
    }

    protected void bolt(Player owner) {
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

    protected void chain(Player owner) {
        setPos(owner.getBoundingBox().getCenter());
        Vec3 desiredAim = ArkCombat.aim(owner);
        smoothAim = smoothAim == null ? desiredAim : smoothAim.lerp(desiredAim, 1 - Math.exp(-(attackKind == Kind.BLADE ? 1d / 120 : 1d / 60) / .10));
        aim = smoothAim;
        if (!ArkCombat.channeling(owner) && left > 20) left = 20;
    }

    protected void parry(Player owner) {
        float progress = steps / 15f;
        setPos(owner.getBoundingBox().getCenter().add(frame.forward().scale((10 + 10 * ArkCurves.parryThrust(progress)) * ArkCombat.PIXEL)));
        scale = 1.4f + ArkCurves.parryThrust(progress) * .2f;
        aim = owner.getBoundingBox().getCenter().add(frame.forward().scale((15 + 50 * ArkCurves.parryThrust(progress)) * ArkCombat.PIXEL));
    }

    protected void blast(Player owner) {
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
        advanceDash(owner);
    }

    protected void spawnBolt(Player owner, Vec3 position, Vec3 velocity, float multiplier, float strength) {
        CalamityAttackEntity bolt = ArkAttackEntity.create(owner, Kind.BOLT, 0, 0, truncateDamage(multiplier), position, velocity, frame);
        bolt.weapon = weapon.copy();
        bolt.homing = strength;
    }
    protected float truncateDamage(float multiplier) { return ArkCombat.scaled(damage, multiplier); }

    protected void hit(Player owner) {
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

    protected boolean collides(AABB box, Player owner) {
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

    protected void finish(Player owner) {
        if (dashing) { owner.setDeltaMovement(owner.getDeltaMovement().scale(.1)); owner.hurtMarked = true; }
        discard();
    }

}
