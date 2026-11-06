import net.everla.projectc.arkofcosmos.calamity.client.CalamitasArenaVisual;
import net.minecraft.world.phys.AABB;

public final class CalamitasArenaVisualCheck {
    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        var bounds = new AABB(-62.5, -62.5, -62.5, 62.5, 62.5, 62.5);
        var visual = new CalamitasArenaVisual(42); visual.snapshot(bounds, 16, 0xdc143c);
        boolean[] faces = new boolean[6];
        visual.step(); require(visual.emitted() == 80, "source W/H counts, not six-wall multiplication");
        var first = visual.particles().get(0);
        require(visual.previousStrip() == null && visual.currentStrip().bounds().equals(bounds), "first static strip generation");
        var shrinking = new CalamitasArenaVisual(7);
        shrinking.snapshot(bounds, 16, 0xdc143c); shrinking.step();
        var inner = bounds.deflate(2);
        shrinking.snapshot(inner, 16, 0xdc143c); shrinking.step();
        require(shrinking.previousStrip().bounds().equals(bounds) && shrinking.currentStrip().bounds().equals(inner), "shrinking source masks overlap for one source frame");
        shrinking.step();
        require(shrinking.previousStrip().bounds().equals(inner), "age2 mask removed without retaining historical bounds");
        for (int frame = 0; frame < 100; frame++) {
            for (var p : visual.particles()) {
                var a = p.center;
                if (a.x == -63) faces[0] = true; else if (a.x == 63) faces[1] = true;
                else if (a.y == -63) faces[2] = true; else if (a.y == 63) faces[3] = true;
                else if (a.z == -63) faces[4] = true; else if (a.z == 63) faces[5] = true;
                else throw new AssertionError("particle lacks source eight-pixel outward face offset");
                require(a.x >= -63 && a.x <= 63 && a.y >= -63 && a.y <= 63 && a.z >= -63 && a.z <= 63, "outside wall range");
            }
            visual.step();
            if (frame == 0) require(first.age == 1 && first.size == 16, "first source age stays unscaled");
            if (frame == 1) require(first.size == 16 * .95f, "second age source float shrink");
        }
        for (boolean covered : faces) require(covered, "all six faces receive source-count particles");
        require(!visual.particles().contains(first), "old particles retire at size <=2");
        require(visual.emitted() == 8080, "101 source frames with exactly80 particles");
        require(visual.particles().size() <= 80 * 43, "bounded source lifetime");
        visual.retire(); int frames = 0;
        while (visual.thickness() >= 4) { visual.step(); frames++; }
        require(frames == 59, "source 2000*0.9 fade stops below4 after59 frames");
        for (int i = 0; i < 43; i++) visual.step();
        require(visual.empty(), "lingering particles drain after wall disappears");
        require(visual.currentStrip() == null && visual.previousStrip() == null, "static strips drain after retirement");
        System.out.println("PASS: arena visual original80 source count, all6 faces,16px particles,0.95 lifetime,59-frame wall fade and cleanup");
    }
}
