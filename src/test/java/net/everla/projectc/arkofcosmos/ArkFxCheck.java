package net.everla.projectc.arkofcosmos;

import net.minecraft.world.phys.Vec3;

/** Numerical source checkpoints, runnable without a window or GL context. */
public final class ArkFxCheck {
    public static void main(String[] args) {
        if (!ArkFxDraw.ownerFirstPerson(7,7,true,true)
                || ArkFxDraw.ownerFirstPerson(7,8,true,true)
                || ArkFxDraw.ownerFirstPerson(7,7,false,true)
                || ArkFxDraw.ownerFirstPerson(7,7,true,false)
                || ArkFxDraw.ownerFirstPerson(-1,-1,true,true))
            throw new AssertionError("Near fade must only affect the owner in first person");
        near(ArkFxDraw.nearOpacity(0), .12);
        near(ArkFxDraw.nearOpacity(.5), .12);
        near(ArkFxDraw.nearOpacity(1.75), .56);
        near(ArkFxDraw.nearOpacity(3), 1);
        near(ArkFxDraw.nearOpacity(30), 1);
        ArkFxDraw.fadeEye = Vec3.ZERO;
        near(ArkFxDraw.opacityAt(Vec3.ZERO), .12);
        ArkFxDraw.fadeEye = null;
        near(ArkFxDraw.opacityAt(Vec3.ZERO), 1);
        float[] half=ArkEffects.mul(ArkEffects.rgb(210,105,30),.5);
        near(half[0],105d/255);near(half[3],.5);
        float[] green=ArkEffects.shift(ArkEffects.rgb(255,0,0),1d/3);
        near(green[0],0);near(green[1],1);near(green[2],0);
        AttackFrame plane=new AttackFrame(new Vec3(1,0,0),new Vec3(0,1,0));
        ArkFxParticle spark=new ArkFxParticle(ArkFxParticle.Type.SPARKLE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,20,plane);
        spark.spin=.1;spark.rotation=0;spark.update();
        near(spark.position.x,1);near(spark.velocity.x,.95);near(spark.opacity,Math.sin(Math.PI/20));near(spark.rotation,.1);
        ArkFxParticle smoke=new ArkFxParticle(ArkFxParticle.Type.SMOKE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,10,plane);
        smoke.opacity=.28;smoke.update();near(smoke.scale,1.01);near(smoke.opacity,.2744);near(smoke.velocity.x,.85);
        smoke.update();near(smoke.scale,1.01*.975);
        ArkFxParticle flare=new ArkFxParticle(ArkFxParticle.Type.FLARE,Vec3.ZERO,new Vec3(1,0,0),ArkEffects.WHITE,1,20,plane);
        flare.delay=2;flare.update();flare.update();near(flare.time,0);near(flare.position.length(),0);
        flare.update();near(flare.time,1);near(flare.position.x,1);
        ArkFxParticle ring=new ArkFxParticle(ArkFxParticle.Type.RING,Vec3.ZERO,Vec3.ZERO,ArkEffects.WHITE,.05,8,plane);
        ring.originalScale=.05;ring.finalScale=1.8;ring.update();near(ring.scale,.05+1.75*(1-Math.pow(.875,4)));
        if(ArkFxDraw.FORMAT.getVertexSize()!=28)throw new AssertionError("Position/color/float3 UV stride");
        System.out.println("Ark FX source checkpoints passed");
    }
    private static void near(double actual,double expected){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(actual+" != "+expected);}
}
