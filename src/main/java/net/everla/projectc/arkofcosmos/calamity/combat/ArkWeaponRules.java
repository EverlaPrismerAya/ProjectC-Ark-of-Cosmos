package net.everla.projectc.arkofcosmos.calamity.combat;

import net.minecraft.util.Mth;
/** Calamity Team weapon rules with the existing Minecraft damage/charge adaptation.
 * Derived content: see THIRD_PARTY_NOTICES.md. Package location does not relicense code.
 */
public final class ArkWeaponRules {
    private ArkWeaponRules() {}
    public static final float BASE_DAMAGE = 16f;
    public static final int MAX_CHARGE = 16;
    public static float scaled(float damage, float multiplier) { return (int)(damage * (1700f / BASE_DAMAGE) * multiplier) / (1700f / BASE_DAMAGE); }
    public static int comboMode(int combo) { return combo == 4 ? 2 : combo % 2; }
    public static int tooltipColor(String key, float wave) {
        return switch (key) {
            case "controls" -> tooltipColor(0xFFD700, 0xDAA520, .5f + wave * .5f);
            case "parry" -> tooltipColor(0x00FFFF, 0x00BFFF, .5f + wave * .75f);
            case "blast" -> tooltipColor(0xFF69B4, 0xDC143C, .5f + wave * .625f);
            default -> 0xFFFFFF;
        };
    }
    private static int tooltipColor(int from, int to, float amount) {
        amount = Mth.clamp(amount, 0, 1);
        int red = (int)Mth.lerp(amount, from >> 16 & 255, to >> 16 & 255);
        int green = (int)Mth.lerp(amount, from >> 8 & 255, to >> 8 & 255);
        int blue = (int)Mth.lerp(amount, from & 255, to & 255);
        return red << 16 | green << 8 | blue;
    }
}
