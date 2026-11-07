import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBarrage;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBarrage.Shot;

public final class CalamitasBarrageCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var barrage = new SupremeCalamitasBarrage();
        var random = new Random(971);
        // Counts from halved hellblast gates; special attacks retain their source timing.
        int[][] expected = {{489,0,0,0,0}, {339,5,0,0,0}, {226,5,4,0,0},
                {171,5,4,0,1}, {227,3,2,60,0}};
        int[] carry = {0,0,0,4,0};
        for (int round = 1; round <= 5; round++) {
            int[] counts = new int[5];
            for (int tick = 0; tick < 300; tick++) {
                for (int substep = 0; substep < SupremeCalamitasBarrage.SOURCE_STEPS_PER_TICK; substep++) {
                    int previousFrame = barrage.completedFrames();
                    barrage.step(round, 1, random, shot -> {
                        counts[shot.kind().ordinal()]++;
                        if (shot.kind() == SupremeCalamitasBarrage.Kind.MAELSTROM)
                            require(previousFrame + 1 == 3120, "maelstrom must spawn on round-four frame 420");
                    });
                }
            }
            require(barrage.completedFrames() == round * 900, "round must last exactly 300 MC ticks");
            require(Arrays.equals(counts, expected[round - 1]), "round " + round + ": " + Arrays.toString(counts));
            require(barrage.shotGate() == carry[round - 1], "gate carry changed");
            System.out.println("round " + round + ": 300 ticks, counts=" + Arrays.toString(counts));
        }
        // Resume in the middle of every round without restarting telegraphs or losing emissions.
        for (int frame = 0; frame < 4500; frame++) {
            var original = new SupremeCalamitasBarrage();
            int round = frame / 900 + 1;
            original.restore(frame, frame % 8, round == 4 ? frame - 2700 : round > 4 ? 900 : 0);
            var restored = new SupremeCalamitasBarrage();
            restored.restore(original.completedFrames(), original.shotGate(), original.maelstromTelegraph());
            var a = new ArrayList<Shot>();
            var b = new ArrayList<Shot>();
            original.step(round, 1.75, new Random(frame), a::add);
            restored.step(round, 1.75, new Random(frame), b::add);
            require(a.equals(b) && original.completedFrames() == restored.completedFrames()
                    && original.shotGate() == restored.shotGate()
                    && original.maelstromTelegraph() == restored.maelstromTelegraph(), "resume frame " + frame);
        }
        try {
            barrage.step(5, 1, random, shot -> {});
            throw new AssertionError("finished barrage accepted another frame");
        } catch (IllegalStateException expectedFailure) { }
        try {
            barrage.restore(-1, 0, 0);
            throw new AssertionError("negative save clock accepted");
        } catch (IllegalArgumentException expectedFailure) { }
        System.out.println("PASS: five round schedules, 4500 resume points, invalid/finished clocks");
    }
}
