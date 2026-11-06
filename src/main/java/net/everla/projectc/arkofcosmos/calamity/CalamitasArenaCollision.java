package net.everla.projectc.arkofcosmos.calamity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Geometry shared by server authority and client prediction. No world writes. */
public final class CalamitasArenaCollision {
    private CalamitasArenaCollision() {}

    /** Clip only outward travel, preserving tangential motion and re-entry.
     * An already outside player is never teleported by ordinary movement clipping. */
    public static Vec3 clip(AABB arena, AABB body, Vec3 movement) {
        return new Vec3(clipAxis(arena.minX, arena.maxX, body.minX, body.maxX, movement.x),
                clipAxis(arena.minY, arena.maxY, body.minY, body.maxY, movement.y),
                clipAxis(arena.minZ, arena.maxZ, body.minZ, body.maxZ, movement.z));
    }

    private static double clipAxis(double low, double high, double bodyLow, double bodyHigh, double delta) {
        if (delta > 0) return Math.min(delta, Math.max(0, high - bodyHigh));
        if (delta < 0) return Math.max(delta, Math.min(0, low - bodyLow));
        return 0;
    }

    /** Minimum translation needed to fit the full body, including corners.
     * An oversized axis has no fitting translation; avoid opposing corrections on that axis. */
    public static Vec3 correction(AABB arena, AABB body) {
        return new Vec3(correctAxis(arena.minX, arena.maxX, body.minX, body.maxX),
                correctAxis(arena.minY, arena.maxY, body.minY, body.maxY),
                correctAxis(arena.minZ, arena.maxZ, body.minZ, body.maxZ));
    }

    private static double correctAxis(double low, double high, double bodyLow, double bodyHigh) {
        if (bodyHigh - bodyLow > high - low) return 0;
        if (bodyLow < low) return low - bodyLow;
        if (bodyHigh > high) return high - bodyHigh;
        return 0;
    }

    public static boolean fits(AABB arena, AABB body) {
        return body.minX >= arena.minX && body.maxX <= arena.maxX
                && body.minY >= arena.minY && body.maxY <= arena.maxY
                && body.minZ >= arena.minZ && body.maxZ <= arena.maxZ;
    }

}
