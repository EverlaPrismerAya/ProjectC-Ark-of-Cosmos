package net.everla.projectc.arkofcosmos.calamity;

import java.util.random.RandomGenerator;
import net.minecraft.world.phys.Vec3;

/** One source projectile remains one projectile; spatial randomness never multiplies counts or speed. */
public final class CalamitasSpace {
    private CalamitasSpace() {}
    /** Keep one plane containing the locked fist aim, closest to the source attack plane.
     * A depth-only aim uses a deterministic perpendicular fallback. */
    public static Vec3 fistAxis(Vec3 shootVelocity, Vec3 planeX) {
        Vec3 aim = unit(shootVelocity, planeX);
        Vec3 normal = planeX.cross(new Vec3(0, -1, 0));
        Vec3 fallback = unit(aim.cross(Math.abs(aim.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)), planeX);
        return unit(normal.subtract(aim.scale(normal.dot(aim))), fallback);
    }
    public static Vec3 fistRay(Vec3 shootVelocity, Vec3 axis, int branch) {
        Vec3 base = shootVelocity.scale(-1);
        double angle = branch == 0 ? 0 : branch == 1 ? Math.PI * 2 / 3 : -Math.PI * 2 / 3;
        return base.scale(Math.cos(angle)).add(axis.cross(base).scale(Math.sin(angle)))
                .add(axis.scale(axis.dot(base) * (1 - Math.cos(angle))));
    }
    /** Vec3.normalize collapses lengths below 1e-4 to ZERO. Our source direction
     * guards allow smaller vectors, so normalize explicitly above their 1e-6 cutoff. */
    public static Vec3 unit(Vec3 vector, Vec3 fallback) {
        double squared = vector.lengthSqr();
        return squared < 1e-12 ? fallback : vector.scale(1 / Math.sqrt(squared));
    }
    /** Source Gigablast steering preserves speed, including valid velocities below
     * Minecraft's 1e-4 normalization cutoff. Coincident targets retain the heading. */
    public static Vec3 homeGigablast(Vec3 velocity, Vec3 targetOffset) {
        double speed = velocity.length(), distance = targetOffset.length();
        if (speed == 0) return Vec3.ZERO;
        Vec3 desired = distance == 0 ? velocity : targetOffset.scale(speed / distance);
        Vec3 blended = velocity.scale(24).add(desired);
        double length = blended.length();
        return length == 0 ? velocity : blended.scale(speed / length);
    }
    public static Vec3 direction(RandomGenerator random) {
        return direction(random.nextDouble(), random.nextDouble());
    }
    public static Vec3 direction(net.minecraft.util.RandomSource random) {
        return direction(random.nextDouble(), random.nextDouble());
    }
    private static Vec3 direction(double height, double rotation) {
        double y = height * 2 - 1, angle = rotation * Math.PI * 2;
        double radius = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(radius * Math.cos(angle), y, radius * Math.sin(angle));
    }
    public static Vec3 spreadAxis(Vec3 aim, RandomGenerator random) {
        Vec3 forward = unit(aim, new Vec3(0, -1, 0));
        Vec3 first = forward.cross(Math.abs(forward.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        double angle = random.nextDouble(Math.PI * 2);
        return first.scale(Math.cos(angle)).add(forward.cross(first).scale(Math.sin(angle)));
    }
}
