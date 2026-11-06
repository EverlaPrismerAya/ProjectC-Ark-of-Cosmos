import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena;
import net.minecraft.world.phys.Vec3;

public final class CalamitasArenaAppearanceCheck {
    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static void settle(SupremeCalamitasArena arena, double ratio, boolean acceptance) {
        for (int frame = 0; frame < 400; frame++) {
            var resumed = SupremeCalamitasArena.load(arena.save());
            arena.stepAppearance(ratio, acceptance); resumed.stepAppearance(ratio, acceptance);
            require(arena.save().equals(resumed.save()), "appearance continuation at source frame " + frame);
        }
    }
    public static void main(String[] args) {
        var arena = new SupremeCalamitasArena(Vec3.ZERO, 16);
        arena.stepAppearance(1, false);
        require(arena.borderColor() == 0xdc143c, "initial source Crimson");
        // A threshold crossed before the >1.1 reset gate snaps directly, as in the source.
        arena.stepAppearance(.5, false);
        require(arena.borderColor() == 0x4169e1, "early threshold source snap");
        var boundary = new SupremeCalamitasArena(Vec3.ZERO, 16);
        boundary.stepAppearance(Math.nextUp(.5f), false);
        require(boundary.borderColor() == 0xdc143c, "above 50 percent float threshold");
        boundary.stepAppearance((double).3f, false);
        require(boundary.borderColor() == 0xdb4b02, "inclusive 30 percent Single threshold");
        settle(arena, 1, false);
        arena.stepAppearance(.5, false);
        require(arena.borderColor() == 0xdc143c, "late threshold starts at source old color");
        settle(arena, .5, false);
        require(arena.borderColor() == 0x4169e1, "50 percent RoyalBlue");
        arena.stepAppearance(.3, false);
        require(arena.borderColor() == 0x4169e1, "orange transition starts at RoyalBlue");
        settle(arena, .3, false);
        require(arena.borderColor() == 0xdb4b02, "30 percent Epiphany");
        settle(arena, .001, false);
        require(arena.borderColor() == 0xdb4b02, "low health alone is not acceptance color");
        arena.stepAppearance(.001, true);
        require(arena.borderColor() == 0xdb4b02, "acceptance transition starts at orange");
        settle(arena, .001, true);
        require(arena.borderColor() == 0x808080, "acceptance Gray");
        var legacy = arena.save(); legacy.remove("BorderColor"); legacy.remove("ColorCompletion");
        var old = SupremeCalamitasArena.load(legacy); old.stepAppearance(1, false);
        require(old.borderColor() == 0xdc143c, "legacy NBT appearance initialization");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1}) {
            var bad = arena.save(); bad.putFloat("ColorCompletion", invalid);
            try { SupremeCalamitasArena.load(bad); throw new AssertionError("invalid completion accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: arena source Crimson/RoyalBlue/Epiphany/Gray, 1.1 reset gate, 0.003 source cadence, NBT continuation and legacy saves");
    }
}
