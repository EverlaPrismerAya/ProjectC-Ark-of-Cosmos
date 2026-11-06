package net.everla.projectc.arkofcosmos.calamity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Death-mode ordinary-world brother AI, Calamity 1a8cebd. Units are source
 * pixels/frame, with upward Minecraft Y. The entity commits movement and damage. */
public final class SupremeCalamitasBrothers {
    public enum Kind { CATACLYSM, CATASTROPHE }
    public record Cue(String sound, float volume, float octaves) {}
    private final List<Cue> cues = new ArrayList<>(3);
    public List<Cue> cues() { return List.copyOf(cues); }
    private void cue(String name, float volume, float octaves) { cues.add(new Cue(name, volume, octaves)); }
    public record Shot(Vec3 position, Vec3 velocity, int variant, int hand, int damage) {}
    private final Kind kind;
    private int bigTimer = 500, limit = 7, vertical, combo, dashTimer, dashes, facing = 1;
    private double delay, counter;
    private boolean movingUp, partnerAlive = true, enrageRoar = true, phase2, side, hand;
    private Vec3 velocity = Vec3.ZERO, offset = Vec3.ZERO;

    public SupremeCalamitasBrothers(Kind kind, boolean movingUp) {
        this.kind = java.util.Objects.requireNonNull(kind);
        this.movingUp = movingUp;
    }
    public Vec3 velocity() { return velocity; }
    public boolean movingUp() { return movingUp; }
    public boolean cachedPartnerAlive() { return partnerAlive; }
    public boolean phase2() { return phase2; }
    /** Applied by the owning native entity only after the single revival is spent. */
    public double healthCap(boolean revivalAvailable) { return !revivalAvailable && !partnerAlive ? .65 : 1; }

    public void step(Vec3 position, Vec3 target, Vec3 targetVelocity, Vec3 planeX,
            double ownRatio, double partnerRatio, boolean actualPartnerAlive,
            RandomGenerator random, Consumer<Shot> emit) {
        if (!finite(position) || !finite(target) || !finite(targetVelocity) || !finite(planeX)
                || Math.abs(planeX.lengthSqr() - 1) > 1e-5 || Math.abs(planeX.y) > 1e-5
                || !Double.isFinite(ownRatio) || ownRatio < 0 || ownRatio > 1
                || !Double.isFinite(partnerRatio) || partnerRatio < 0 || partnerRatio > 1)
            throw new IllegalArgumentException("Invalid brother combat input");
        cues.clear();
        boolean fist = kind == Kind.CATACLYSM;
        if (bigTimer > 0) bigTimer--;
        double ratio = (ownRatio + partnerRatio) * .5;
        if (partnerAlive && ownRatio < .6 && enrageRoar) {
            enrageRoar = false; cue("brother_roar", 1.5f, .3f);
        }
        phase2 = !partnerAlive || ownRatio < .6;
        if (phase2 && bigTimer > 400) bigTimer = 400;
        if (bigTimer > 0) limit = Math.max(limit, fist ? (!partnerAlive ? 29 : 7) : (phase2 ? 11 : 7));
        Vec3 aim = direction(target.subtract(position));
        Vec3 axis = CalamitasSpace.spreadAxis(aim, random);
        int previousFacing = facing;
        if (bigTimer > 50) {
            int speed = (int)Math.rint(2 + 4.5 * (1 - ratio));
            int increment = phase2 ? (int)(speed * 1.5) : speed;
            if (movingUp) { if (vertical < 400) vertical += increment; else movingUp = false; }
            else { if (vertical > -400) vertical -= increment; else movingUp = true; }
            double horizontal = (side ? -750 : 750) * (fist ? 1 : -1);
            Vec3 distance = target.add(planeX.scale(horizontal)).add(0, -vertical, 0).subtract(position);
            if (!phase2) {
                offset = counter <= 15 ? aim.scale(bigTimer <= 60 ? -280 : 70) : offset.scale(.9);
                velocity = desired(distance.subtract(offset), 17);
            } else if (!fist && dashTimer > 0) {
                dashTimer--;
                emit.accept(new Shot(position, direction(velocity).scale(.1), 4 + dashes++, hand ? 1 : 0, 120));
                if (dashes == 30 && !partnerAlive) {
                    cue("brother_swing", .55f, -.3f);
                    for (int sign : new int[]{-1, 1}) {
                        Vec3 ray = rotate(aim, axis, sign * .34);
                        emit.accept(new Shot(position.add(ray.scale(130)), ray.scale(90), 50, 0, 120));
                    }
                }
                velocity = velocity.scale(.975);
            } else if (counter <= 15) velocity = desired(distance, 20 + 60 * clamp((delay - 10) / 110));
            else velocity = velocity.scale(.85);
        } else velocity = velocity.scale(.9);
        facing = target.subtract(position).dot(planeX) > 0 ? 1 : -1;
        if (delay < 120) { delay += 1.5; return; }
        if (bigTimer > 50) {
            counter += (1.5 + .5 * (1 - ratio)) * (!partnerAlive ? (fist ? 1.32 : 1.25) : 1);
            if (counter < 50) return;
            counter = 0;
            Vec3 spawn = position.add(planeX.scale((fist ? 74 : 125) * previousFacing));
            Vec3 ray = !fist && !partnerAlive ? direction(aim.add(targetVelocity.scale(.0305))) : aim;
            if (fist) {
                cue("hellblast", 1, 0);
                if (!phase2 || combo++ < 2) emit.accept(new Shot(spawn, ray.scale(15), 2, hand ? 1 : 0, 100));
                else {
                    combo = 0;
                    for (int sign : new int[]{-1, 1})
                        emit.accept(new Shot(spawn, rotate(ray, axis, sign * (partnerAlive ? .55 : .48)).scale(15), 2, hand ? 1 : 0, 100));
                }
            } else {
                boolean accelerated = phase2 && combo++ >= 2;
                cue("brother_resonance", .5f, hand ? .2f : -.2f);
                if (accelerated) { combo = 0; cue("brother_swing", .55f, .4f); }
                emit.accept(new Shot(spawn, ray.scale(phase2 ? (accelerated ? .2 : 5.5) : 8), accelerated ? 3 : 2, hand ? 1 : 0, 100));
            }
            hand = !hand;
            // The source samples partner existence only after an ordinary shot.
            partnerAlive = actualPartnerAlive;
        } else if (bigTimer > 0) {
            if (bigTimer == 50) cue("brother_charge", .85f, .6f);
            bigTimer--; // Source deliberately decrements twice in this pause.
        } else {
            double rate = 2.5 + .5 * (1 - ratio);
            if (!partnerAlive) rate = fist ? (3 + (1 + (29 - limit) * .45) * (1 - ratio)) * 1.2 : rate * 1.35;
            counter += phase2 && limit == 0 ? 1 : rate;
            if (counter < 50) return;
            counter = 0;
            Vec3 predicted = !partnerAlive ? direction(aim.add(targetVelocity.scale(.032))) : aim;
            if (!partnerAlive ? limit <= 3 && limit > 0 : limit == 1) {
                if (fist) {
                    cue("brother_echo", .4f, -.8f - limit * .1f);
                    for (int sign : new int[]{-1, 1})
                        emit.accept(new Shot(position, rotate(aim, axis, sign * (.6 - limit * .16)).scale(11), 2, 0, 100));
                } else {
                    cue("brother_swing", .55f, -.3f - limit * .1f);
                    emit.accept(new Shot(position.add(predicted.scale(130)), (phase2 ? aim : predicted).scale(90), 50, 5, 120));
                }
            } else if (limit == 0 && phase2) {
                if (fist) {
                    cue("shot", 1.8f, .5f); cue("brother_finisher", .65f, -.75f);
                    emit.accept(new Shot(position, aim.scale(9.5), 3, hand ? 1 : 0, 100));
                } else {
                    cue("brother_dash", .85f, -.5f);
                    velocity = predicted.scale(90); dashTimer = 30; dashes = 0;
                }
            } else if (fist) {
                cue("shot", 1.2f, .4f);
                Vec3 jitter = rotate(aim.scale(1.5), axis, Math.PI / 2).scale(random.nextDouble(-25, 25));
                double speed = 7.5 - (phase2 ? (29 - limit) * .13 : (8 - limit) * .15);
                emit.accept(new Shot(position.add(jitter), aim.scale(speed), 1, hand ? 1 : 0, 100));
            } else {
                cue("brother_resonance", .5f, hand ? .2f : -.2f);
                emit.accept(new Shot(position, aim.scale(8), 1, hand ? 1 : 0, 100));
            }
            hand = !hand;
            if (limit > 0) limit--;
            else { bigTimer = 500; delay = 0; limit = 7; side = !side; }
        }
    }
    private static Vec3 desired(Vec3 distance, double speed) {
        double length = distance.length();
        return length < 1e-12 ? Vec3.ZERO : distance.scale(1 / length)
                .scale(Math.min(length, speed) * (1 - clamp(length / 2400)) + Math.min(length / 24, speed * 3) * clamp(length / 2400));
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Vec3 direction(Vec3 v) { return CalamitasSpace.unit(v, new Vec3(0, -1, 0)); }
    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        return v.scale(Math.cos(angle)).add(axis.cross(v).scale(Math.sin(angle))).add(axis.scale(axis.dot(v) * (1 - Math.cos(angle))));
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putInt("BigTimer", bigTimer); tag.putInt("Limit", limit); tag.putInt("Vertical", vertical);
        tag.putInt("Combo", combo); tag.putInt("DashTimer", dashTimer); tag.putInt("Dashes", dashes); tag.putInt("Facing", facing);
        tag.putDouble("Delay", delay); tag.putDouble("Counter", counter);
        tag.putBoolean("MovingUp", movingUp); tag.putBoolean("PartnerAlive", partnerAlive);
        tag.putBoolean("EnrageRoar", enrageRoar); tag.putBoolean("Phase2", phase2); tag.putBoolean("Side", side); tag.putBoolean("Hand", hand);
        tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
        tag.putDouble("OX", offset.x); tag.putDouble("OY", offset.y); tag.putDouble("OZ", offset.z);
        return tag;
    }
    public void load(CompoundTag tag) {
        int timer = tag.getInt("BigTimer"), n = tag.getInt("Limit"), y = tag.getInt("Vertical"), c = tag.getInt("Combo");
        int dash = tag.getInt("DashTimer"), trails = tag.getInt("Dashes"), face = tag.getInt("Facing");
        double wait = tag.getDouble("Delay"), count = tag.getDouble("Counter");
        Vec3 v = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
        Vec3 o = new Vec3(tag.getDouble("OX"), tag.getDouble("OY"), tag.getDouble("OZ"));
        if (!kind.name().equals(tag.getString("Kind")) || timer < 0 || timer > 500 || n < 0 || n > 29
                || Math.abs(y) > 410 || c < 0 || c > 2 || dash < 0 || dash > 30 || trails < 0 || trails > 30
                || Math.abs(face) != 1 || !Double.isFinite(wait) || wait < 0 || wait > 120
                || !Double.isFinite(count) || count < 0 || count >= 50 || !finite(v) || !finite(o))
            throw new IllegalArgumentException("Invalid saved brother combat state");
        cues.clear(); enrageRoar = !tag.contains("EnrageRoar") || tag.getBoolean("EnrageRoar");
        bigTimer = timer; limit = n; vertical = y; combo = c; dashTimer = dash; dashes = trails; facing = face;
        delay = wait; counter = count; velocity = v; offset = o;
        movingUp = tag.getBoolean("MovingUp"); partnerAlive = tag.getBoolean("PartnerAlive");
        phase2 = tag.getBoolean("Phase2"); side = tag.getBoolean("Side"); hand = tag.getBoolean("Hand");
    }
}
