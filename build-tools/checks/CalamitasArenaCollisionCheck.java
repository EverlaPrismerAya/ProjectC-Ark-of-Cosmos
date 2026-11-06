import net.everla.projectc.arkofcosmos.calamity.CalamitasArenaCollision;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CalamitasArenaCollisionCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        AABB old = new AABB(-10, -10, -10, 10, 10, 10);
        AABB body = new AABB(9.4, 0, 9.4, 10, 1.8, 10);
        Vec3 clipped = CalamitasArenaCollision.clip(old, body, new Vec3(20, .4, 20));
        near(clipped.x, 0, "right corner blocks outward travel");
        near(clipped.y, .4, "vertical wall sliding retained");
        near(clipped.z, 0, "depth corner blocks outward travel");
        require(CalamitasArenaCollision.clip(old, body, new Vec3(-2, -.2, -2)).equals(new Vec3(-2, -.2, -2)),
                "inward movement unaffected");
        AABB middle = new AABB(-.3, 0, -.3, .3, 1.8, .3);
        clipped = CalamitasArenaCollision.clip(old, middle, new Vec3(1000, -1000, -1000));
        require(CalamitasArenaCollision.fits(old, middle.move(clipped)), "high speed cannot tunnel through six faces");
        near(clipped.x, 9.7, "full width used");
        near(clipped.y, -10, "floor uses feet");
        near(CalamitasArenaCollision.clip(old, middle, new Vec3(0, 1000, 0)).y, 8.2, "ceiling uses head");
        AABB outside = middle.move(11, 0, 0);
        near(CalamitasArenaCollision.clip(old, outside, new Vec3(-2, 0, 0)).x, -2, "outside can re-enter");
        near(CalamitasArenaCollision.clip(old, outside, new Vec3(2, 0, 0)).x, 0, "outside cannot worsen escape");
        require(CalamitasArenaCollision.clip(old, outside, Vec3.ZERO).equals(Vec3.ZERO), "no implicit outside teleport");

        AABB shrink = new AABB(-9, -9, -9, 9, 9, 9);
        Vec3 push = CalamitasArenaCollision.correction(shrink, body);
        require(push.equals(new Vec3(-1, 0, -1)), "corner shrink pushes both axes minimally");
        require(CalamitasArenaCollision.fits(shrink, body.move(push)), "push fits complete body");
        AABB floorBody = new AABB(0, -10, 0, .6, -8.2, .6);
        near(CalamitasArenaCollision.correction(shrink, floorBody).y, 1, "rising floor carries player");
        AABB triple = new AABB(9.4, 8.2, 9.4, 10, 10, 10);
        push = CalamitasArenaCollision.correction(shrink, triple);
        require(push.equals(new Vec3(-1, -1, -1)), "three simultaneous faces solved in one translation");
        require(CalamitasArenaCollision.fits(shrink, triple.move(push)), "three-face corner fully contained");
        require(CalamitasArenaCollision.correction(shrink, triple.move(push)).equals(Vec3.ZERO), "no corner ping-pong after correction");
        AABB huge = new AABB(-11, -1, -1, 11, 1, 1);
        require(!CalamitasArenaCollision.fits(shrink, huge), "oversized body cannot be silently accepted");
        near(CalamitasArenaCollision.correction(shrink, huge).x, 0, "oversized body does not ping-pong");
        System.out.println("Calamitas arena collision geometry passed: six faces, corner sliding, high speed and simultaneous two/three-face forced shrink");
    }
}
