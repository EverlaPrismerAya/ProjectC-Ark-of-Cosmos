import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CalamitasArenaShrinkCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-8, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        var arena = new SupremeCalamitasArena(new Vec3(0, 100, 0), 16);
        AABB body = new AABB(-.3, 100, -.3, .3, 101.8, .3);
        arena.step(false, false, false, false, false, () -> body, (old, next) -> {});
        near(arena.bounds().maxX, 118.75, "unobstructed horizontal wall retains source shrink");
        near(arena.bounds().maxZ, 118.75, "depth wall retains source shrink without terrain holds");
        near(arena.bounds().minZ, -118.75, "opposite depth wall still shrinks");
        var restored = SupremeCalamitasArena.load(arena.save());
        require(restored.bounds().equals(arena.bounds()), "source arena faces survive NBT");
        var migrated = SupremeCalamitasArena.load(arena.save());
        near(migrated.bounds().maxZ, arena.bounds().maxX, "old depth pair migrates from horizontal extents");
        near(migrated.bounds().minZ, arena.bounds().minX, "old front migrates from left extent");
        final boolean[] resolved = {false};
        arena.step(false, false, false, false, false, () -> {
            require(resolved[0], "target AABB sampled before shrink pushes completed"); return body;
        }, (old, next) -> { resolved[0] = true; });
        System.out.println("Calamitas source shrink commit, unchanged terrain-independent extents, NBT and post-push target sampling passed");
    }
}
