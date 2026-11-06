package net.everla.projectc.arkofcosmos.calamity;

import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Ordinary-world first/last-stage attack loop, Calamity 1a8cebd.
 * Positions and velocities use source pixels, with Minecraft's upward Y.
 * Each call is one source frame; the owning mob commits movement/collision.
 */
public final class SupremeCalamitasCombat {
    private static final int[] CYCLE = {0,3,4,1,1,4,3,0,1,0,3,4,4,3,1,0,4,4,1,1,0,1,0,1};
    public enum Kind { DART, HELLBLAST, FIREBLAST, GIGABLAST }
    public enum Cue { SHOT, BIG_SHOT, HELLBLAST, DASH }
    public record Shot(Kind kind, Vec3 position, Vec3 velocity, Vec3 planeX, double maximumSpeed, int variant) {}
    private int mode, cycle, elapsed, charges, pause;
    private double shotTimer, sideTimer;
    private boolean splittingAllowed = true, fireFirst, willCharge, contact;
    private Vec3 velocity = Vec3.ZERO;
    private Cue cue;
    /** One cue per source attack frame, including an eight-projectile fan. */
    public Cue cue() { return cue; }
    public int mode() { return mode; }
    public int elapsed() { return elapsed; }
    public double shotTimer() { return shotTimer; }
    public boolean contact() { return contact; }
    public boolean willCharge() { return willCharge; }
    public Vec3 velocity() { return velocity; }
    public void pauseAttacks(int frames) { pause = Math.max(pause, frames); }

    /** Frozen encounters must not call this method. Plane X is locked by the caller
     * at attack activation; directed shots still sample the current 3D target.
     */
    public void step(Vec3 position, Vec3 target, double targetHalfWidth, Vec3 planeX,
            boolean secondStage, boolean worm, double healthRatio, double enrage,
            RandomGenerator random, Consumer<Shot> emit) {
        if (!finite(position) || !finite(target) || !finite(planeX) || planeX.lengthSqr() < .99
                || !Double.isFinite(healthRatio) || healthRatio < 0 || healthRatio > 1
                || !Double.isFinite(enrage) || enrage < 1 || enrage > 2
                || !Double.isFinite(targetHalfWidth) || targetHalfWidth < 0)
            throw new IllegalArgumentException("Invalid Calamitas combat input");
        cue = null;
        if (pause > 0) pause--;
        contact = false;
        if (mode == -1) {
            cycle = (cycle + 1) % CYCLE.length;
            mode = CYCLE[cycle]; elapsed = charges = 0; sideTimer = 0;
            if (cycle == 0 || cycle == 5 || cycle == 9 || cycle == 15) willCharge = false;
            if (cycle == 2 || cycle == 7 || cycle == 13 || cycle == 17) willCharge = true;
            return;
        }
        Vec3 direction = direction(target.subtract(position));
        switch (mode) {
            case 0 -> {
                velocity = smooth(velocity, target.add(0, 550, 0).subtract(position), 12, .12);
                if (++elapsed >= (secondStage ? 240 : 300)) { mode = -1; fireFirst = true; }
                shotTimer += worm ? .5 : 1;
                if (shotTimer > (secondStage ? 60 : 90)) {
                    shotTimer = 0;
                    int choice = random.nextInt(6);
                    if (choice == 0 && splittingAllowed && !fireFirst) {
                        splittingAllowed = false;
                        cue = Cue.BIG_SHOT;
                        shoot(Kind.GIGABLAST, position, direction, planeX, 10 * enrage, 0, 2, 8, secondStage, emit);
                    } else if (choice == 1 && splittingAllowed || fireFirst) {
                        cue = Cue.SHOT;
                        if (!secondStage || elapsed > 1) splittingAllowed = false;
                        shoot(Kind.FIREBLAST, position, direction, planeX, 10 * enrage, 0, 2, 8, secondStage, emit);
                    } else {
                        splittingAllowed = true;
                        cue = Cue.BIG_SHOT;
                        Vec3 axis = direction.cross(new Vec3(0, 1, 0));
                        if (axis.lengthSqr() < 1e-12) axis = planeX;
                        axis = CalamitasSpace.unit(axis, planeX);
                        for (int i = 0; i < 8; i++) {
                            Vec3 fan = rotate(direction, axis, Math.toRadians(-20 + 40d * i / 7));
                            // Source spawn offset uses the central aim, not the fan direction.
                            if (secondStage || pause == 0) emit.accept(new Shot(Kind.DART,
                                    position.add(direction.scale(8)), fan.scale(10 * enrage), planeX, 13 * enrage, 4));
                        }
                    }
                    fireFirst = false;
                }
            }
            case 1 -> {
                cue = Cue.DASH;
                velocity = direction.scale((worm ? 26 : 30) + (1 - healthRatio) * 8);
                contact = true;
                mode = 2;
            }
            case 2 -> {
                contact = ++elapsed < 25;
                if (!contact) velocity = snap(velocity.scale(.96));
                if (elapsed >= 70) {
                    elapsed = 0;
                    mode = ++charges < (secondStage ? 1 : 2) ? 1 : -1;
                    contact = false;
                }
            }
            case 3 -> {
                velocity = smooth(velocity, sideDestination(position, target, targetHalfWidth, planeX, 600).subtract(position), 32, 1.2);
                if (++elapsed >= (secondStage ? 300 : 480)) mode = -1;
                else {
                    sideTimer += worm ? .5 : 1;
                    if (sideTimer >= (secondStage ? 24 : 20)) {
                        sideTimer = 0;
                        cue = Cue.HELLBLAST;
                        shoot(Kind.HELLBLAST, position, direction, planeX, 10 * enrage, 0, 2, 4, false, emit);
                    }
                }
            }
            case 4 -> {
                velocity = smooth(velocity, sideDestination(position, target, targetHalfWidth, planeX, 750).subtract(position), 32, 1.2);
                if (++shotTimer > (secondStage ? (worm ? 200 : 100) : (worm ? 280 : 140))) {
                    shotTimer = 0;
                    cue = Cue.BIG_SHOT;
                    shoot(Kind.GIGABLAST, position, direction, planeX, 5 * enrage, 0, 2, 8, secondStage, emit);
                }
                if (++elapsed >= (secondStage ? 240 : 300)) mode = -1;
            }
            default -> throw new IllegalStateException("Invalid combat mode");
        }
    }

    private void shoot(Kind kind, Vec3 position, Vec3 direction, Vec3 plane, double speed,
            double maximum, int variant, double offset, boolean ignoresPause, Consumer<Shot> emit) {
        if (ignoresPause || pause == 0)
            emit.accept(new Shot(kind, position.add(direction.scale(offset)), direction.scale(speed), plane, maximum, variant));
    }
    private static Vec3 sideDestination(Vec3 position, Vec3 target, double halfWidth, Vec3 plane, double offset) {
        return target.add(plane.scale(position.subtract(target).dot(plane) < halfWidth ? -offset : offset));
    }
    private static Vec3 direction(Vec3 delta) { return CalamitasSpace.unit(delta, new Vec3(0, -1, 0)); }
    private static Vec3 snap(Vec3 v) { return new Vec3(Math.abs(v.x) < .1 ? 0 : v.x, Math.abs(v.y) < .1 ? 0 : v.y, Math.abs(v.z) < .1 ? 0 : v.z); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        return v.scale(Math.cos(angle)).add(axis.cross(v).scale(Math.sin(angle))).add(axis.scale(axis.dot(v) * (1 - Math.cos(angle))));
    }
    /** Calamity SmoothMovement followed by Terraria SimpleFlyMovement, per axis. */
    public static Vec3 smooth(Vec3 current, Vec3 distance, double baseSpeed, double acceleration) {
        double length = distance.length();
        Vec3 unit = length < 1e-12 ? Vec3.ZERO : distance.scale(1 / length);
        Vec3 desired = unit.scale(Math.min(length, baseSpeed)).lerp(unit.scale(Math.min(length / 24, baseSpeed * 3)), Math.min(1, length / 2400));
        return new Vec3(approach(current.x, desired.x, acceleration), approach(current.y, desired.y, acceleration), approach(current.z, desired.z, acceleration));
    }
    private static double approach(double current, double desired, double acceleration) {
        if (current < desired) { current += acceleration; if (current < 0 && desired > 0) current += acceleration; }
        else if (current > desired) { current -= acceleration; if (current > 0 && desired < 0) current -= acceleration; }
        return current;
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Mode", mode); tag.putInt("Cycle", cycle); tag.putInt("Elapsed", elapsed);
        tag.putInt("Charges", charges); tag.putInt("Pause", pause);
        tag.putDouble("ShotTimer", shotTimer); tag.putDouble("SideTimer", sideTimer);
        tag.putBoolean("SplittingAllowed", splittingAllowed); tag.putBoolean("FireFirst", fireFirst);
        tag.putBoolean("WillCharge", willCharge); tag.putBoolean("Contact", contact);
        tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
        return tag;
    }
    public void load(CompoundTag tag) {
        int m = tag.getInt("Mode"), c = tag.getInt("Cycle"), e = tag.getInt("Elapsed"), n = tag.getInt("Charges"), p = tag.getInt("Pause");
        double shot = tag.getDouble("ShotTimer"), side = tag.getDouble("SideTimer");
        Vec3 v = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
        if (m < -1 || m > 4 || c < 0 || c >= 24 || e < 0 || e > 480 || n < 0 || n > 2 || p < 0
                || !Double.isFinite(shot) || shot < 0 || shot > 281 || !Double.isFinite(side) || side < 0 || side > 24 || !finite(v))
            throw new IllegalArgumentException("Invalid saved Calamitas combat state");
        mode = m; cycle = c; elapsed = e; charges = n; pause = p; shotTimer = shot; sideTimer = side;
        velocity = v; splittingAllowed = tag.getBoolean("SplittingAllowed"); fireFirst = tag.getBoolean("FireFirst");
        willCharge = tag.getBoolean("WillCharge"); contact = tag.getBoolean("Contact");
    }
}
