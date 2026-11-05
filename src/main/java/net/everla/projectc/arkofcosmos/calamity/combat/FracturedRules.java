package net.everla.projectc.arkofcosmos.calamity.combat;

import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;

/** FracturedArk / ArkoftheAncientsSwungBlade / AncientBeam, Calamity Team.
 * Source clocks are 60 Hz; the blade has two updates per source frame. */
public final class FracturedRules {
    private FracturedRules() {}
    public static float baseDamage() { return net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage("fractured_ark"); }
    public static final int USE_TIME = 22, SWING_UPDATES = 35, BEAM_FRAMES = 40, MAX_CHARGE = 10;
    public static float damage(int charge) { return charge > 0 ? (int)(baseDamage() * 1.5f) : baseDamage(); }
    public static float beamDamage(float damage) { return (int)(damage * .8f); }
    public static float ratio(float age) { return ArkCurves.swing(age / SWING_UPDATES); }
    public static float rotation(float age, int sign) { return (float)(Math.PI * 3 / 8 * sign * (1 - 2 * ratio(age))); }
    public static float scale(float age, int charge) { return 1.4f + (float)Math.sin(ratio(age) * Math.PI) * .6f + charge / 10f * .6f; }
    public static double beamDeceleration(int age) { return 1 - Math.pow(age / (double)BEAM_FRAMES, 3); }
    public static boolean beamTiles(int age) { return age > 5; }
}
