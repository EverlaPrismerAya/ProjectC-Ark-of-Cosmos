package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.entity.OldOneEntity.Kind;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Required visible bodies of native projectiles 118, 27 and 280. Source: 1.3.5.3 Projectile/Dust. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class OldOneParticles {
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){
            e.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.ARK_OF_THE_OLD_ONE.get(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),
                (stack,level,entity,seed)->entity!=null&&!entity.level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.OldOneEntity.class,entity.getBoundingBox().inflate(16),a->a.ownerId()==entity.getId()&&a.kind()==Kind.SWORD&&a.level().getGameTime()-a.renderState().getLong("StartTick")<5).isEmpty()?1:0));
        }
    }
    private static final Random RNG=new Random(),SPATIAL=new Random();
    private static final List<Dust> LIVE=new ArrayList<>(),DISPLAY=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static int red=255,green,blue,style,sourceStep;
    public static int[] disco(){return new int[]{red,green,blue};}
    private record State(Vec3 at,double scale,double rotation){}
    private static final class Dust {
        final int type,variant;final AttackFrame plane;boolean noGravity;
        Vec3 at,velocity;double scale,rotation;final State[] states=new State[4];double born,died=1;
        Dust(int type,Vec3 at,Vec3 velocity,double scale,boolean noGravity,AttackFrame plane){
            this.type=type;this.at=at;this.velocity=velocity;this.scale=scale;this.noGravity=noGravity;this.plane=plane;born=sourceStep/3d;variant=RNG.nextInt(3);Arrays.fill(states,state());
        }
        State state(){return new State(at,scale,rotation);}
        boolean update(){
            at=at.add(velocity);
            if(!noGravity)velocity=velocity.add(0,-.1/16,0);
            velocity=new Vec3(velocity.x*.99,velocity.y,velocity.z*.99);
            rotation+=velocity.dot(plane.forward())*8;scale-=.01;
            if(noGravity){velocity=velocity.scale(.92);scale-=.04;}
            return scale>=.1;
        }
    }
    public static void receive(CompoundTag data){EVENTS.add(data.copy());}
    private static Dust dust(int type,Vec3 center,int width,int inset,Vec3 initial,double scale,boolean noGravity,AttackFrame f){
        int area=Math.max(5,width-inset*2);
        Vec3 at=center.subtract(f.forward().add(f.right()).scale((width/2d-inset)/16));
        at=at.add(f.forward().scale((4+RNG.nextInt(Math.max(1,area-4)))/16d)).add(f.right().scale((4+RNG.nextInt(Math.max(1,area-4)))/16d));
        Vec3 jitter=f.forward().scale((RNG.nextInt(41)-20)*.1/16).add(f.right().scale((RNG.nextInt(41)-20)*.1/16));
        Dust d=new Dust(type,at,initial.add(jitter),scale*(1+(RNG.nextInt(41)-20)*.01),noGravity,f);LIVE.add(d);DISPLAY.add(d);return d;
    }
    private static void spread(Dust d){
        double z=SPATIAL.nextDouble()*2-1,a=SPATIAL.nextDouble()*Math.PI*2,r=Math.sqrt(1-z*z);
        d.velocity=new Vec3(r*Math.cos(a),z,r*Math.sin(a)).scale(d.velocity.length());Arrays.fill(d.states,d.state());
    }
    private static void emit(CompoundTag s){
        Kind k=Kind.values()[s.getInt("Kind")];Vec3 at=ElementsEntity.vector(s,"Position"),v=ElementsEntity.vector(s,"Velocity");
        AttackFrame f=new AttackFrame(ElementsEntity.vector(s,"F"),ElementsEntity.vector(s,"R"));boolean death=s.getString("Event").equals("old_one_death");
        if(k==Kind.ICE_BLADE){
            for(int i=0;i<(death?10:2);i++){
                Dust d=dust(92,at,10,0,death?Vec3.ZERO:v,death?1:1.2,!death,f);
                if(!death)d.velocity=d.velocity.scale(.3);
                else if(RNG.nextInt(3)!=0){d.velocity=d.velocity.scale(2);d.scale*=1.75;
                    d.noGravity=true;
                }else d.scale*=.5;
                spread(d);
            }
        }else if(k==Kind.WATER_BOLT){
            if(death){for(int i=0;i<30;i++){spread(dust(172,at,16,0,v.scale(.1),1,true,f));spread(dust(172,at,16,0,v.scale(.1),.5,false,f));}}
            else{
                for(int i=0;i<5;i++){Dust d=dust(172,at,16,4,Vec3.ZERO,1.2,true,f);d.velocity=d.velocity.scale(.1).add(v.scale(.1));d.at=d.at.subtract(v.scale(i/3d));spread(d);}
                if(RNG.nextInt(5)==0){Dust d=dust(172,at,16,4,Vec3.ZERO,.6,false,f);d.velocity=d.velocity.scale(.25).add(v.scale(.5));spread(d);}
            }
        }else if(k==Kind.ICHOR&&!death&&s.getInt("Updates")>4){
            for(int i=0;i<3;i++){Dust d=dust(170,at,32,14,Vec3.ZERO,1,true,f);d.velocity=d.velocity.scale(.1).add(v.scale(.5));d.at=d.at.subtract(v.scale(i/3d));spread(d);}
            if(RNG.nextInt(8)==0){Dust d=dust(170,at,32,16,Vec3.ZERO,.5,false,f);d.velocity=d.velocity.scale(.25).add(v.scale(.5));spread(d);}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(Minecraft.getInstance().level==null){LIVE.clear();DISPLAY.clear();EVENTS.clear();red=255;green=blue=style=0;return;}
        if(Minecraft.getInstance().isPaused())return;
        DISPLAY.clear();DISPLAY.addAll(LIVE);for(Dust d:LIVE){Arrays.fill(d.states,d.state());d.born=0;d.died=1;}
        // Consume each source frame separately; extraUpdates emit several times within that frame.
        for(int step=1;step<=3;step++){
            sourceStep=step-1;
            for(CompoundTag s:EVENTS)if(s.getInt("SourceStep")==sourceStep)emit(s);
            if(style==0&&(green+=7)>=255){green=255;style++;}if(style==1&&(red-=7)<=0){red=0;style++;}
            if(style==2&&(blue+=7)>=255){blue=255;style++;}if(style==3&&(green-=7)<=0){green=0;style++;}
            if(style==4&&(red+=7)>=255){red=255;style++;}if(style==5&&(blue-=7)<=0){blue=0;style=0;}
            for(var it=LIVE.iterator();it.hasNext();){Dust d=it.next();boolean alive=d.update();d.states[step]=d.state();if(!alive){d.died=step/3d;it.remove();}}
        }EVENTS.clear();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();double phase=e.getPartialTick()*3;int index=Math.min(2,(int)phase);double t=phase-index;
        for(Dust d:DISPLAY){if(e.getPartialTick()<d.born||e.getPartialTick()>d.died)continue;State a=d.states[index],b=d.states[index+1];if(a==null||b==null)continue;
            float rgb=d.type==92?1:d.type==170?200/255f:250/255f,alpha=d.type==92?0:d.type==170?100/255f:150/255f;
            double scale=a.scale+(b.scale-a.scale)*t;
            OldOneRenderer.region(e.getPoseStack(),buffers,e.getCamera().getPosition(),"old_one/dust",a.at.lerp(b.at,t),NativeSprites.billboard(),a.rotation+(b.rotation-a.rotation)*t,scale,scale,(d.type%100)*10,(d.type/100)*30+d.variant*10,8,8,new float[]{rgb,rgb,rgb,alpha});
        }buffers.endBatch();
    }
}
