package net.everla.projectc.arkofcosmos.calamity;

import java.util.random.RandomGenerator;
import java.util.function.Consumer;

/** Ordinary-world death-mode bullet hell, pinned to Calamity 1a8cebd.
 * Coordinates/velocities remain in source pixels/updates; +Y points down.
 * The server calls step once per source frame (three times per Minecraft tick).
 * Projectile extraUpdates belong to projectile simulation, not this clock.
 */
public final class SupremeCalamitasBarrage {
    public static final int SOURCE_STEPS_PER_TICK = 3;
    public static final int DURATION = 900;
    public static final int RUMBLE_FRAME = DURATION - 360;
    public enum Kind { HELLBLAST, FIREBLAST, GIGABLAST, WAVE, MAELSTROM }
    /** Offset from the target sampled at emission; maelstrom instead uses arena center. */
    public record Shot(Kind kind, double x, double y, double vx, double vy) {}
    private int completedFrames;
    private int shotGate;
    private int maelstromTelegraph;

    public int completedFrames() { return completedFrames; }
    public int shotGate() { return shotGate; }
    public int maelstromTelegraph() { return maelstromTelegraph; }

    public void restore(int frames, int gate, int telegraph) {
        if (frames < 0 || frames > DURATION * 5 || gate < 0 || gate >= 16
                || telegraph < 0 || telegraph > DURATION)
            throw new IllegalArgumentException("Invalid Calamitas barrage state");
        completedFrames = frames;
        shotGate = gate;
        maelstromTelegraph = telegraph;
    }

    /** Round is 1..5; caller controls phase transitions and pauses between rounds. */
    public void step(int round, double speedMultiplier, RandomGenerator random, Consumer<Shot> emit) {
        if (round < 1 || round > 5 || completedFrames < (round - 1) * DURATION
                || completedFrames >= round * DURATION)
            throw new IllegalStateException("Barrage round does not match source clock");
        if (!Double.isFinite(speedMultiplier) || speedMultiplier < 1 || speedMultiplier > 2)
            throw new IllegalArgumentException("Invalid enrage velocity multiplier");
        int frame = ++completedFrames;
        int localFrame = frame - (round - 1) * DURATION;

        if (round == 2) {
            // Preserve the source's strict inequalities at global frames 1200 and 1500.
            if (frame % 180 == 0 && (frame < 1200 || frame > 1500))
                vertical(Kind.FIREBLAST, false, 5, speedMultiplier, random, emit);
            else if (frame % 180 == 0 && frame > 1200 && frame < 1500)
                side(Kind.FIREBLAST, true, 5, speedMultiplier, random, emit);
        } else if (round >= 3) {
            if (frame % (round == 5 ? 240 : 180) == 0)
                vertical(Kind.FIREBLAST, false, 5, speedMultiplier, random, emit);
            if (frame % (round == 5 ? 360 : 240) == 0)
                vertical(Kind.GIGABLAST, false, 10, speedMultiplier, random, emit);
        }
        if (round == 4 && ++maelstromTelegraph == 420)
            emit.accept(new Shot(Kind.MAELSTROM, 0, 0, 0, 0));
        if (round == 5 && frame % 30 == 0) {
            int offset = random.nextInt(-1000, 1001);
            emit.accept(new Shot(Kind.WAVE, 1000, offset, -5 * speedMultiplier, 0));
            emit.accept(new Shot(Kind.WAVE, -1000, -offset, 5 * speedMultiplier, 0));
        }
        int gate = 8 + switch (round) { case 1 -> 0; case 2 -> 1; case 3 -> 4; case 4 -> 6; default -> 8; };
        if (++shotGate < gate) return;
        shotGate = 0;
        if (frame % (gate * 6) == 0) {
            boolean fromRight = random.nextBoolean();
            emit.accept(new Shot(Kind.HELLBLAST, fromRight ? 1000 : -1000, 0,
                    (fromRight ? -4 : 4) * speedMultiplier, 0));
        }
        // Source uses global frame comparisons (<300, <600, etc), not <=.
        if (localFrame < 300) {
            vertical(Kind.HELLBLAST, round == 2 || round == 4, 4, speedMultiplier, random, emit);
        } else if (localFrame < 600) {
            if (round == 2 || round == 4)
                side(Kind.HELLBLAST, false, 3.5, speedMultiplier, random, emit);
            else if (round == 3)
                side(Kind.HELLBLAST, true, 3.5, speedMultiplier, random, emit);
            else {
                side(Kind.HELLBLAST, true, 3.5, speedMultiplier, random, emit);
                side(Kind.HELLBLAST, false, 3.5, speedMultiplier, random, emit);
            }
        } else {
            if (round == 1 || round == 5)
                vertical(Kind.HELLBLAST, false, 3, speedMultiplier, random, emit);
            side(Kind.HELLBLAST, true, 3, speedMultiplier, random, emit);
            side(Kind.HELLBLAST, false, 3, speedMultiplier, random, emit);
        }
    }

    private static void vertical(Kind kind, boolean below, double speed, double multiplier,
                                 RandomGenerator random, Consumer<Shot> emit) {
        emit.accept(new Shot(kind, random.nextInt(-1000, 1001), below ? 1000 : -1000,
                0, (below ? -speed : speed) * multiplier));
    }

    private static void side(Kind kind, boolean right, double speed, double multiplier,
                             RandomGenerator random, Consumer<Shot> emit) {
        emit.accept(new Shot(kind, right ? 1000 : -1000, random.nextInt(-1000, 1001),
                (right ? -speed : speed) * multiplier, 0));
    }
}
