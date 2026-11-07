package net.everla.projectc.arkofcosmos.enchant;
/** Calamity revision 1a8cebd: 16 source pixels/block, 60 source frames/second. */
public final class CurseMath {
    private CurseMath() {}
    public static double smooth(double a,double b,double t){t=Math.max(0,Math.min(1,t));return a+(b-a)*t*t*(3-2*t);}
    public static double resentful(double blocks){return smooth(.7,1.45,(blocks*16-250)/2150);}
    public static double bloodthirsty(double blocks){return smooth(.75,1.75,(400-blocks*16)/225);}
    public static double ephemeral(double charge){return .77+.49*(Math.pow(2,Math.max(0,Math.min(1,charge/1600)))-1);}
    public static double discharge(double charge,double useFrames){return Math.max(.001,charge-Math.max(10,useFrames*2.25));}
    public static double hellbound(double frames){return smooth(1,1.6,frames/2400);}
    public static double withering(double damage){return damage<=1?0:Math.min(1,Math.log(damage)*Math.pow(damage,2d/3)/177000);}
    public static int soulCost(int cost){return (int)Math.ceil(cost*.7);}
    public static double health(double source,double maximum){return source*maximum/400;}
}
