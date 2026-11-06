package net.everla.projectc.arkofcosmos.calamity;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class CalamitasCollision {
    private CalamitasCollision() {}
    /** Exact segment-to-box squared distance. Inflating an AABB alone treats its
     * corners as solid cubes and produces false hits for spherical source attacks. */
    public static boolean sweptSphereHits(Vec3 from, Vec3 to, double radius, AABB box) {
        double[] start = {from.x, from.y, from.z}, delta = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] min = {box.minX, box.minY, box.minZ}, max = {box.maxX, box.maxY, box.maxZ};
        double[] cuts = new double[8]; cuts[0] = 0; cuts[1] = 1; int count = 2;
        for (int axis = 0; axis < 3; axis++) if (Math.abs(delta[axis]) > 1e-15) {
            double first = (min[axis] - start[axis]) / delta[axis], last = (max[axis] - start[axis]) / delta[axis];
            if (first > 0 && first < 1) cuts[count++] = first;
            if (last > 0 && last < 1) cuts[count++] = last;
        }
        java.util.Arrays.sort(cuts, 0, count);
        for (int part = 1; part < count; part++) {
            double lo = cuts[part - 1], hi = cuts[part], mid = (lo + hi) * .5, a = 0, b = 0;
            for (int axis = 0; axis < 3; axis++) {
                double value = start[axis] + delta[axis] * mid;
                if (value >= min[axis] && value <= max[axis]) continue;
                double offset = start[axis] - (value < min[axis] ? min[axis] : max[axis]);
                a += delta[axis] * delta[axis]; b += delta[axis] * offset;
            }
            double t = a == 0 ? lo : Math.max(lo, Math.min(hi, -b / a)), distance = 0;
            for (int axis = 0; axis < 3; axis++) {
                double value = start[axis] + delta[axis] * t;
                double gap = value < min[axis] ? min[axis] - value : value > max[axis] ? value - max[axis] : 0;
                distance += gap * gap;
            }
            if (distance <= radius * radius) return true;
        }
        return false;
    }

}
