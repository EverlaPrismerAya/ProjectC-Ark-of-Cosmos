package net.everla.projectc.arkofcosmos.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ArkGeometry {
    private ArkGeometry() {}

    /** Full separating-axis test: a rotated attack box against the target's world AABB. */
    public static boolean box(AABB target, Vec3 center, Vec3 x, Vec3 y, Vec3 z, double hx, double hy, double hz) {
        Vec3[] axes = {x, y, z, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        Vec3 delta = target.getCenter().subtract(center);
        double tx = target.getXsize() / 2, ty = target.getYsize() / 2, tz = target.getZsize() / 2;
        for (Vec3 axis : axes) if (separated(axis, delta, x, y, z, hx, hy, hz, tx, ty, tz)) return false;
        for (int a = 0; a < 3; a++) for (int b = 3; b < 6; b++)
            if (separated(axes[a].cross(axes[b]), delta, x, y, z, hx, hy, hz, tx, ty, tz)) return false;
        return true;
    }

    private static boolean separated(Vec3 axis, Vec3 delta, Vec3 x, Vec3 y, Vec3 z,
                                     double hx, double hy, double hz, double tx, double ty, double tz) {
        if (axis.lengthSqr() < 1e-16) return false;
        double radius = Math.abs(axis.dot(x)) * hx + Math.abs(axis.dot(y)) * hy + Math.abs(axis.dot(z)) * hz
                + Math.abs(axis.x) * tx + Math.abs(axis.y) * ty + Math.abs(axis.z) * tz;
        return Math.abs(delta.dot(axis)) > radius + 1e-8;
    }

    public static boolean line(AABB target, Vec3 start, Vec3 end, double width, AttackFrame frame) {
        Vec3 along = end.subtract(start);
        double length = along.length();
        if (length < 1e-8) return target.inflate(width / 2).contains(start);
        along = along.scale(1 / length);
        Vec3 normal = frame.normal();
        Vec3 across = normal.cross(along).normalize();
        if (across.lengthSqr() < .5) across = frame.right();
        normal = along.cross(across).normalize();
        return box(target, start.add(end).scale(.5), along, across, normal, length / 2, width / 2, width / 2);
    }

    public static Vec3 towards(Vec3 from, Vec3 to, double distance) {
        Vec3 offset = to.subtract(from);
        return offset.length() <= distance ? to : from.add(offset.normalize().scale(distance));
    }

    public static Vec3 turn(Vec3 velocity, Vec3 targetDirection, double maxAngle, Vec3 fallbackAxis) {
        double speed = velocity.length();
        if (speed < 1e-8 || targetDirection.lengthSqr() < 1e-16) return velocity;
        Vec3 a = velocity.scale(1 / speed), b = targetDirection.normalize();
        double angle = Math.acos(Math.max(-1, Math.min(1, a.dot(b))));
        if (angle <= maxAngle) return b.scale(speed * .995);
        Vec3 axis = a.cross(b).normalize();
        if (axis.lengthSqr() < .5) axis = a.cross(fallbackAxis).normalize();
        if (axis.lengthSqr() < .5) axis = a.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 result = a.scale(Math.cos(maxAngle)).add(axis.cross(a).scale(Math.sin(maxAngle)))
                .add(axis.scale(axis.dot(a) * (1 - Math.cos(maxAngle))));
        return result.scale(speed * .995);
    }
}
