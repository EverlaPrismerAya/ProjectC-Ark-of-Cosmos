package net.everla.projectc.arkofcosmos.calamity.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.ViolenceEntity;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

/** Source Blood/Blood2/Impact/Spark particles on the shared three-step clock. */
public final class ViolenceEffects {
    private ViolenceEffects(){}
    private static final List<Particle> LIVE=new ArrayList<>(),FRAME=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static final Map<UUID,Integer> SEEN=new HashMap<>();
    private static Object world;
    private static int phase;
    private static final class Particle {
        int kind,time,life,owner;double scale,spin,rotation,born;Vec3 at,velocity;float[] color;
        final Vec3[] positions=new Vec3[4];final double[] scales=new double[4],rotations=new double[4];
        void capture(int i){positions[i]=at;scales[i]=scale;rotations[i]=rotation;}
        void update(){
            at=at.add(velocity);time++;
            if(kind==0){scale*=.98;velocity=velocity.multiply(.97,1,.97);velocity=new Vec3(velocity.x,net.minecraft.util.Mth.clamp(velocity.y-.9/16,-22/16d,22/16d),velocity.z);}
            if(kind==1)velocity=velocity.scale(.98);
            if(kind==2){scale*=.95;velocity=velocity.scale(.95);if(velocity.length()<12/16d)velocity=velocity.multiply(.94,1,.94).add(0,-.25/16,0);}
            if(kind==3)rotation+=spin;
        }
    }
    public static void beginFrame(){
        var level=Minecraft.getInstance().level;
        if(world!=level){world=level;LIVE.clear();FRAME.clear();EVENTS.clear();SEEN.clear();}
        FRAME.clear();FRAME.addAll(LIVE);for(var p:FRAME){p.born=0;for(int i=0;i<4;i++)p.capture(i);}
    }
    public static void receive(CompoundTag s){EVENTS.add(s.copy());}
    private static Particle add(int kind,CompoundTag s,Vec3 at,Vec3 velocity,double scale,int life,float[] color){
        Particle p=new Particle();p.kind=kind;p.at=at;p.velocity=velocity;p.scale=scale;p.life=life;p.color=color;p.owner=s.getInt("Owner");p.rotation=random()*Math.PI*2;p.born=(phase-1)/3d;
        for(int i=0;i<4;i++)p.capture(i);LIVE.add(p);FRAME.add(p);return p;
    }
    private static Vec3 spray(Vec3 direction,double spread,double speed,double up){
        Vec3 right=direction.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.001)right=new Vec3(1,0,0);
        Vec3 perpendicular=right.cross(direction).normalize();double angle=range(-spread,spread),around=random()*Math.PI*2;
        return direction.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)*Math.cos(around))).add(perpendicular.scale(Math.sin(angle)*Math.sin(around))).scale(speed/16).add(0,up/16,0);
    }
    private static void particles(CompoundTag s,boolean grinding){
        Vec3 at=grinding?vector(s,"Position").lerp(vector(s,"Target"),.5):vector(s,"At");
        Vec3 bloodAt=grinding?at:vector(s,"Target");
        Vec3 direction=grinding?vector(s,"Velocity").normalize().scale(-1):vector(s,"Position").subtract(bloodAt).normalize();boolean organic=s.getBoolean("Organic");
        boolean emit=grinding||!s.getBoolean("Javelin");int count=grinding?1:6;
        double damageRatio=clamp((s.getFloat("Dealt")*20-950)/1050);
        if(organic&&emit){
            for(int i=0;i<count;i++){
                double scale=range(.6,.8)*(rng.nextInt(20)==0?2:1);
                float[] color=mix(mix(rgb(255,0,0),rgb(139,0,0),random()),rgb(51,22,94),random()*.65);
                Vec3 offset=new Vec3(range(-1,1)*s.getFloat("TargetWidth"),range(-1,1)*s.getFloat("TargetHeight"),range(-1,1)*s.getFloat("TargetWidth")).scale(.04);
                add(0,s,bloodAt.add(offset),spray(direction,.81,range(11,23),12),scale,22+rng.nextInt(14),color);
            }
            for(int i=0;i<(grinding?1:3);i++)add(1,s,bloodAt,spray(direction,.9,range(9,14.5),0),range(.2,.33),20,mix(rgb(255,0,0),rgb(139,0,0),range(.5,1)));
        }else if(!organic&&emit&&(!grinding||s.getBoolean("FinalUpdate"))){
            for(int i=0;i<(grinding?2:6);i++){
                double scale=(range(.8,1)+(grinding?1:damageRatio)*.85)*(rng.nextInt(10)==0?2:1);
                add(2,s,at,spray(direction,.6,range(12,25),6),scale,22+rng.nextInt(14),mix(mix(rgb(192,192,192),rgb(255,215,0),random()*.7),rgb(255,165,0),random()));
            }
        }
        if(!grinding){
            double t=damageRatio;
            Particle p=add(3,s,at,Vec3.ZERO,lerp(.6,1,t),20,mix(rgb(192,192,192),rgb(255,215,0),random()*.5));
            p.spin=lerp(.08,.2,t)*(rng.nextBoolean()?1:-1)*range(.75,1.25);
        }
        if(!grinding||s.getBoolean("FinalUpdate")){
            var mc=Minecraft.getInstance();var sound=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,grinding?"violence_grind":"violence_impact"));
            if(sound!=null)mc.level.playLocalSound(at.x,at.y,at.z,sound,net.minecraft.sounds.SoundSource.PLAYERS,grinding?.2f:1,1,false);
            if(organic&&!grinding&&!s.getBoolean("Javelin")){var grind=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"violence_grind"));if(grind!=null)mc.level.playLocalSound(at.x,at.y,at.z,grind,net.minecraft.sounds.SoundSource.PLAYERS,1,1,false);}
        }
    }
    public static void tick(int step){
        phase=step;var mc=Minecraft.getInstance();if(mc.level==null)return;
        Set<UUID> live=new HashSet<>();
        for(var e:mc.level.entitiesForRendering())if(e instanceof ViolenceEntity attack){
            live.add(attack.getUUID());var s=attack.view();var samples=s.getList("Samples",10);int count=samples.size()-1;
            for(int i=1+(step-1)*Math.max(0,count)/3;i<=step*Math.max(0,count)/3;i++){
                var sample=samples.getCompound(i);int age=sample.getInt("Age");
                if(age<=SEEN.getOrDefault(attack.getUUID(),-1))continue;SEEN.put(attack.getUUID(),age);
                if(sample.getBoolean("Grinding"))particles(sample,true);
            }
        }
        if(step==3){
            for(var s:EVENTS){
                if(s.getString("Event").equals("violence_hit"))particles(s,false);
                else if(s.getString("Event").equals("violence_end")){
                    Vec3 at=vector(s,"At");for(int i=0;i<75;i++){double angle=Math.PI*2*i/75;mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,at.x,at.y,at.z,Math.cos(angle)*4/16,3/16d,Math.sin(angle)*4/16);}
                }
            }
            EVENTS.clear();SEEN.keySet().retainAll(live);
        }
        for(var it=LIVE.iterator();it.hasNext();){var p=it.next();p.update();p.capture(step);if(p.time>=p.life)it.remove();}
    }
    public static void render(float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 camera){
        var d=new NativeSprites.Draw(pose,buffers,camera,-1);var face=NativeSprites.billboard();
        for(var p:FRAME){
            if(partial<p.born)continue;double index=partial*3;int a=Math.min(2,(int)index),b=a+1;double t=index-a;
            Vec3 at=p.positions[a].lerp(p.positions[b],t);double scale=lerp(p.scales[a],p.scales[b],t),age=p.time-3+partial*3,completion=clamp(age/p.life);
            float[] color=mul(p.color,1-Math.pow(completion,p.kind==1?4:3));
            if(p.kind<2){var level=Minecraft.getInstance().level;int light=level.getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(at));color=mul(color,Math.pow(net.minecraft.client.renderer.LightTexture.getBrightness(level.dimensionType(),light),.15));}
            double rotation=p.kind==3?lerp(p.rotations[a],p.rotations[b],t):face.angle(p.velocity)+(p.kind==1?0:Math.PI/2);
            if(p.kind==0)d.centered("fx/violence_blood",at,face,rotation,scale*.1,scale*.1*(1+Math.min(1,Math.abs(p.velocity.y)*16/24)*.84),color,true);
            if(p.kind==1){int h=NativeSprites.height("fx/violence_blood2")/3,w=NativeSprites.width("fx/violence_blood2");d.region("fx/violence_blood2",at,face,rotation,scale,w/2d,h/2d,color,0,Math.min(2,(int)(completion*3))*h,w,h,true,15728880);}
            if(p.kind==2){d.centered("fx/calamitas_spark",at,face,rotation,scale*.5,scale*1.6,color,true);d.centered("fx/calamitas_spark",at,face,rotation,scale*.5*.45,scale*1.6,color,true);}
            if(p.kind==3){double pulse=(1-Math.abs(completion*2-1))*1.3*scale;for(double r:new double[]{0,rotation,-rotation})d.centered("fx/calamitas_spark",at,face,r,pulse*.3,pulse,p.color,true);}
        }
    }
}
