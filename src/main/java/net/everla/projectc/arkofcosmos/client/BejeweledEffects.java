package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original gem Dust (86..91,262). Projectile alpha is 255: its visible trail is Dust. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class BejeweledEffects {
    private BejeweledEffects(){}
    private static final Random RANDOM=new Random();
    private static final int[] TYPES={86,87,88,89,262,90,91};
    private static final List<Dust> LIVE=new ArrayList<>();
    private static final Map<UUID,Seen> SEEN=new HashMap<>();
    private static Object world;
    private static final class Seen {int age=-1;boolean burst;long touched;}
    private static final class Dust {
        Vec3 at,previous,velocity;double scale,oldScale,rotation,oldRotation;int type,frame,owner;boolean noGravity,noLight;
    }
    private static boolean world(){
        var level=Minecraft.getInstance().level;
        if(level!=world){world=level;LIVE.clear();SEEN.clear();}
        return level!=null;
    }
    private static Vec3 vector(CompoundTag tag){return new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));}
    private static Vec3 velocity(CompoundTag tag){return new Vec3(tag.getDouble("VX"),tag.getDouble("VY"),tag.getDouble("VZ"));}
    private static Vec3 unit(){double y=RANDOM.nextDouble()*2-1,a=RANDOM.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);return new Vec3(r*Math.cos(a),y,r*Math.sin(a));}
    private static Dust dust(Vec3 at,Vec3 base,int type,int owner){
        Dust d=new Dust();d.type=type;d.owner=owner;d.frame=RANDOM.nextInt(3);
        double offsetX=RANDOM.nextInt(6)-1,offsetY=RANDOM.nextInt(6)-1;
        d.at=d.previous=at.add(unit().scale(Math.hypot(offsetX,offsetY)/16d));
        double x=(RANDOM.nextInt(41)-20)*.1,y=(RANDOM.nextInt(41)-20)*.1;
        // Spread in 3D, preserving the original two-component resultant speed magnitude.
        Vec3 direction=unit().scale(Math.hypot(x,y)/16).add(base);
        d.velocity=direction.normalize().scale(Math.hypot(x+base.length()*16,y)/16);
        d.scale=(1+(RANDOM.nextInt(41)-20)*.01)*1.2;
        return d;
    }
    private static void add(Dust d){d.oldScale=d.scale;LIVE.add(d);}
    public static void receive(CompoundTag data){
        if(!world())return;
        Seen seen=SEEN.computeIfAbsent(data.getUUID("Attack"),k->new Seen());
        seen.touched=Minecraft.getInstance().level.getGameTime();
        int type=TYPES[Math.floorMod(data.getInt("Variant"),7)],owner=data.getInt("Owner");
        var samples=data.getList("Samples",10);
        for(int i=0;i<samples.size();i++){
            var sample=samples.getCompound(i);int age=sample.getInt("Age");if(age<=seen.age)continue;
            seen.age=age;
            // AI_029: exactly two trials, each with probability 1/5, per source frame.
            for(int trial=0;trial<2;trial++)if(RANDOM.nextInt(5)==0){
                Dust d=dust(vector(sample),velocity(sample),type,owner);d.noGravity=true;d.velocity=d.velocity.scale(.3);add(d);
            }
        }
        if(data.getBoolean("Burst")&&!seen.burst){
            seen.burst=true;
            Vec3 at=vector(data),v=velocity(data),direction=v.length()<1/16d?Vec3.ZERO:v.normalize();
            for(int i=0;i<15;i++){
                Dust d=dust(at,direction.scale(1/16d),type,owner);d.noGravity=true;d.scale*=1.25;
                d.velocity=d.velocity.scale(2.2).add(direction.scale(1.5/16)).scale(RANDOM.nextFloat());add(d);
            }
            for(int i=0;i<30;i++){
                Dust d=dust(at,direction.scale(1/16d),type,owner);d.noGravity=RANDOM.nextInt(5)!=0;
                d.scale*=1+RANDOM.nextFloat();d.velocity=d.velocity.scale(4.2);
                if(!d.noGravity){d.scale*=.6;d.noLight=true;}
                d.velocity=d.velocity.scale(RANDOM.nextFloat());add(d);
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!world()||Minecraft.getInstance().isPaused())return;
        long now=Minecraft.getInstance().level.getGameTime();SEEN.values().removeIf(s->now-s.touched>120);
        for(var it=LIVE.iterator();it.hasNext();){
            Dust d=it.next();d.previous=d.at;d.oldScale=d.scale;d.oldRotation=d.rotation;
            for(int sub=0;sub<3;sub++){
                d.at=d.at.add(d.velocity);d.velocity=d.velocity.multiply(.99,1,.99);
                d.rotation+=d.velocity.x*16*.5;d.scale-=.01;
                if(d.noGravity){d.velocity=d.velocity.scale(.92);d.scale-=.04;}
            }
            if(d.scale<.1)it.remove();
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||LIVE.isEmpty())return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var frame=NativeSprites.billboard();
        double partial=event.getPartialTick();
        for(Dust d:LIVE){
            double scale=d.oldScale+(d.scale-d.oldScale)*partial;
            // Full-bright additive GetAlpha; noLight debris uses the world's sampled light.
            int light=d.noLight?net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(d.at)):15728880;
            float alpha=d.noLight?205/255f:1;
            new NativeSprites.Draw(event.getPoseStack(),buffers,event.getCamera().getPosition(),d.owner)
                .region("fx/bejeweled_dust",d.previous.lerp(d.at,partial),frame,d.oldRotation+(d.rotation-d.oldRotation)*partial,scale,4,4,new float[]{1,1,1,alpha},d.type%100*10,(d.type/100*3+d.frame)*10,8,8,!d.noLight,light);
        }
        buffers.endBatch();
    }
}
