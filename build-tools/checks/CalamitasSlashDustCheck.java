import java.util.ArrayList;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.minecraft.world.phys.Vec3;

public final class CalamitasSlashDustCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var random = new Random(27966);
        Vec3 center = new Vec3(3, 20, -8), velocity = new Vec3(.2, -.3, .4);
        int[] totals = new int[4];
        for (int mode = 0; mode < 4; mode++) for (int sample = 0; sample < 10000; sample++) {
            var dusts = new ArrayList<SupremeCalamitasSparks.SlashDust>();
            SupremeCalamitasSparks.slashDust(mode, center, velocity, 16, random, dusts::add);
            require(mode < 2 ? dusts.size() <= 1 : dusts.size() == (mode == 2 ? 3 : 10), "source count");
            totals[mode] += dusts.size();
            for (var emitted : dusts) {
                var dust = emitted.dust();
                require(dust.type() == (mode == 2 ? 279 : 66), "native ID");
                require(dust.variant() >= 0 && dust.variant() < 3, "source frame");
                require(emitted.color() == (mode == 3 ? 0x00FFFF : 0x00BFFF), "source color");
                require(emitted.alpha() == (mode == 0 ? 100 : 0), "source alpha");
                require(dust.scale() >= (mode == 2 ? 1.2f : mode == 3 ? 1.3f : .5f)
                        && dust.scale() < (mode == 2 ? 1.8f : mode == 3 ? 1.9f : .7f), "source scale");
                if (mode >= 2) {
                    Vec3 offset = dust.position().subtract(center);
                    double radial = offset.length() * 16 / (mode == 2 ? 2 : 1);
                    require(radial >= Math.sqrt(2) * (mode == 2 ? 14 * .1f : 7 * .8f) - 1e-5
                            && radial < Math.sqrt(2) * (mode == 2 ? 14 * 2.5f : 7 * 1.2f) + 1e-5, "3D preserves source magnitude");
                    double ratio = dust.velocity().length() / offset.length();
                    require(mode == 2 ? Math.abs(ratio - .5) < 1e-6 : ratio >= .2f - 1e-6 && ratio < 1, "offset/velocity relation");
                } else {
                    double ratio = -dust.velocity().dot(velocity) / velocity.lengthSqr();
                    require(ratio >= (mode == 0 ? .1f : .2f) - 1e-6 && ratio < (mode == 0 ? 1.5f : 1.2f), "backward speed");
                    require(dust.velocity().cross(velocity).lengthSqr() < 1e-20, "locked direction");
                    if (mode == 0) require(dust.position().equals(center), "carrier center");
                    else require(dust.position().subtract(center).add(velocity.normalize().scale(8.5 / 16)).length() <= 50.0 / 16, "acceleration spread");
                }
            }
        }
        require(totals[0] > 4800 && totals[0] < 5200, "carrier half probability");
        require(totals[1] > 3100 && totals[1] < 3550, "acceleration third probability");
        require(totals[2] == 30000 && totals[3] == 100000, "burst/death exact quantities");
        System.out.println("PASS: slash Dust four modes, 40000 samples, source counts/probability/speed/scale/color/alpha and 3D offsets");
    }
}
