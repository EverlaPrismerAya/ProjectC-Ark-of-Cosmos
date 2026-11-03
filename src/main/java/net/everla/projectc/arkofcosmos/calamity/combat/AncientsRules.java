package net.everla.projectc.arkofcosmos.calamity.combat;

import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;

/** Calamity Team: TrueArkoftheAncients and its projectiles, local DLL snapshot in reference/ancients. */
public final class AncientsRules {
    private AncientsRules() {}
    public static final float BASE_DAMAGE=6;
    public static final int USE_TIME=25, SWING_UPDATES=40, BEAM_FRAMES=75, MAX_CHARGE=10;
    public static float damage(int charge){return charge>0?(int)(BASE_DAMAGE*1.25f):BASE_DAMAGE;}
    public static float beamDamage(float damage){return (int)damage;}
    public static float starDamage(float damage){return (int)(damage*.6f);}
    public static float blastDamage(int charge,int hits){return (int)(BASE_DAMAGE*charge*1.25f*.5f)*(float)Math.pow(.25,hits*.1);}
    public static float ratio(float age){return ArkCurves.swing(age/SWING_UPDATES);}
    public static float rotation(float age,int sign){return (float)(Math.PI*3/8*sign*(1-2*ratio(age)));}
    public static float scale(float age,int charge){return 1.6f+(float)Math.sin(ratio(age)*Math.PI)*.65f+(charge>0?.6f:0);}
    public static double beamDeceleration(int age){return 1-Math.pow(age/(double)BEAM_FRAMES,3);}
    public static boolean beamTiles(int age){return age>5;}
}
