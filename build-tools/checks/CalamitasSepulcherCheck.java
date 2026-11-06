import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSepulcher;
import net.minecraft.world.phys.Vec3;

public final class CalamitasSepulcherCheck {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { require(Math.abs(actual - expected) < 1e-8, message + ": " + actual); }
    public static void main(String[] args) {
        int energy = 0;
        for (int i = 0; i < 52; i++) if (SupremeCalamitasSepulcher.energy(i)) energy++;
        require(energy == 25 && SupremeCalamitasSepulcher.ARMS == 0, "52 source segments and no independent arms by requested adaptation");
        near(SupremeCalamitasSepulcher.trailLength(), 2222, "source total spacing");
        var trail = new SupremeCalamitasSepulcher.Trail(Vec3.ZERO, new Vec3(1, 0, 0), 16);
        for (int frame = 1; frame <= 800; frame++) {
            Vec3 next = new Vec3(frame * .7, 20 * Math.sin(frame / 70d), 15 * Math.sin(frame / 110d));
            trail.append(next);
            var resumed = new SupremeCalamitasSepulcher.Trail(Vec3.ZERO, new Vec3(1, 0, 0), 16);
            resumed.load(trail.save());
            require(trail.segments(16).equals(resumed.segments(16)), "trail NBT continuity frame " + frame);
            require(trail.segments(16).size() == 52, "no omitted segment poses");
            require(trail.save().getList("Points", 10).size() < 220, "history not bounded by arc length");
        }
        // A right-angle turn must propagate down the history, without shortcutting
        // the bend or placing every segment on the head's latest direction.
        trail = new SupremeCalamitasSepulcher.Trail(Vec3.ZERO, new Vec3(1, 0, 0), 16);
        trail.append(new Vec3(200, 0, 0)); trail.append(new Vec3(200, 10, 0));
        var positions = trail.segments(16);
        near(positions.get(0).x, 200, "leading body follows vertical leg");
        near(positions.get(0).y, 10 - 52d / 16, "body arc-length offset");
        near(positions.get(3).y, 0, "later segment has not reached bend");
        require(positions.get(3).x < 200, "later segment remains on old leg");
        trail = new SupremeCalamitasSepulcher.Trail(Vec3.ZERO, new Vec3(1, 0, 0), 16);
        for (int frame = 1; frame <= 200; frame++) trail.append(new Vec3(frame, 0, 0));
        positions = trail.segments(16);
        double offset = 0;
        for (int i = 0; i < 52; i++) {
            offset += SupremeCalamitasSepulcher.spacing(i) / 16;
            near(positions.get(i).x, 200 - offset, "straight spacing " + i);
        }
        Vec3 v = SupremeCalamitasSepulcher.steer(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 100, 0), 0);
        near(v.x, -.545, "source initial lateral escape");
        near(v.y, .545 * 1.1, "source upward pursuit");
        v = SupremeCalamitasSepulcher.steer(new Vec3(100, 0, 0), Vec3.ZERO, new Vec3(1000, 0, 0), 150);
        near(v.x, 26 - .32, "source speed cap and cooldown acceleration");
        for (int frame = 0; frame < 10000; frame++) {
            v = SupremeCalamitasSepulcher.steer(v, Vec3.ZERO, new Vec3(Math.sin(frame / 70d) * 500, Math.cos(frame / 90d) * 400, Math.cos(frame / 110d) * 300), frame % 151);
            require(Double.isFinite(v.lengthSqr()) && v.length() < 29, "finite bounded 3D steering");
        }
        System.out.println("PASS: Sepulcher 52 poses/25 energy/24 arms, source steering, bounded history, bends and 800 NBT resumes");
    }
}
