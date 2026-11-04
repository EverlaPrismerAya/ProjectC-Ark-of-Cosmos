package net.everla.projectc.arkofcosmos.calamity.combat;
/** Calamity BrokenBiomeBlade constants. Source damage 38 maps to 5 Minecraft health. */
public final class BrokenBiomeRules {
 private BrokenBiomeRules(){}
 public static final int PURE=0,HOT=1,COLD=2,TROPICAL=3,EVIL=4;
 public static final int[] SOURCE_DAMAGE={38,35,40,65,50};
 public static final int[] COLORS={0xABB449,0xEE9C49,0xA5EBEB,0xA2C855,0xD34093};
 public static float damage(double source){return (float)(source*5/38);}
 public static float base(int mode){return damage(mode<0?38:SOURCE_DAMAGE[mode]);}
 public static double swordAngle(double age){return -Math.PI/3+Math.PI*2/3*BiomeRules.clamp(age/30);}
 public static int steps(int kind,int mode){return kind==1||mode<=PURE?3:mode==TROPICAL?9:6;}
}
