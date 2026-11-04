package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.BiomeEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.*;

/** Original Calamity particle textures and update curves, rendered through native Minecraft materials. */
public final class BiomeEffects {
    private BiomeEffects(){}
    private static final Random RNG=new Random();
    private static final List<Particle> LIVE=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static final Map<UUID,Integer> SEEN=new HashMap<>();
    private static Object world;
    private static final class Particle {
        String type;Vec3 at,previous,velocity;AttackFrame frame;float[] color;Vec3 gravity=Vec3.ZERO;double scale,rotation,opacity=1,sourceOpacity,spin=.1;int age,life,owner,variant;
        boolean update(){
            previous=at;at=at.add(velocity);age++;double t=age/(double)life;
            switch(type){
                case "spark"->{opacity=Math.cos(t*Math.PI/2);velocity=velocity.scale(.8);rotation+=.1*(t>.5?1:.5);}
                case "sparkle","snow","bloom"->{opacity=Math.sin(t*Math.PI);velocity=velocity.scale(.95);rotation+=.1;}
                case "smoke","mist"->{velocity=velocity.scale(.85);rotation+=spin*Math.signum(velocity.dot(frame.right()));if(sourceOpacity>90){scale+=.01;sourceOpacity-=3;}else{scale*=.975;sourceOpacity-=2;}opacity=Math.max(0,sourceOpacity/255);float[] fade=rgb(type.equals("mist")?0xADD8E6:0x2F4F4F);double blend=Math.max(0,Math.min(1,(255-sourceOpacity-100)/80));for(int k=0;k<3;k++)color[k]+=(fade[k]-color[k])*blend;if(sourceOpacity<0)return false;}
                case "stone"->{velocity=velocity.scale(.95).add(0,-.1/16d,0);opacity=t<.5?1:Math.sin(t*Math.PI);rotation+=.1;}
                case "dust75"->{velocity=velocity.multiply(.99,1,.99).scale(.92);scale-=.05;rotation+=velocity.dot(frame.forward())*8;if(scale<.1)return false;}
                case "water"->{velocity=velocity.scale(.85);scale*=.975;opacity*=.985;rotation+=spin*Math.signum(velocity.dot(frame.right()));}
                case "dust","dust_big"->{velocity=velocity.add(gravity).scale(.95);opacity=Math.sin(t*Math.PI);scale*=.98;rotation+=spin*Math.signum(velocity.dot(frame.right()));}
                case "dust33"->{velocity=velocity.add(0,-.1/16,0).scale(.99);scale*=.99;opacity=Math.min(1,(life-age)/20d);}
                case "dust16"->{velocity=velocity.scale(.95);scale-=.01;opacity=Math.min(1,(life-age)/20d);}
            }return age<life;
        }
    }
    private static void reset(){Object now=Minecraft.getInstance().level;if(now!=world){world=now;LIVE.clear();EVENTS.clear();SEEN.clear();}}
    public static void receive(CompoundTag s){reset();EVENTS.add(s.copy());}
    private static double range(double a,double b){return a+(b-a)*RNG.nextDouble();}
    private static float[] rgb(int color){return new float[]{(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1};}
    private static AttackFrame frame(CompoundTag s){return new AttackFrame(vector(s,"F"),vector(s,"R"));}
    private static Vec3 circle(AttackFrame f,double pixels){return f.direction(range(0,Math.PI*2)).scale(Math.sqrt(RNG.nextDouble())*pixels/16);}
    private static void add(String type,CompoundTag s,Vec3 at,Vec3 v,int color,double scale,int life){
        Particle p=new Particle();p.type=type;p.owner=s.getInt("Owner");p.at=p.previous=at;p.velocity=v;p.frame=frame(s);p.color=rgb(color);p.scale=scale;p.life=life;p.variant=RNG.nextInt(3);p.rotation=range(0,Math.PI*2);if(type.equals("water"))p.opacity=.5;if(type.equals("mist")||type.equals("smoke")){p.sourceOpacity=life;p.life=180;}if(type.equals("dust")||type.equals("dust_big"))p.gravity=vector(s,"Velocity").scale(-.1).add(0,-.1/16,0);LIVE.add(p);
    }
    public static void tick(){
        reset();var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof BiomeEntity b)sample(b,b.view());
        for(var event:EVENTS)burst(event);EVENTS.clear();
        for(int i=0;i<3;i++)LIVE.removeIf(p->!p.update());
        if(mc.level.getGameTime()%200==0){Set<UUID> ids=new HashSet<>();for(var e:mc.level.entitiesForRendering())if(e instanceof BiomeEntity)ids.add(e.getUUID());SEEN.keySet().retainAll(ids);}
    }
    public static void sample(BiomeEntity entity,CompoundTag state){
        if(state.isEmpty())return;int last=SEEN.getOrDefault(entity.getUUID(),0);ListTag samples=state.getList("Samples",10);if(samples.isEmpty()){if(state.getInt("Age")>last)emit(state);}else for(int i=0;i<samples.size();i++){CompoundTag s=samples.getCompound(i);if(s.getInt("Age")>last)emit(s);}SEEN.put(entity.getUUID(),state.getInt("Age"));
    }
    private static void emit(CompoundTag s){
        int kind=s.getInt("Kind"),mode=s.getInt("Mode");Vec3 at=vector(s,"Position"),v=vector(s,"Velocity"),center=vector(s,"Center"),dir=vector(s,"Direction");AttackFrame f=frame(s);
        if(kind==BiomeEntity.BEAM||kind==BiomeEntity.SIGIL)add("dust75",s,at.add(circle(f,16)),v.scale(.1).add(circle(f,1)),0xBFFF3D,.9,60);

        if(kind==BiomeEntity.MIST&&s.getInt("Alpha")<=140&&RNG.nextInt(15)==0)add("snow",s,at.add(circle(f,17*s.getDouble("Scale"))),Vec3.ZERO,0x4BB1FA,range(.3,1.5),40);
        if(kind==BiomeEntity.BLADE&&mode==ASTRAL&&s.getInt("State")==1){double a=range(-Math.PI/4,Math.PI/4),strength=(1+Math.sin(a*2+Math.PI/2))*2*range(7.5,20);Vec3 velocity=vector(s,"OwnerVelocity").subtract(f.direction(a).scale(strength/16));add("spark",s,at,velocity,RNG.nextBoolean()?0x48D1CC:0xFF8C00,range(.1,1.6),20+RNG.nextInt(30));}
        if(kind==BiomeEntity.ORB){
            Vec3 side=new Vec3(dir.x,0,dir.z).normalize();
            if(s.getBoolean("Water")){
                for(int i=0;i<5;i++)add("dust33",s,at.add(circle(f,10)),side.scale(-3.7/16d).add(0,6/16d,0),0xFFFFFF,range(1,3),90);
                add("dust16",s,at.add(circle(f,10)),side.scale(2.7/16d).add(0,3/16d,0),0xFFFFFF,1.4,90);
                if(RNG.nextBoolean()){double a=range(-Math.PI/2,Math.PI/2);add("spark",s,at.add(f.direction(f.angle(v)+a).scale((10+50*Math.abs(a)/(Math.PI/2)*s.getDouble("Scale"))/16)),v.scale(-1),0x87CEEB,range(1,2),30);}
            }else if(s.getInt("LandTime")>10){
                for(int i=0;i<3;i++)add("dust33",s,at.add(circle(f,i==0?0:16)),side.scale(-3.7/16d).add(0,6/16d,0),0xFFFFFF,range(1,3),90);
                add("dust16",s,at.add(side),side.scale(2.7/16d).add(0,3/16d,0),0xFFFFFF,1.4,90);
                Vec3 top=at.add(circle(f,6.5)).add(0,BiomeEntity.waveY(s.getInt("LandTime"),s.getInt("Left"))*100/16d,0);
                add("dust_big",s,top,v.scale(.5),0x778899,range(1,2),25);
                if(RNG.nextInt(5)==0)add("mist",s,top,v.scale(.5),0x778899,range(1,2),245-RNG.nextInt(50));
            }else{add("dust",s,at.add(circle(f,13)),v.scale(.5),0x778899,range(.2,2),25);if(RNG.nextBoolean())add("water",s,at.add(circle(f,13)),v.scale(.5),0xFFFFFF,range(.2,2),50);}
        }
    }
    private static void burst(CompoundTag s){
        String event=s.getString("Fx");Vec3 at=vector(s,"At"),dir=vector(s,"Direction"),ownerV=vector(s,"OwnerVelocity");AttackFrame f=frame(s);
        switch(event){
            case "cold_snow"->{add("snow",s,at,dir.scale(3/16d),0x4BB1FA,range(.3,1.5),40);}
            case "orb_end"->{for(int i=0;i<4;i++)add("mist",s,at.add(circle(f,26)),vector(s,"Velocity").scale(.5),0x778899,range(1,2),245-RNG.nextInt(50));}
            case "attuned"->{int color=COLORS[Math.max(0,s.getInt("Mode"))];for(int i=0;i<6;i++)add("bloom",s,at.add(f.right().scale(range(-20,20)/16)),new Vec3(0,range(1,5)/16,0),color,range(.02,.22),20+RNG.nextInt(30));for(int i=0;i<11;i++)add("sparkle",s,at.add(f.right().scale(range(-16,16)/16)),new Vec3(0,range(1,5)/16,0),color,range(.3,.7),20+RNG.nextInt(30));}
            case "holy_throw"->{for(int color:new int[]{0xFF69B4,0x00FFFF})for(int i=0;i<9;i++){double a=range(-Math.PI/4,Math.PI/4),m=(1+Math.sin(a*2+Math.PI/2))*(color==0xFF69B4?2:1)*range(7.5,20);add("spark",s,at,ownerV.add(f.direction(a).scale(m/16)),color,range(2,3.5),20+RNG.nextInt(30));}}
            case "beam_end"->{for(int i=0;i<16;i++)add("dust75",s,at.add(dir.scale((-.5+i/15d)*88/16)),vector(s,"Velocity"),0xBFFF3D,2,60);}
            case "pogo"->{for(int i=0;i<8;i++){Vec3 offset=f.right().scale(range(-10,10)/16);Vec3 back=f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(-1);add("smoke",s,at.add(offset),back.scale(9/16d),0xFF4500,range(1.8,2.6),115-RNG.nextInt(30));add("bloom",s,at.subtract(offset.scale(3)),dir.scale(-6/16d*range(.5,1)),0xFFA500,range(.01,.21),20+RNG.nextInt(40));}for(int i=0;i<3;i++)add("stone",s,at.add(circle(f,30)),dir.scale(-range(3,6)/16),0xF5F5DC,range(1,1.4),30+RNG.nextInt(50));}
            case "hot_end"->{for(int i=0;i<4;i++)for(int j=0;j<4;j++)add("sparkle",s,at.add(dir.scale((60+j*50)/16d)),vector(s,"Velocity").scale(range(.9,1.1)),0xFF4500,range(.3,.7),20+RNG.nextInt(30));}
            case "monolith_start"->{for(int i=0;i<20;i++)add("spark",s,at,circle(f,1).scale(range(7.5,20)),RNG.nextBoolean()?0x48D1CC:0xFF8C00,range(.1,1.6),20+RNG.nextInt(30));}
            case "monolith_burst"->{double size=s.getDouble("Size");for(int i=0;i<8;i++)add("smoke",s,at.add(f.right().scale(range(0,10)/16)),f.direction(range(-Math.PI/2,Math.PI/2)).scale(range(5,15)/16),RNG.nextBoolean()?0x48D1CC:0xFF8C00,range(2.8,3.6)*size,165-RNG.nextInt(30));for(int i=0;i<6;i++)add("stone",s,at.add(f.right().scale(range(30,90)/16)),f.direction(range(-Math.PI/4,Math.PI/4)).scale(range(3,6)/16),0x778899,range(1,3.4)*size,30+RNG.nextInt(50));}
            case "critical"->{for(int i=0;i<4;i++)add("spark",s,at,f.direction(range(-Math.PI/2,Math.PI/2)).scale(9/16d),0x32CD32,range(1,2),30);}
        }
    }
    public static void render(PoseStack pose,MultiBufferSource buffers,Vec3 camera,float partial){
        for(Particle p:LIVE){var d=new NativeSprites.Draw(pose,buffers,camera,p.owner,true);float[] c=p.color.clone();c[3]*=(float)p.opacity;Vec3 at=p.previous.lerp(p.at,partial);AttackFrame f=NativeSprites.billboard();
            if(p.type.equals("dust33")||p.type.equals("dust16")){int type=p.type.equals("dust33")?33:16;d.region("elements/dust",at,f,p.rotation,p.scale,4,4,c,type*10,p.variant*10,8,8,false,15728880);continue;}
            if(p.type.equals("dust75")){d.region("elements/dust",at,f,p.rotation,p.scale,4,4,new float[]{1,1,1,155/255f},750,p.variant*10,8,8,false,15728880);continue;}
            String texture=BiomeAssets.texture(p.type.equals("spark")?"thin_spark":""+p.type);int w=NativeSprites.width(texture),h=NativeSprites.height(texture);boolean atlas=p.type.equals("stone")||p.type.equals("mist")||p.type.equals("dust")||p.type.equals("dust_big");if(atlas){h/=p.type.equals("stone")?5:3;d.region(texture,at,f,p.rotation,p.scale,w/2d,h/2d,c,0,p.variant*h,w,h,!p.type.equals("stone"),15728880);}else if(p.type.equals("snow")){for(int i=0;i<3;i++)d.sprite(texture,at,f,p.rotation+i*Math.PI*2/3,p.scale,p.scale,0,h/2d,c,true,false,15728880);}else {if(p.type.equals("spark")){float[] bloom=c.clone();bloom[3]*=.5f;double bs=p.scale*.6*h/NativeSprites.height(BiomeAssets.texture("bloom"));d.centered(BiomeAssets.texture("bloom"),at,f,0,bs,bs,bloom,true);}d.centered(texture,at,f,p.rotation,p.scale,p.scale,c,!p.type.equals("smoke"));}
        }
    }
}
