package net.everla.projectc.arkofcosmos.calamity.combat;

import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.world.phys.Vec3;

/** OmegaBiomeBlade and projectile rules from the local Calamity DLL. See THIRD_PARTY_NOTICES.md. */
public final class TrueBiomeRules {
    private TrueBiomeRules() {}
    public static final int PRIDE=0, CHAINED=1, FURY=2, TIDES=3;
    private static final float[] DAMAGE={4,6,4.5f,8};
    private static final int[] ORIGINAL={140,400,200,550};
    public static final int[] COLOR={0xDC69C5,0x71EFB1,0xD83716,0x846DE9};
    public static final int[] COLOR2={0xABEF71,0xA9CFFF,0xD88316,0x7AD5E9};
    public static float base(int mode){return DAMAGE[mode];}
    public static float damage(int mode,double source){return (float)(source*DAMAGE[mode]/ORIGINAL[mode]);}
    public static double clamp(double x){return Math.max(0,Math.min(1,x));}
    public static double lerp(double a,double b,double t){return a+(b-a)*t;}
    public static double shred(double charge){return clamp(charge/250);}
    public static int furyCooldown(double charge){return 30-(int)(14*shred(charge));}
    public static float furyDamage(double charge){return (float)(base(FURY)*(1+.9*shred(charge))*furyCooldown(charge)/30);}
    public static int lungeDuration(double charge){return (7+(int)((charge/500-.25)*20))*2;}
    public static double monolithWidth(double t){
        if(t<.25)return .2;
        if(t<.4)return .2+Math.sin((t-.25)/.15*Math.PI/2);
        if(t<.5){double x=clamp((t-.4)/.1);return 1.2-.2*(1-Math.sqrt(1-x*x));}
        return 1;
    }
    public static double monolithHeight(double t){
        if(t<.2){double x=clamp(t/.2);return .15*(1-Math.sqrt(1-x*x));}
        if(t<.25)return .15+Math.sin((t-.2)/.05*Math.PI/2);
        if(t<.7){double x=clamp((t-.25)/.45);return 1.15-.15*(1-Math.sqrt(1-x*x));}
        double x=clamp((t-.7)/.3);return 1-.1*(x==1?1:1-Math.pow(2,-10*x));
    }
    public static Vec3 bezier(Vec3[] controls,double t){
        Vec3[] work=controls.clone();
        for(int n=work.length-1;n>0;n--)for(int i=0;i<n;i++)work[i]=work[i].lerp(work[i+1],t);
        return work[0];
    }
    private static double noise(double seed){return .5+(Math.sin(seed*17.07947)+Math.sin(seed*.2*25.13274))*.25;}
    /** Source GenerateCurve; radians inside ToRadians are intentional source behavior. */
    public static Vec3[] chain(Vec3 center,AttackFrame frame,double angle,double seed,double shift,double orientation){
        double a=noise(seed),b=noise(a*100),c=noise(b*50),d=noise(c);
        if(orientation==-1&&a>=.5||orientation==1&&a<.5)a=1-a;
        a+=shift*.1*orientation;double sign=a>=.5?1:-1,pi=Math.PI;
        Vec3 dir=frame.direction(angle),side=frame.direction(angle+pi/2);
        Vec3 p0=center.add(frame.direction(angle+Math.toRadians(lerp(.23561947*sign,pi/4*sign,a))).scale(lerp(50,130,Math.sin(a*pi))/16));
        double ease=shift==1?1:1-Math.pow(2,-10*shift);
        Vec3 tip=center.add(frame.direction(angle+lerp(-.01*sign,.01*sign,ease)).scale(400*(.75+.75*c-.05*ease)/16));
        tip=bezier(new Vec3[]{tip.add(side.scale(50/16d)),tip.add(side.scale(50/16d)).add(dir.scale(250/16d)),tip.subtract(side.scale(50/16d)).add(dir.scale(250/16d)),tip.subtract(side.scale(50/16d))},a);
        double bend=Math.toRadians(lerp(0,160*sign,Math.sin(a*pi)));
        Vec3 offset=frame.direction(angle+bend).scale(lerp(130,200,clamp(Math.sin(a*pi)-.5)*2)/16);
        Vec3 p2=tip.add(offset);
        Vec3 p1=p2.add(frame.direction(angle+bend+pi-Math.toRadians(lerp(80*sign,110*sign,Math.sin(a*pi)))).scale(lerp(120,280,Math.sin(a*pi))/16));
        tip=tip.add(frame.direction(pi*2*b).scale(d*30/16));
        p2=p2.add(frame.direction(pi*2*c).scale(b*30/16));
        p1=p1.add(frame.direction(pi*2*d).scale(c*30/16));
        return new Vec3[]{center,p0,p1,p2,tip};
    }
}
