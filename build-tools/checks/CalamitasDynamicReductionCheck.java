import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasDynamicReduction;

public final class CalamitasDynamicReductionCheck {
    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-7) throw new AssertionError(actual + " != " + expected);
    }
    private static double curve(double damage) { return damage <= 160 ? damage : 160 + 160 * Math.log(damage / 160); }
    public static void main(String[] args) {
        var reduction = new SupremeCalamitasDynamicReduction();
        near(reduction.apply(0, 160), 160);
        near(reduction.apply(0, 160), curve(320) - 160);
        near(reduction.apply(0, 320), curve(640) - curve(320));
        near(reduction.apply(19, 10), curve(650) - curve(640));
        near(reduction.apply(39, 100), 100);
        near(reduction.apply(59, 320), curve(320));
        var split = new SupremeCalamitasDynamicReduction();
        double sum = 0;
        for (int i = 0; i < 64; i++) sum += split.apply(0, 10);
        near(sum, curve(640));
        var sustained = new SupremeCalamitasDynamicReduction();
        double[] actual = new double[200];
        for (int tick = 0; tick < actual.length; tick++) {
            actual[tick] = sustained.apply(tick, 32);
            if (tick >= 19) {
                double window = 0;
                for (int j = tick - 19; j <= tick; j++) window += actual[j];
                near(window, curve(640));
            }
        }
        near(new SupremeCalamitasDynamicReduction().apply(0, 160), 160);
        near(sustained.apply(500, 160), 160);
        System.out.println("PASS: 160 DPS logarithmic curve, split hits, rolling expiry, sustained DPS and independent bosses");
    }
}
