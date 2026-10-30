package net.everla.projectc.arkofcosmos.calamity.combat;

/** Galaxia rules derived from the local Calamity DLL. See THIRD_PARTY_NOTICES.md. */
public final class GalaxiaRules {
    private GalaxiaRules() {}
    public static final int PHOENIX = 0, ARIES = 1, POLARIS = 2, ANDROMEDA = 3;
    public static float damage(double terraria) { return (float)(terraria * 16 / 1700); }
    public static float base(int mode) { return damage(new int[]{1200, 1325, 1800, 2800}[mode]); }
    public static final int NAME_COLOR = 0x2B60DE;
    public static int tooltipColor(int mode) { return new int[]{0xFF5700, 0xC459C9, 0x80BDFF, 0x8480FF}[mode]; }
    public static int passiveColor(int mode) { return mode < 2 ? 0x4C89ED : 0xCB1977; }
    public static int attunementColor(int mode, double seconds) {
        int from = tooltipColor(mode), to = new int[]{0xFF8F00, 0xFF0000, 0xFF808C, 0xC2A6FF}[mode];
        float t = .5f + (float)Math.sin(seconds) * .5f;
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (from >> shift) & 255, b = (to >> shift) & 255;
            result |= (int)(a + (b - a) * t) << shift;
        }
        return result;
    }
    public static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    public static double throwCurve(double t) {
        if (t < .45) return Math.sqrt(1 - Math.pow(t / .45 - 1, 2));
        if (t < .6) return 1;
        double x = clamp((t - .6) / .4);
        double ease = x < .5 ? 4*x*x*x : 1 - Math.pow(-2*x+2, 3)/2;
        return 1 - 1.05 * ease;
    }
    public static double ariesDisplacement(double t) {
        t = clamp(t / 40);
        if (t < .3) return .2 + Math.pow(t / .3, 3);
        if (t < .6) return 1 + .2*Math.sin((t-.3)/.3*Math.PI);
        return 1 - .1*Math.sin((t-.6)/.4*Math.PI);
    }
    public static double ariesScale(double t) {
        t = clamp(t/30);
        return 1 + (t < .3 ? .2+Math.pow(t/.3,3) : 1+.2*Math.sin((t-.3)/.7*Math.PI));
    }
    public static double chargeDisplacement(double t) {
        t = clamp(t);
        if (t < .06) return .2*Math.pow(t/.06,3);
        if (t < .25) return .2+.1*Math.sin((t-.06)/.19*Math.PI);
        if (t < .5) return .2-.45*(t-.25)/.25;
        if (t < .93) return -.25-.2*Math.pow((t-.5)/.43,3);
        return -.45-.1*Math.sin((t-.93)/.07*Math.PI);
    }
    public static int lungeDuration(double charge) { return (7+(int)((charge/360-.25)*20))*2; }
}
