package net.everla.projectc.arkofcosmos.calamity.client;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.world.phys.Vec3;

public final class CalamitasSparkParticleCheck {
    private static void near(double a,double b,String message){if(Math.abs(a-b)>1e-7)throw new AssertionError(message+": "+a+" != "+b);}
    public static void main(String[] args){
        var plane=new AttackFrame(new Vec3(1,0,0),new Vec3(0,-1,0));
        for (boolean noGravity : new boolean[]{false, true}) {
            var dust = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_235, Vec3.ZERO, new Vec3(1, 2, 3).scale(1d/16), new float[]{1,1,1,0}, 3, 200, plane);
            dust.noGravity = noGravity; dust.spin = 1; dust.update();
            near(dust.position.y, .125, "235 moves before native branch");
            near(dust.scale, noGravity ? 2.94f : 2.98f, "235 noGravity extra0.04 shrink");
            near(dust.velocity.x, .99f / 16 * (noGravity ? .92f : 1), "235 noGravity extra0.92 damping");
            near(dust.velocity.y, .125 * (noGravity ? .92f : 1), "235 skips generic gravity even when noGravityfalse");
            near(dust.rotation, .495f, "235 rotation before final drag");
        }
        var tracking = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_235, Vec3.ZERO, Vec3.ZERO, new float[]{1,1,1,0}, .5f, 200, plane);
        tracking.dustFadeIn = 1.9f; tracking.dustTargetPosition = new Vec3(0, 0, 20); tracking.update();
        near(tracking.scale, .497f, "235 fadeIn replaces common0.01 loss with0.007 growth");
        near(tracking.velocity.z, (1 - .497f) * 20 / 16 / 5, "235 far target4to1 steering");
        tracking = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_235, Vec3.ZERO, Vec3.ZERO, new float[]{1,1,1,0}, .5f, 200, plane);
        tracking.dustFadeIn = 1; tracking.dustTargetPosition = new Vec3(0, 0, .1); tracking.update();
        near(tracking.scale, .3494f, "235 within100px source distance shrink");
        near(tracking.velocity.z, (1 - .3494f) * 20 / 16 / 5, "235 near steering uses updated scale");
        var clears = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_235, Vec3.ZERO, Vec3.ZERO, new float[]{1,1,1,0}, 1.49f, 200, plane);
        clears.dustFadeIn = 1.1f; clears.noGravity = true; clears.update();
        near(clears.dustFadeIn, 0, "235 fade clears above threshold");near(clears.scale, 1.447f, "235 cleared fade enables final0.04 shrink same update");
        var longest = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_235, Vec3.ZERO, Vec3.ZERO, new float[]{1,1,1,0}, .5f, 200, plane);
        longest.dustFadeIn = 1; int longestSteps = 0; while(longest.time < longest.lifetime && longestSteps < 200){longest.update();longestSteps++;}
        if(longestSteps <= 100 || longestSteps >= 200)throw new AssertionError("235 fade lifetime must end by scale, not old100-step cap: "+longestSteps);
        System.out.println("PASS: native235 noGravity flags/drag/shrink, near/far fade steering/threshold and scale expiry beyond100steps");
        for (double pixels : new double[]{8, 16, 32}) for (int sign : new int[]{-1, 1}) {
            var blood = new ArkFxParticle(ArkFxParticle.Type.SOURCE_DUST_5, Vec3.ZERO,
                    new Vec3(sign * 2 / pixels, 1 / pixels, -1 / pixels), new float[]{1,1,1,1}, 1.3f, 100, plane);
            blood.pixelScale = 16 / pixels; blood.spin = sign * 2; near(blood.rotation, 0, "Blood native zero rotation");
            float scale = 1.3f;
            for (int step = 1; step <= 20; step++) {
                blood.update(); scale = (scale - .04f) - .01f;
                double decay = Math.pow(.99f, step), sum = (1 - decay) / (1 - .99f);
                near(blood.position.x, sign * 2 / pixels * sum, "Blood horizontal geometric displacement");
                near(blood.position.z, -1 / pixels * sum, "Blood depth geometric displacement");
                near(blood.position.y, step / pixels - .1f / pixels * step * (step - 1) / 2, "Blood move before native gravity");
                near(blood.velocity.y, (1 - .1f * step) / pixels, "Blood0.1px gravity");
                near(blood.scale, scale, "Blood0.04 plus common0.01 scale loss");
                near(blood.color[3], 1, "Blood opaque material no alpha fade");
            }
        }
        System.out.println("PASS: native Blood5 gravity/geometric horizontal motion/float shrink/initial rotation across pixel scales");
        for(boolean inward:new boolean[]{false,true}){
            int life=inward?9:35;float scale=inward?.1f:1.3f;float width=.6f,height=1;
            var p=new ArkFxParticle(inward?ArkFxParticle.Type.VOID_SPARK:ArkFxParticle.Type.SOURCE_SPARK,Vec3.ZERO,new Vec3(1,2,3),ArkEffects.rgb(121,21,77),scale,life,plane);
            if(inward)scale*=.357f;
            Vec3 pos=Vec3.ZERO,velocity=new Vec3(1,2,3);
            for(int time=1;time<=life;time++){
                pos=pos.add(velocity);velocity=velocity.scale(.95f);scale*=inward?.9f:.95f;width*=.7f;height*=1.25f;p.update();
                near(p.position.distanceTo(pos),0,"move before damping");near(p.velocity.distanceTo(velocity),0,"velocity damping");near(p.scale,scale,"source float scale");
                float fade=(float)Math.pow((float)time/life,3);
                near(p.color[0],(int)(121*(1-fade))/255f,"cubic source color");
                if(inward){near(p.originalX,width,"void horizontal shrink");near(p.originalY,height,"void vertical stretch");}
            }
            near(p.color[3],0,"source expiry fade");
        }
        var orb=new ArkFxParticle(ArkFxParticle.Type.SOURCE_ORB,Vec3.ZERO,Vec3.ZERO,ArkEffects.rgb(255,0,0),2.1f,20,plane);
        for(var type:new ArkFxParticle.Type[]{ArkFxParticle.Type.SOURCE_SPARK,ArkFxParticle.Type.VOID_SPARK}) {
            int alpha=type==ArkFxParticle.Type.SOURCE_SPARK?89:178;
            var p=new ArkFxParticle(type,Vec3.ZERO,Vec3.ZERO,new float[]{0,66/255f,alpha/255f,alpha/255f},5,9,plane);
            for(int time=1;time<=9;time++) {p.update();float fade=(float)Math.pow(time/9f,3);
                near(p.color[3],(int)(alpha*(1-fade))/255f,"slash source alpha retained through cubic fade");}
        }
        float expectedScale=2.1f,center=1;
        for(int time=1;time<=20;time++){
            expectedScale*=.93f;center-=.1f;orb.update();near(orb.scale,expectedScale,"orb float shrink");near(orb.opacity,center,"orb center flash fades independently");
            float t=(float)Math.pow(time/20f,3);near(orb.color[0],(int)(255+(51-255)*t)/255f,"orb cubic fade to20percent");
        }
        near(orb.color[0],.2f,"orb final color remains20percent");
        var glow=new ArkFxParticle(ArkFxParticle.Type.SOURCE_BLOOM,Vec3.ZERO,Vec3.ZERO,ArkEffects.rgb(121,21,77),0,59,plane);glow.finalScale=1.4f;
        for(int time=1;time<=59;time++){
            glow.update();float progress=1-(float)Math.pow(1-time/59f,4);near(glow.scale,1.4f*progress,"bloom source PolyOut4 growth");
            near(glow.color[0],121/255f,"source nonfading bloom color");near(glow.color[3],1,"source nonfading bloom alpha");
        }
        System.out.println("Calamitas GlowOrb20percent/center flash and nonfading Bloom PolyOut4 source laws passed");
        System.out.println("Calamitas Spark/VoidSpark source move/time/update order, float scales, cubic fade and void stretch passed");
    }
}
