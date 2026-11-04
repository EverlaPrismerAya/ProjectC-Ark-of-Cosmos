package net.everla.projectc.arkofcosmos.calamity.combat;

/** TrueBiomeBlade (Biome Blade), not OmegaBiomeBlade. Source clocks are projectile updates. */
public final class BiomeRules {
    private BiomeRules() {}
    public static final int PURE=0,HOT=1,COLD=2,TROPICAL=3,EVIL=4,HOLY=5,ASTRAL=6,MARINE=7;
    public static final int[] SOURCE_DAMAGE={105,122,175,150,155,60,250,300};
    public static final int[] COLORS={0xABB449,0xF08B50,0x75DFFF,0x81CF50,0xD34093,0xDC8FFF,0x40CFC4,0x6495ED};
    public static float damage(double source){return (float)(source*6/115);}
    public static float base(int mode){return damage(mode<0?115:SOURCE_DAMAGE[mode]);}
    public static double clamp(double v){return Math.max(0,Math.min(1,v));}
    public static double lerp(double a,double b,double t){return a+(b-a)*t;}
    public static double exp(double t){return t<=0?0:Math.pow(2,10*t-10);}
    public static double poly(double t){return t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;}
    public static double thrust(double t){
        if(t<.2)return -.15*Math.sin(t/.2*Math.PI);
        if(t<.35)return .9*poly((t-.2)/.15);
        if(t<.7)return .9+.1*Math.sin((t-.35)/.35*Math.PI);
        return .9-.9*poly((t-.7)/.3);
    }
    public static double thrustScale(double t){return t<.1?exp(t/.1):t<.85?1:1-exp((t-.85)/.15);}
    public static double monolithWidth(double t){
        t=clamp(t);if(t<.25)return .2;if(t<.4)return .2+Math.sin((t-.25)/.15*Math.PI/2);
        if(t<.5){double x=(t-.4)/.1;return 1.2-.2*(1-Math.sqrt(1-x*x));}return 1;
    }
    public static double monolithHeight(double t){
        t=clamp(t);if(t<.2){double x=t/.2;return .15*(1-Math.sqrt(1-x*x));}
        if(t<.25)return .15+Math.sin((t-.2)/.05*Math.PI/2);
        if(t<.7){double x=(t-.25)/.45;return 1.15-.15*(1-Math.sqrt(1-x*x));}
        return 1-.1*(t>=1?1:1-Math.pow(2,-10*(t-.7)/.3));
    }
    public static int steps(int kind,int mode){return kind==0?(mode==TROPICAL?9:mode<=PURE||mode==MARINE?3:6):kind==1||kind==2?6:3;}
}
