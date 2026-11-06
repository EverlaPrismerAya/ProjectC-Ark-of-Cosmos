package net.everla.projectc.arkofcosmos.calamity.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client decoration only: source particle counts and size lifetime, distributed over six walls.
 * A source left/right pair chooses X or Z; upper/lower pairs cover the full horizontal plane. */
public final class CalamitasArenaVisual {
    public record Strip(AABB bounds, double pixels, float thickness) {}
    public static final class Particle {
        public final Vec3 center;
        public float size = 16;
        public int age;
        private Particle(Vec3 center) { this.center = center; }
    }
    private final Random random;
    private final List<Particle> particles = new ArrayList<>();
    private AABB bounds;
    private double pixels;
    private int color;
    private float thickness = 2000;
    private boolean retiring;
    private int emitted;
    private Strip currentStrip, previousStrip;

    public CalamitasArenaVisual(long seed) { random = new Random(seed); }
    public AABB bounds() { return bounds; }
    public double pixels() { return pixels; }
    public int color() { return color; }
    public float thickness() { return thickness; }
    public List<Particle> particles() { return particles; }
    public int emitted() { return emitted; }
    public Strip currentStrip() { return currentStrip; }
    public Strip previousStrip() { return previousStrip; }
    public boolean empty() { return thickness < 4 && particles.isEmpty(); }
    public boolean retiring() { return retiring; }
    public void snapshot(AABB bounds, double pixels, int color) {
        if (retiring) return;
        this.bounds = bounds; this.pixels = pixels; this.color = color;
    }
    public void retire() { retiring = true; }
    /** Exactly one source update, regardless of render FPS. */
    public void step() {
        // Source MagicPixel particles survive age 1, then scale to zero at age 2.
        previousStrip = currentStrip;
        currentStrip = null;
        for (var particle : particles) if (++particle.age > 1) particle.size *= .95f;
        particles.removeIf(particle -> particle.age > 1 && particle.size <= 2);
        if (bounds == null || thickness < 4) return;
        if (retiring && (thickness *= .9f) < 4) return;
        currentStrip = new Strip(bounds, pixels, thickness);
        int vertical = (int)Math.ceil((float)(bounds.getYsize() * pixels) / 100f);
        int horizontal = (int)Math.ceil((float)(bounds.getXsize() * pixels) / 100f);
        double offset = 8 / pixels;
        for (int i = 0; i < vertical; i++) {
            boolean x = random.nextBoolean();
            for (int side = 0; side < 2; side++) {
                double a = between(bounds.minX, bounds.maxX), y = between(bounds.minY, bounds.maxY);
                double face = side == 0 ? (x ? bounds.maxX : bounds.maxZ) + offset : (x ? bounds.minX : bounds.minZ) - offset;
                spawn(x ? new Vec3(face, y, between(bounds.minZ, bounds.maxZ)) : new Vec3(a, y, face));
            }
        }
        for (int i = 0; i < horizontal; i++) {
            spawn(new Vec3(between(bounds.minX, bounds.maxX), bounds.maxY + offset, between(bounds.minZ, bounds.maxZ)));
            spawn(new Vec3(between(bounds.minX, bounds.maxX), bounds.minY - offset, between(bounds.minZ, bounds.maxZ)));
        }
    }
    private void spawn(Vec3 center) { particles.add(new Particle(center)); emitted++; }
    private double between(double low, double high) { return low + random.nextFloat() * (high - low); }
}
