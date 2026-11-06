import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasAnimation;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Phase;

public final class CalamitasAnimationCheck {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var a = new SupremeCalamitasAnimation();
        int[] first = {1, 0, 1, 5, 3}, second = {0, 0, 1, 6, 0};
        for (int mode = 0; mode < 5; mode++) {
            a.step(Phase.COMBAT, mode, 0, false, false, 0, 1);
            require(a.save().getInt("Family") == first[mode], "first stage family " + mode);
            a.step(Phase.COMBAT, mode, 0, true, false, 0, 1);
            require(a.save().getInt("Family") == second[mode], "second stage family " + mode);
        }
        for (boolean worm : new boolean[]{false, true}) {
            int rate = worm ? 200 : 100;
            a.step(Phase.COMBAT, 4, 40, true, worm, rate - 1, rate);
            require(a.save().getInt("Family") == 0, "warmup boundary");
            a.step(Phase.COMBAT, 4, 41, true, worm, rate - 1, rate);
            require(a.save().getInt("Frame") == 3 && a.save().getBoolean("Driven"), "charge terminal frame");
            double retained = a.save().getDouble("Counter");
            a.step(Phase.COMBAT, 4, 42, true, worm, rate, 0);
            require(a.save().getInt("Frame") == 3, "shot reset uses post-shot clock");
            a.step(Phase.COMBAT, 4, 43, true, worm, 14, 15);
            require(a.save().getInt("Frame") == 5, "punch recovery");
            require(a.save().getDouble("Counter") == retained, "driven strip must retain loop counter");
            var restored = new SupremeCalamitasAnimation(); restored.load(a.save());
            for (int frame = 0; frame < 600; frame++) {
                a.idle(); restored.idle(); require(a.save().equals(restored.save()), "reload continuity");
            }
        }
        for (Phase phase : new Phase[]{Phase.BARRAGE, Phase.HEART_CAST, Phase.BROTHERS_CAST, Phase.TRANSITION}) {
            a.step(phase, 0, 0, false, false, 0, 0);
            require(a.save().getInt("Family") == 2, "casting " + phase);
        }
        a.step(Phase.BROTHERS_WARNING, 0, 0, false, false, 0, 0);
        require(a.save().getInt("Family") == 0, "warning remains default");
        a.load(new net.minecraft.nbt.CompoundTag());
        require(a.save().getInt("Frame") == 0, "legacy save defaults");
        System.out.println("Calamitas animation families, source boundaries, retained counter and NBT passed");
    }
}
