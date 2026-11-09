package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.FirstFractalEntity;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Source Dust 278; independent lifetime so particles survive the sword's removal. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class FirstFractalParticles {
    private FirstFractalParticles(){}
    private static final Random RANDOM=new Random();
    private static final Map<UUID,Integer> SEEN=new HashMap<>();
    private static final List<Dust> LIVE=new ArrayList<>();
    private static Object world;
    private static final class Dust {
        Vec3 at,previous,velocity;double scale=.7,oldScale=.7,rotation,oldRotation;int variant,owner;float[] color;
    }
    private static void emit(CompoundTag s){
        var p=new Dust();p.at=p.previous=vector(s,"Position");p.owner=s.getInt("Owner");p.variant=RANDOM.nextInt(3);
        // The source random drift is spread spatially at the same speed magnitude.
        double vx=(RANDOM.nextInt(41)-20)*.1,vy=(RANDOM.nextInt(41)-20)*.1;
        double z=RANDOM.nextDouble()*2-1,angle=RANDOM.nextDouble()*Math.PI*2,r=Math.sqrt(1-z*z);
        p.velocity=new Vec3(r*Math.cos(angle),z,r*Math.sin(angle)).scale(Math.hypot(vx,vy)/32).add(vector(s,"Velocity").scale(2));
        p.color=FirstFractalRenderer.color(s.getFloat("Hue"),1);float whiten=RANDOM.nextFloat()*.3f;
        for(int i=0;i<3;i++)p.color[i]+=(1-p.color[i])*whiten;
        LIVE.add(p);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var level=Minecraft.getInstance().level;
        if(level!=world){world=level;LIVE.clear();SEEN.clear();}if(level==null)return;
        for(var it=LIVE.iterator();it.hasNext();){
            var p=it.next();p.previous=p.at;p.oldScale=p.scale;p.oldRotation=p.rotation;
            for(int source=0;source<3;source++){
                p.at=p.at.add(p.velocity);p.velocity=p.velocity.multiply(.99,1,.99);
                // Common Dust.UpdateDust rotation and fade before the type-specific tail.
                p.rotation+=p.velocity.x*8;p.scale-=.01;
                p.velocity=p.velocity.scale(.93);p.scale+=.0025;
            }
            if(p.scale<.1)it.remove();
        }
        Set<UUID> active=new HashSet<>();
        for(var entity:level.entitiesForRendering())if(entity instanceof FirstFractalEntity sword){
            active.add(sword.getUUID());int seen=SEEN.getOrDefault(sword.getUUID(),-1);var s=sword.view();
            var samples=s.getList("Samples",10);
            for(int i=0;i<samples.size();i++){var sample=samples.getCompound(i);if(sample.getInt("Age")>seen&&sample.getBoolean("Dust"))emit(sample);}
            SEEN.put(sword.getUUID(),s.getInt("Age"));
        }
        SEEN.keySet().retainAll(active);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||LIVE.isEmpty())return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var frame=NativeSprites.billboard();
        float partial=e.getPartialTick();
        for(var p:LIVE){
            var draw=new NativeSprites.Draw(e.getPoseStack(),buffers,e.getCamera().getPosition(),p.owner);
            double scale=p.oldScale+(p.scale-p.oldScale)*partial;
            // Source GetAlpha uses alpha=25 with bright RGB, represented as an additive native draw.
            draw.region("fx/first_fractal_dust",p.previous.lerp(p.at,partial),frame,p.oldRotation+(p.rotation-p.oldRotation)*partial,scale,4,4,p.color,0,p.variant*10,8,8,true,15728880);
        }
        buffers.endBatch();
    }
}
