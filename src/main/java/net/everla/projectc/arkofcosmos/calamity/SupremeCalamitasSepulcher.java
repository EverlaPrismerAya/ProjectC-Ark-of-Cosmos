package net.everla.projectc.arkofcosmos.calamity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Source head steering and the explicitly requested 3D history-following adaptation. */
public final class SupremeCalamitasSepulcher {
    public static final int SEGMENTS = 52, ARMS = 0, LEGACY_ARMS = 24;
    private SupremeCalamitasSepulcher() {}
    public static boolean energy(int index) { return index < 51 && index % 2 == 1; }
    public static double spacing(int index) { return index == 51 ? 20 : energy(index) ? 34 : 52; }
    public static double trailLength() {
        double length = 0;
        for (int i = 0; i < SEGMENTS; i++) length += spacing(i);
        return length;
    }
    /** Source coordinates in pixels with upward Y. Quantization preserves the source's
     * truncation toward zero. The third axis receives the same component steering;
     * the vertical escape term uses the dominant horizontal reversing component.
     */
    public static Vec3 steer(Vec3 velocity, Vec3 position, Vec3 target, int cooldown) {
        // Run the source's signed escape rules in its downward-Y coordinates.
        velocity = new Vec3(velocity.x, -velocity.y, velocity.z);
        position = new Vec3(position.x, -position.y, position.z);
        target = new Vec3(target.x, -target.y, target.z);
        double speed = velocity.length();
        if (speed > 0) velocity = velocity.scale(Math.max(14, Math.min(26, speed)) / speed);
        Vec3 delta = quantize(target).subtract(quantize(position));
        double length = delta.length();
        if (length < 1e-12) return new Vec3(velocity.x, -velocity.y, velocity.z);
        Vec3 wanted = delta.scale(20 / length);
        double a = .545 - cooldown * .0015;
        double x = velocity.x, y = velocity.y, z = velocity.z;
        if (sameSign(x, wanted.x) || sameSign(y, wanted.y) || sameSign(z, wanted.z)) {
            x = approach(x, wanted.x, a); y = approach(y, wanted.y, a); z = approach(z, wanted.z, a);
            double horizontal = Math.abs(wanted.x) >= Math.abs(wanted.z) ? wanted.x : wanted.z;
            double horizontalVelocity = Math.abs(wanted.x) >= Math.abs(wanted.z) ? x : z;
            if (Math.abs(wanted.y) < 4 && oppositeSign(horizontalVelocity, horizontal)) y += (y > 0 ? 1 : -1) * a * 2;
            if (Math.abs(wanted.x) < 4 && oppositeSign(y, wanted.y)) x += (x > 0 ? 1 : -1) * a * 2;
            if (Math.abs(wanted.z) < 4 && oppositeSign(y, wanted.y) && Math.abs(delta.z) > 1e-12) z += (z > 0 ? 1 : -1) * a * 2;
        } else {
            // Source chooses Y on an exact tie. Adding Z does not change its 2D result.
            if (Math.abs(delta.x) > Math.abs(delta.y) && Math.abs(delta.x) >= Math.abs(delta.z)) {
                x = approach(x, wanted.x, a * 1.1);
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 10) y += (y > 0 ? 1 : -1) * a;
            } else if (Math.abs(delta.z) > Math.abs(delta.y)) {
                z = approach(z, wanted.z, a * 1.1);
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 10) y += (y > 0 ? 1 : -1) * a;
            } else {
                y = approach(y, wanted.y, a * 1.1);
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 10) {
                    if (Math.abs(delta.z) > Math.abs(delta.x)) z += (z > 0 ? 1 : -1) * a;
                    else x += (x > 0 ? 1 : -1) * a;
                }
            }
        }
        return new Vec3(x, -y, z);
    }
    private static double approach(double value, double goal, double amount) { return value + Math.signum(goal - value) * amount; }
    private static boolean sameSign(double a, double b) { return a > 0 && b > 0 || a < 0 && b < 0; }
    private static boolean oppositeSign(double a, double b) { return a > 0 && b < 0 || a < 0 && b > 0; }
    private static Vec3 quantize(Vec3 value) { return new Vec3(truncate(value.x), truncate(value.y), truncate(value.z)); }
    private static double truncate(double value) { return Math.copySign(Math.floor(Math.abs(value) / 16) * 16, value); }

    /** Arc length, not a fixed number of ticks, retains source spacing at all speeds.
     * All segment samples are obtained in one traversal of the history.
     */
    public static final class Trail {
        private record Point(Vec3 position, double distance) {}
        private final ArrayDeque<Point> points = new ArrayDeque<>();
        private double distance;
        private final double maximumLength;
        public Trail(Vec3 origin, Vec3 forward, double pixels) {
            maximumLength = (trailLength() + 104) / pixels;
            Vec3 direction = forward.lengthSqr() < 1e-12 ? new Vec3(0, 1, 0) : forward.normalize();
            points.add(new Point(origin.subtract(direction.scale(maximumLength)), -maximumLength));
            points.add(new Point(origin, 0));
        }
        public void append(Vec3 position) {
            Point last = points.peekLast();
            double step = position.distanceTo(last.position);
            if (!Double.isFinite(step)) throw new IllegalArgumentException("Invalid Sepulcher trail point");
            if (step < 1e-9) return;
            distance += step;
            points.addLast(new Point(position, distance));
            // Keep one point before the cutoff so interpolation remains continuous.
            while (points.size() > 2) {
                var iterator = points.iterator(); iterator.next();
                if (iterator.next().distance >= distance - maximumLength) break;
                points.removeFirst();
            }
        }
        public List<Vec3> segments(double pixels) {
            var result = new ArrayList<Vec3>(SEGMENTS);
            var iterator = points.descendingIterator();
            Point newer = iterator.next(), older = iterator.hasNext() ? iterator.next() : newer;
            double offset = 0;
            for (int i = 0; i < SEGMENTS; i++) {
                offset += spacing(i) / pixels;
                double at = distance - offset;
                while (older.distance > at && iterator.hasNext()) { newer = older; older = iterator.next(); }
                double length = newer.distance - older.distance;
                double fraction = length < 1e-12 ? 0 : Math.max(0, Math.min(1, (at - older.distance) / length));
                result.add(older.position.lerp(newer.position, fraction));
            }
            return result;
        }
        public CompoundTag save() {
            var tag = new CompoundTag(); var list = new ListTag();
            tag.putDouble("Distance", distance);
            for (Point point : points) {
                var entry = new CompoundTag(); entry.putDouble("X", point.position.x); entry.putDouble("Y", point.position.y); entry.putDouble("Z", point.position.z);
                entry.putDouble("D", point.distance); list.add(entry);
            }
            tag.put("Points", list); return tag;
        }
        public void load(CompoundTag tag) {
            double savedDistance = tag.getDouble("Distance");
            var restored = new ArrayDeque<Point>();
            double previous = Double.NEGATIVE_INFINITY;
            for (Tag raw : tag.getList("Points", Tag.TAG_COMPOUND)) {
                var point = (CompoundTag) raw;
                Vec3 position = new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"));
                double d = point.getDouble("D");
                if (!Double.isFinite(position.lengthSqr()) || !Double.isFinite(d) || d <= previous || restored.size() >= 32768)
                    throw new IllegalArgumentException("Invalid Sepulcher trail save");
                restored.addLast(new Point(position, d)); previous = d;
            }
            if (restored.size() < 2 || !Double.isFinite(savedDistance) || savedDistance != restored.peekLast().distance)
                throw new IllegalArgumentException("Invalid Sepulcher trail distance");
            distance = savedDistance; points.clear(); points.addAll(restored);
        }
    }
}
