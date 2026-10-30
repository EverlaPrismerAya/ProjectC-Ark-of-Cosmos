package net.everla.projectc.arkofcosmos.calamity.combat;

import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;

/** ArkoftheElements source rules, with the explicitly selected Minecraft balance. */
public final class ElementsRules {
    private ElementsRules() {}
    public static final float BASE_DAMAGE = 5;
    public static final int MAX_CHARGE = 10, PARRY_WINDOW = 15, PARRY_COOLDOWN = 340;
    public static final double MAX_REACH = 500 / 16d;
    public static int mode(int combo) { return combo == 4 ? 2 : combo % 2; }
    public static boolean snapWindow(int age) { return age >= 12 && age < 60; }
    public static float charged(float base, int charge) { return charge > 0 ? base * 1.35f : base; }
    public static float blastDamage(int charge, int hits) { return BASE_DAMAGE * charge * 1.35f * .5f * (float)Math.pow(.25, hits * .1); }
    public static int afterAttack(int charge) { return Math.max(0, charge - 1); }
    public static double throwRatio(double t) {
        t = clamp(t);
        if (t < .25) return Math.sqrt(1 - Math.pow(t / .25 - 1, 2));
        if (t < .75) return 1;
        return Math.sqrt(1 - Math.pow((t - .75) / .25, 2));
    }
    public static int stitches(double age) {
        double t = clamp((age - 32.5) / 37.5 * 3);
        return (int)Math.ceil((1 - Math.sqrt(1 - t*t)) * 8);
    }
    public static double stitchBorn(int index) { return Math.floor(32.5 + 12.5 * Math.sqrt(1 - Math.pow(1 - index / 8d, 2))) + 1; }
    public static double clamp(double t) { return Math.max(0, Math.min(1, t)); }
    public static float thrust(double age) { return ArkCurves.blastThrust((float)clamp(age / 25)); }
    public static boolean dotDue(int elapsed) { return elapsed > 0 && elapsed % 5 == 0; }
}
