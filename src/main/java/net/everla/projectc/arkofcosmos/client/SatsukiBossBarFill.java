package net.everla.projectc.arkofcosmos.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import com.Polarice3.Goety.common.entities.boss.Apostle;

/** Shared fill for RF's legacy Goety hook and modern native boss bar. */
public final class SatsukiBossBarFill {
    private SatsukiBossBarFill() {}
    private static final java.util.Map<Apostle,double[]> ark$hurtStates=new java.util.WeakHashMap<>();
    private static float ark$hurtFade;
    private static long ark$noiseFrame;

    public static void update(Apostle apostle,float partial){
        double now=apostle.level().getGameTime()+partial;
        float health=apostle.getHealth();
        double[] state=ark$hurtStates.computeIfAbsent(apostle,key->new double[]{health,Double.NEGATIVE_INFINITY});
        if(health<state[0])state[1]=now;
        state[0]=health;
        ark$hurtFade=(float)Math.max(0,1-(now-state[1])/10);
        ark$noiseFrame=(long)(now*3);
    }

    public static void fill(GuiGraphics graphics,int x,int y,float health,int height,float alpha){
        if(health<1||alpha<=0)return;
        graphics.flush();
        float hue=(net.minecraft.Util.getMillis()%12000L)/12000f;
        int opacity=(int)(255*alpha);
        for(int column=0;column<(int)health;column++){
            int rgb=Mth.hsvToRgb((hue+column/208f)%1,0.48f*(1-ark$hurtFade),1);
            graphics.fill(x+column,y,x+column+1,y+height,(opacity<<24)|rgb);
        }
        if(ark$hurtFade>0){
            int noiseAlpha=(int)(220*ark$hurtFade*alpha);
            for(int column=0;column<(int)health;column++)for(int row=0;row<height;row++){
                int hash=(int)ark$noiseFrame*73428767+column*912931+row*438289;
                hash=(hash^(hash>>>13))*1274126177;
                if((hash&3)!=0)continue;
                int gray=(hash>>>24)&255;
                graphics.fill(x+column,y+row,x+column+1,y+row+1,
                        (noiseAlpha<<24)|(gray<<16)|(gray<<8)|gray);
            }
        }
        graphics.flush();
    }

}

