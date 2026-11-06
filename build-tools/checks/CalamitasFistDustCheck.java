import java.util.ArrayList;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.minecraft.world.phys.Vec3;

public final class CalamitasFistDustCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-6, message);
    }
    public static void main(String[] args) {
        var random = new Random(2796625);
        Vec3 center = new Vec3(3, 20, -8), plane = new Vec3(.6, 0, .8);
        for (int mode = 0; mode < 3; mode++) for (int sample = 0; sample < 10000; sample++) {
            var dusts = new ArrayList<SupremeCalamitasSparks.SlashDust>();
            SupremeCalamitasSparks.fistDust(mode, center, plane, 16, random, dusts::add);
            require(dusts.size() == (mode == 0 ? 1 : mode == 1 ? 50 : 10), "exact source quantity");
            for (int i = 0; i < dusts.size(); i++) {
                var emitted = dusts.get(i); var dust = emitted.dust();
                require(dust.type() == (mode == 2 ? 66 : 279), "native Dust ID");
                require(dust.variant() >= 0 && dust.variant() < 3, "source frame range");
                require(emitted.color() == (mode == 2 ? 0xFF007F : 0xFF0000), "source byte color");
                require(emitted.alpha() == 0, "source alpha");
                require(dust.scale() >= (mode == 0 ? .9f : 1.3f)
                        && dust.scale() < (mode == 0 ? 1.2f : 1.9f), "source scale");
                var offset = dust.position().subtract(center);
                if (mode == 1) {
                    near(offset.length() * 16, i < 25 ? 7 : 5, "ring radius");
                    near(dust.velocity().length() * 16, i < 25 ? 9 : 7, "ring speed");
                    near(offset.dot(new Vec3(-.8, 0, .6)), 0, "locked plane");
                    near(offset.normalize().dot(dust.velocity().normalize()), 1, "radial velocity");
                    var next = dusts.get(i / 25 * 25 + (i + 1) % 25).dust();
                    near(offset.normalize().dot(next.position().subtract(center).normalize()),
                            Math.cos(Math.PI * 2 / 25), "25 evenly spaced points including wrap");
                } else {
                    double radial = offset.length() * 16 / (mode == 0 ? 2 : 1);
                    require(radial >= Math.sqrt(2) * (mode == 0 ? 14 * .1f : 7 * .8f) - 1e-5
                            && radial < Math.sqrt(2) * (mode == 0 ? 14 * 2.5f : 7 * 1.2f) + 1e-5,
                            "3D preserves source radial magnitude");
                    double ratio = dust.velocity().length() / offset.length();
                    require(mode == 0 ? Math.abs(ratio - .5) < 1e-6 : ratio >= .2f - 1e-6 && ratio < 1,
                            "source position and velocity relation");
                }
            }
        }
        for (int sample = 0; sample < 10000; sample++) {
            var pulses = new ArrayList<SupremeCalamitasSparks.Pulse>();
            SupremeCalamitasSparks.fistPulses(random, pulses::add);
            require(pulses.size() == 3, "three pulse layers");
            near(pulses.get(0).scale(), pulses.get(1).scale(), "paired shared size");
            near(pulses.get(0).scale() / 1.7f, pulses.get(2).scale() / 1.4f, "white shared size");
            for (int i = 0; i < 3; i++) {
                var p = pulses.get(i); double multiplier = i < 2 ? 1.7f : 1.4f;
                require(p.scale() >= .8f * multiplier - 1e-6 && p.scale() < 1.2f * multiplier + 1e-6, "pulse size range");
                require(p.color() == (i < 2 ? 0xFF007F : 0xE5E5E5), "pulse source byte color");
                require(p.rotation() >= -10 && p.rotation() < 10, "pulse source rotation");
            }
        }
        Vec3 shoot = plane.scale(.3).add(0, -.4, 0);
        for (boolean alive : new boolean[]{true, false}) {
            var sparks = new ArrayList<SupremeCalamitasSparks.Spark>();
            SupremeCalamitasSparks.fistTelegraph(alive, center, shoot, net.everla.projectc.arkofcosmos.calamity.CalamitasSpace.fistAxis(shoot, plane), 35, sparks::add);
            require(sparks.size() == (alive ? 28 : 84), "telegraph original quantity");
            for (int i = 0; i < sparks.size(); i++) {
                var spark = sparks.get(i);
                near(spark.velocity().length(), shoot.length() * 1.8, "telegraph source magnitude");
                near(spark.position().subtract(center).subtract(spark.velocity().scale(9 + i % 28 * 11)).length(), 0, "telegraph spacing");
                near(spark.velocity().dot(plane.cross(new Vec3(0, -1, 0))), 0, "telegraph locked plane");
                require(spark.lifetime() == 7 && spark.scale() == .17f && spark.color() == 0xFF007F, "telegraph parameters");
            }
            if (!alive) near(sparks.get(0).velocity().dot(sparks.get(28).velocity()) / sparks.get(0).velocity().lengthSqr(), -.5, "120 degree branches");
        }
        System.out.println("PASS: fist Dust three modes, 30000 samples, exact counts1/50/10, ring plane/radius/speed/spacing, source color/scale and 3D magnitude");
        System.out.println("PASS: fist pulse 10000 shared-size samples and telegraph28/84 source count/spacing/velocity/plane/color");
    }
}
