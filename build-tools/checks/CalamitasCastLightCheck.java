import java.util.*;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.minecraft.world.phys.Vec3;

public final class CalamitasCastLightCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double a,double b,String message){require(Math.abs(a-b)<2e-5,message+": "+a+" != "+b);}
    public static void main(String[] args){
        var body=new Vec3(23,90,-37);var positive=body.add(500d/16,0,0);var negative=body.add(-500d/16,0,0);int orbs=0,blooms=0;
        for(int delay=149;delay>=0;delay--){
            var out=new ArrayList<SupremeCalamitasSparks.Light>();SupremeCalamitasSparks.brotherLights(delay,body,positive,negative,16,out::add);
            int expected=delay>=60&&delay<105?2:delay==59?4:0;require(out.size()==expected,"source light boundary "+delay);
            for(int i=0;i<out.size();i++){
                var light=out.get(i);
                if(light.bloom()){
                    blooms++;require(light.lifetime()==59,"bloom life");require(light.scale()==(i%2==0?1.4f:1f),"source paired final scales");
                    require(light.position().equals(i<2?negative:positive),"bloom locked center");require(light.color()==(i<2?0x00BFFF:0x79154D),"bloom source palette");
                }else{
                    orbs++;require(light.lifetime()==20&&light.color()==0xFF0000,"orb life/color");
                    near(light.scale(),2.8f-delay*.01f,"orb scale");
                    float t=(delay-105f)/(60f-105f);double squared=t*t,cubed=squared*t;double factor=.5*t+1.5*squared-cubed;
                    near((light.position().x-body.x)*16,(i==0?-1:1)*(44*.45f+(500-44*.45f)*factor),"source horizontal spline");
                    near((light.position().y-body.y)*16,-21+510.5*t-468.5*squared-21*cubed,"source curved rise");
                    near(light.position().z,body.z,"source locked cast plane");
                    if(delay==60)near(light.position().distanceTo(i==0?negative:positive),0,"orb arrives at locked center");
                }
            }
        }
        require(orbs==90&&blooms==4,"source full-cast90orbs4blooms");
        System.out.println("Calamitas cast light geometry passed: source constant44x42, 90 Catmull-Rom orbs, 4 bloom colors/scales/lifetimes and all150 boundaries");
    }
}
