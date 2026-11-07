package net.everla.projectc.arkofcosmos.calamity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed world-space arena; source distances are pixels until converted at the boundary.
 * Source Vector4 order is up/right/down/left (ArenaWallSystem.Box).
 */
public final class SupremeCalamitasArena {
    private final Vec3 origin;
    private final double pixelsPerBlock;
    private double up = 2000, right = 2000, down = 2000, left = 2000;
    private double enrageSpeed = 1;
    private Vec3 attackX = new Vec3(1, 0, 0);
    private int shotIndex;
    private double depthOffset;
    private double barrageLeft = 2000, barrageRight = 2000;
    private int borderColor = 0xff0000;
    private float colorCompletion = 1;

    public SupremeCalamitasArena(Vec3 origin, double pixelsPerBlock) {
        if (!finite(origin) || !Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0)
            throw new IllegalArgumentException("Invalid Calamitas arena");
        this.origin = origin;
        this.pixelsPerBlock = pixelsPerBlock;
    }

    public Vec3 origin() { return origin; }
    public double pixelsPerBlock() { return pixelsPerBlock; }
    public double enrageSpeed() { return enrageSpeed; }
    public Vec3 attackX() { return attackX; }
    public int borderColor() { return borderColor; }

    /** Pinned GetArenaColor/Color.Lerp order, including its 1.1 reset gate and byte truncation.
     * Health thresholds are independent of the mandatory encounter-phase gates. */
    public void stepAppearance(double healthRatio, boolean deathAnimationStarted) {
        if (!Double.isFinite(healthRatio) || healthRatio < 0)
            throw new IllegalArgumentException("Invalid arena appearance health");
        int color = 0xdc143c, oldColor = color; // XNA Crimson
        float ratio = (float)healthRatio; // Source lifeRatio is a Single.
        if (deathAnimationStarted) { color = 0x808080; oldColor = 0xdb4b02; }
        else if (ratio <= .3f) { color = 0xdb4b02; oldColor = 0x4169e1; }
        else if (ratio <= .5f) color = 0x4169e1;
        if (colorCompletion > 1.1f && color != borderColor) colorCompletion = 0;
        if (colorCompletion < 1) {
            int rgb = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int from = oldColor >> shift & 255, to = color >> shift & 255;
                rgb |= (int)(from + (to - from) * colorCompletion) << shift;
            }
            borderColor = rgb;
        } else borderColor = color;
        colorCompletion += .003f;
    }

    public AABB bounds() {
        return new AABB(origin.x - left / pixelsPerBlock, origin.y - down / pixelsPerBlock,
                origin.z - left / pixelsPerBlock, origin.x + right / pixelsPerBlock,
                origin.y + up / pixelsPerBlock, origin.z + right / pixelsPerBlock);
    }

    /** Only a fixed-direction shot whose whole sweep is outside and separating can be retired.
     * Curving shots and projectile emitters keep their native lifetime, even outside the wall. */
    public static int projectileBoundary(AABB bounds, Vec3 from, Vec3 to, double radius,
            double pixels, boolean terminalStraight, boolean splitChild, long elapsedTicks) {
        if (!terminalStraight) return 0;
        boolean escaping = from.x - radius > bounds.maxX && to.x > from.x
                || from.x + radius < bounds.minX && to.x < from.x
                || from.y - radius > bounds.maxY && to.y > from.y
                || from.y + radius < bounds.minY && to.y < from.y
                || from.z - radius > bounds.maxZ && to.z > from.z
                || from.z + radius < bounds.minZ && to.z < from.z;
        if (!escaping) return 0;
        // The visible wall strip extends 1999 source pixels beyond the collision planes.
        if (!bounds.inflate(1999 / pixels).contains(from)) return 2;
        return !splitChild || elapsedTicks >= 20 ? 1 : 0;
    }

    /** One source-frame interpolation, preserving upstream growth/shrink rates. */
    public void step(boolean wormAlive, boolean seekersAlive, boolean brothersAlive,
                     boolean fourthBarrageStarted, boolean accepting, AABB targetBounds) {
        step(wormAlive, seekersAlive, brothersAlive, fourthBarrageStarted, accepting,
                () -> targetBounds, (previous, proposed) -> {});
    }

    /** Resolve moving walls before committing them; sample the target after any carried movement. */
    public void step(boolean wormAlive, boolean seekersAlive, boolean brothersAlive,
                     boolean fourthBarrageStarted, boolean accepting,
                     java.util.function.Supplier<AABB> targetBounds,
                     java.util.function.BiConsumer<AABB, AABB> carry) {
        double targetUp = 1000, targetRight = 1000, targetDown = 1000, targetLeft = 1000;
        if (wormAlive) {
            targetRight *= 1.15;
            targetDown *= fourthBarrageStarted ? .75 : .25;
            targetLeft *= 1.15;
        }
        if (seekersAlive) {
            targetUp *= 1.5;
            targetRight *= .85;
            targetDown *= 1.5;
            targetLeft *= .85;
        }
        if (brothersAlive) {
            targetUp *= .85;
            targetRight *= 1.33;
            targetDown *= .85;
            targetLeft *= 1.33;
        }
        if (accepting) { targetUp = 400; targetRight = 500; targetDown = 73; targetLeft = 500; }
        double blend = accepting ? .02 : fourthBarrageStarted ? .05 : .1;
        AABB previous = bounds();
        double nextUp = up + (targetUp - up) * blend, nextRight = right + (targetRight - right) * blend;
        double nextDown = down + (targetDown - down) * blend, nextLeft = left + (targetLeft - left) * blend;
        AABB proposed = new AABB(origin.x - nextLeft / pixelsPerBlock, origin.y - nextDown / pixelsPerBlock,
                origin.z - nextLeft / pixelsPerBlock, origin.x + nextRight / pixelsPerBlock,
                origin.y + nextUp / pixelsPerBlock, origin.z + nextRight / pixelsPerBlock);
        carry.accept(previous, proposed);
        up = nextUp; right = nextRight; down = nextDown; left = nextLeft;
        // The source tests AABB overlap, not whether the player's center is contained.
        enrageSpeed = bounds().intersects(targetBounds.get())
                ? Math.max(1, enrageSpeed * .99) : Math.min(2, enrageSpeed * 1.01);
    }

    /** Capture only at a barrage boundary. Depth distribution is fixed by this phase seed;
     * one source emission maps to exactly one 3D projectile, with no extra random draws
     * in the upstream attack RNG and no multiplication of the original projectile count.
     */
    public void beginBarrage(Vec3 horizontalDirection, long phaseSeed) {
        if (!finite(horizontalDirection)) throw new IllegalArgumentException("Invalid attack direction");
        Vec3 horizontal = new Vec3(horizontalDirection.x, 0, horizontalDirection.z);
        attackX = CalamitasSpace.unit(horizontal, new Vec3(1, 0, 0));
        shotIndex = 0;
        depthOffset = (phaseSeed >>> 11) * 0x1.0p-53;
        barrageLeft = left;
        barrageRight = right;
    }

    public record Emission(Vec3 position, Vec3 velocity) {}

    /** target is sampled once at emission; no homing is implied by this mapping. */
    public Emission map(SupremeCalamitasBarrage.Shot shot, Vec3 target) {
        if (!finite(target)) throw new IllegalArgumentException("Invalid shot target");
        if (shot.kind() == SupremeCalamitasBarrage.Kind.MAELSTROM)
            return new Emission(origin, Vec3.ZERO);
        Vec3 depth = new Vec3(-attackX.z, 0, attackX.x);
        double fraction = depthOffset + shotIndex++ * .6180339887498949;
        fraction -= Math.floor(fraction);
        // Keep depth coordinates anchored to the arena, rather than following the target.
        double sourceDepth = -barrageLeft + (barrageLeft + barrageRight) * fraction;
        Vec3 targetOffset = target.subtract(origin);
        Vec3 position = origin.add(attackX.scale(targetOffset.dot(attackX) + shot.x() / pixelsPerBlock))
                .add(0, targetOffset.y - shot.y() / pixelsPerBlock, 0)
                .add(depth.scale(sourceDepth / pixelsPerBlock));
        Vec3 velocity = attackX.scale(shot.vx() / pixelsPerBlock).add(0, -shot.vy() / pixelsPerBlock, 0);
        return new Emission(position, velocity);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putDouble("X", origin.x); tag.putDouble("Y", origin.y); tag.putDouble("Z", origin.z);
        tag.putDouble("PixelsPerBlock", pixelsPerBlock);
        tag.putDouble("Up", up); tag.putDouble("Right", right);
        tag.putDouble("Down", down); tag.putDouble("Left", left);
        tag.putDouble("EnrageSpeed", enrageSpeed);
        tag.putDouble("AttackX", attackX.x); tag.putDouble("AttackZ", attackX.z);
        tag.putInt("ShotIndex", shotIndex); tag.putDouble("DepthOffset", depthOffset);
        tag.putDouble("BarrageLeft", barrageLeft); tag.putDouble("BarrageRight", barrageRight);
        tag.putInt("BorderColor", borderColor); tag.putFloat("ColorCompletion", colorCompletion);
        return tag;
    }

    public static SupremeCalamitasArena load(CompoundTag tag) {
        var arena = new SupremeCalamitasArena(new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                tag.getDouble("PixelsPerBlock"));
        arena.up = extent(tag, "Up"); arena.right = extent(tag, "Right");
        arena.down = extent(tag, "Down"); arena.left = extent(tag, "Left");
        arena.enrageSpeed = tag.getDouble("EnrageSpeed");
        arena.attackX = new Vec3(tag.getDouble("AttackX"), 0, tag.getDouble("AttackZ"));
        arena.shotIndex = tag.getInt("ShotIndex"); arena.depthOffset = tag.getDouble("DepthOffset");
        arena.barrageLeft = extent(tag, "BarrageLeft"); arena.barrageRight = extent(tag, "BarrageRight");
        if (tag.contains("BorderColor")) {
            arena.borderColor = tag.getInt("BorderColor"); arena.colorCompletion = tag.getFloat("ColorCompletion");
            if (arena.borderColor < 0 || arena.borderColor > 0xffffff || !Float.isFinite(arena.colorCompletion) || arena.colorCompletion < 0)
                throw new IllegalArgumentException("Invalid saved Calamitas arena appearance");
        }
        if (!Double.isFinite(arena.enrageSpeed) || arena.enrageSpeed < 1 || arena.enrageSpeed > 2
                || !finite(arena.attackX) || Math.abs(arena.attackX.lengthSqr() - 1) > 1e-6
                || arena.shotIndex < 0 || !Double.isFinite(arena.depthOffset)
                || arena.depthOffset < 0 || arena.depthOffset >= 1)
            throw new IllegalArgumentException("Invalid saved Calamitas arena");
        return arena;
    }

    private static double extent(CompoundTag tag, String key) {
        double value = tag.getDouble(key);
        if (!Double.isFinite(value) || value <= 0 || value > 10000)
            throw new IllegalArgumentException("Invalid arena extent " + key);
        return value;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
