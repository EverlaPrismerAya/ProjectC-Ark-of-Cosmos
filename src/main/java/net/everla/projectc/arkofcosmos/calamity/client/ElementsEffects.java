package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkFxParticle.Type.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.*;

/** Source 60Hz particle emitters and sound events. */
public final class ElementsEffects {
    private static Object world;
    private static final List<ArkFxParticle> PARTICLES=new ArrayList<>(),FRAME=new ArrayList<>();
    private static final Map<Integer,Visual> VISUALS=new HashMap<>();
    private static final class Visual {int seen=-1,stitches;final Deque<CompoundTag> history=new ArrayDeque<>();}
    public static Iterable<CompoundTag> history(int id){Visual v=VISUALS.get(id);return v==null?List.of():v.history;}
    private static ArkFxParticle add(ArkFxParticle.Type type,Vec3 at,Vec3 velocity,float[] color,double scale,int life,AttackFrame f,int owner){ArkFxParticle p=new ArkFxParticle(type,at,velocity,color,scale,life,f);p.ownerId=owner;PARTICLES.add(p);return p;}
    private static ArkFxParticle spark(boolean crit,Vec3 at,Vec3 velocity,float[] bloom,double scale,int life,double spin,double bloomScale,double hue,AttackFrame f,int owner){ArkFxParticle p=add(crit?CRIT:SPARKLE,at,velocity,WHITE,scale,life,f,owner);p.bloom=bloom;p.spin=spin;p.bloomScale=bloomScale;p.hueShift=hue;return p;}
    private static void squish(Vec3 at,Vec3 velocity,float[] color,double scale,double strength,double hue,AttackFrame f,int owner){ArkFxParticle p=add(SQUISH,at,velocity,color,scale,60,f,owner);p.squish=strength;p.hueShift=hue;}
    private static void ring(Vec3 at,float[] color,double end,int life,AttackFrame f,int owner){ArkFxParticle p=add(RING,at,Vec3.ZERO,color,.05,life,f,owner);p.originalScale=.05;p.finalScale=end;}
    private static Vec3 radial(AttackFrame f,double speed){return f.direction(random()*Math.PI*2).scale(speed/16);}
    private static AttackFrame frame(CompoundTag s){Vec3 forward=vector(s,"F"),right=vector(s,"R");return forward.lengthSqr()<.5?new AttackFrame(new Vec3(0,0,1),new Vec3(-1,0,0)):new AttackFrame(forward,right);}
    private static void resetWorld(){Minecraft mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;PARTICLES.clear();FRAME.clear();VISUALS.clear();ElementsAudio.clear();ElementsVanillaParticles.clear();}}
    public static void receive(CompoundTag s){
        resetWorld();Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        String event=s.getString("Event");if(event.equals("elements_resonance")){ElementsVanillaParticles.resonance(s.getInt("Target"),mc.level.getGameTime());return;}if(event.equals("elements_sound")){ElementsAudio.receive(s);return;}
        Vec3 at=vector(s,"At"),v=vector(s,"Velocity");AttackFrame f=frame(s);int owner=s.getInt("Owner"),kind=s.getInt("Kind"),mode=s.getInt("Mode");
        if(event.equals("elements_hit")){
            if(kind==BLADE)for(int i=0;i<5;i++)squish(at,rotate(at.subtract(vector(s,"Position")).normalize(),range(-Math.PI/5,Math.PI/5),f).scale(range(3.6,8)/16),ORANGE_RED,range(.3,.6),2.5,.06,f,owner);
            else if(kind==NEEDLE||kind==PARRY){
                if(kind==PARRY)spark(false,at,Vec3.ZERO,rgb(255,105,180),1.2,35,.1,2,0,f,owner);
                for(int i=0;i<10;i++)squish(at,radial(f,range(2.6,4)),CYAN,range(.3,.6),1.5,.02,f,owner);
            }else if(kind==BLAST){
                float[][] colors={rgb(255,165,0),rgb(255,127,80),ORANGE_RED,rgb(255,215,0)};ring(at,colors[rng.nextInt(4)],.2+random(),30,f,owner);
                for(int i=0;i<10;i++)squish(at,rotate(f.forward(),range(-Math.PI/5,Math.PI/5),f).scale(range(2.6,4)/16),rgb(255,0,0),range(.3,.6),1.5,.002,f,owner);
            }
        }else if(event.equals("elements_snap")){
            spark(false,at,mc.level.getEntity(owner)==null?Vec3.ZERO:mc.level.getEntity(owner).getDeltaMovement().subtract(v.normalize().scale(1/16d)),ORANGE_RED,range(1,2),10+rng.nextInt(10),.1,3,0,f,owner);
            if(mc.player!=null&&mc.player.getId()==owner)shake=Math.max(shake,3);
        }else if(event.equals("elements_end")){
            if(kind==NEEDLE)for(int i=0;i<10;i++)squish(at.add(v.normalize().scale(40/16d)),radial(f,range(1.2,2.3)),CYAN,range(.3,.6),1.5,.02,f,owner);
            if(kind==STAR)ElementsVanillaParticles.death(s);
            if(kind==STAR)for(int i=0;i<2;i++)spark(true,at,radial(f,range(5.2,4.3)),CYAN,range(1.3,1.6),40,.1,3.5,.02,f,owner);
            if(kind==BLADE&&mode==3){ArkFxParticle p=add(LINE,at.subtract(vector(s,"Direction").scale(40/16d)),Vec3.ZERO,mul(rgb(255,165,0),.7),.2,10,f,owner);p.line=vector(s,"Direction").scale(80/16d);p.expansion=250;}
        }else if(event.equals("elements_parry")){
            var player=mc.level.getEntity(owner);for(int i=0;i<5;i++){
                double width=player==null?.6:player.getBbWidth(),height=player==null?1.8:player.getBbHeight();
                double a=random()*Math.PI*2,r=Math.sqrt(random()),size=range(.5,1.4);Vec3 offset=f.forward().scale(Math.cos(a)*r*width*2).add(f.right().scale(Math.sin(a)*r*height*1.2));
                ArkFxParticle p=add(FLARE,at.add(offset),offset.scale(.01),WHITE,1,20+rng.nextInt(6),f,owner);p.bloom=rgb(255,0,0);p.rotation=0;p.originalX=.6*size;p.originalY=size;p.finalX=1.5*size;p.finalY=2.7*size;p.bloomScale=3;p.delay=rng.nextInt(7)*2;
            }
        }else if(event.equals("elements_blast")){
            double length=ElementsRules.thrust(s.getInt("Age"))*242/16d;
            for(int i=0;i<20;i++){
                double offset=random()*length;Vec3 p=at.add(f.forward().scale(offset)).add(f.right().scale(-10/16d));float[] color=rng.nextBoolean()?ORANGE_RED:rng.nextBoolean()?WHITE:rgb(255,165,0);
                double size=range(.05,.4)*(.4+.6*Math.sin(offset/length*Math.PI));int type=rng.nextInt(3);
                if(type<2)add(type==0?BLOOM:LIGHT,p,f.right().scale(range(-4,-1)/16),color,size,10+rng.nextInt(20),f,owner);
                else spark(true,p,f.right().scale(range(-10,-1)/16),color,size*7,10+rng.nextInt(20),.1,3,0,f,owner);
            }
        }
    }
    private static Vec3 rotate(Vec3 v,double angle,AttackFrame f){Vec3 n=f.normal();return v.scale(Math.cos(angle)).add(n.cross(v).scale(Math.sin(angle))).add(n.scale(n.dot(v)*(1-Math.cos(angle))));}
    private static void emit(CompoundTag s,Visual visual){
        int kind=s.getInt("Kind"),age=s.getInt("Age"),owner=s.getInt("Owner");Vec3 p=vector(s,"Position"),v=vector(s,"Velocity");AttackFrame f=frame(s);
        if(kind==BLADE&&s.getInt("Mode")>=2){
            double r=s.getDouble("Rotation"),scale=s.getDouble("Scale");Vec3 at=p.add(f.direction(r).scale(90*scale/16)).add(f.direction(r-Math.PI/2).scale(20*scale/16));
            spark(true,at,f.direction(r).scale(7/16d),ORANGE_RED,range(1,2),10+rng.nextInt(10),.1,3,range(0,.01),f,owner);
            if(age==12&&!s.getBoolean("Missed")&&Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.getId()==owner)ring(p,ORANGE_RED,1.8,8,f,owner);
        }else if(kind==STAR&&age==0){
            ArkFxParticle a=add(FLARE,p,Vec3.ZERO,WHITE,1,20,f,owner);a.rotation=0;a.bloom=ORANGE_RED;a.originalX=.5;a.originalY=1;a.finalX=1.5;a.finalY=3;a.bloomScale=1;a.hueShift=.01;
            for(int i=0;i<2;i++)squish(p,rotate(v,range(-Math.PI/5,Math.PI/5),f).scale(.1),CYAN,range(.8,1.4),.3,.02,f,owner);
        }else if(kind==BLAST){
            int count=ElementsRules.stitches(age);double length=ElementsRules.thrust(age)*242/16;
            while(visual.stitches<count){int i=visual.stitches++;spark(true,p.add(f.forward().scale(length*(i+.5)/8)).add(f.right().scale(-10/16d)),Vec3.ZERO,CYAN,3,8,.1,3,0,f,owner);}
        }
    }
    public static void tick(){
        resetWorld();Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        ElementsVanillaParticles.beginFrame();
        FRAME.clear();FRAME.addAll(PARTICLES);for(ArkFxParticle p:FRAME)p.beginFrame();
        Set<Integer> live=new HashSet<>();List<ElementsEntity> entities=new ArrayList<>();for(var e:mc.level.entitiesForRendering())if(e instanceof ElementsEntity a){entities.add(a);live.add(a.getId());}
        for(int phase=1;phase<=3;phase++){
            ElementsVanillaParticles.status(phase);
            for(ElementsEntity e:entities){Visual visual=VISUALS.computeIfAbsent(e.getId(),k->new Visual());var list=e.effectSnapshot().getList("Samples",10);int updates=e.kind()==BLADE?6:3;
                for(int i=0;i<list.size();i++){CompoundTag s=list.getCompound(i);int age=s.getInt("Age");if(age<=visual.seen||i*3/Math.max(1,updates)!=phase-1)continue;
                    int before=PARTICLES.size();emit(s,visual);ElementsVanillaParticles.emit(s,phase);visual.history.addFirst(s.copy());while(visual.history.size()>16)visual.history.removeLast();visual.seen=age;
                    for(int j=before;j<PARTICLES.size();j++){ArkFxParticle p=PARTICLES.get(j);p.born=(phase-1)/3d;p.prepareFrame();FRAME.add(p);}
                }
            }
            ElementsVanillaParticles.update(phase);
            Iterator<ArkFxParticle> iterator=PARTICLES.iterator();while(iterator.hasNext()){ArkFxParticle p=iterator.next();p.update();p.capture(phase);if(p.time>=p.lifetime){p.died=phase/3d;iterator.remove();}}
        }
        VISUALS.keySet().removeIf(id->!live.contains(id));
    }
    public static void render(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 camera,float partial){
        var face=NativeSprites.billboard();
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=camera;
        try{for(ArkFxParticle p:FRAME){ArkFxDraw.ownerView(p.ownerId,Vec3.ZERO);p.draw(partial,face);}
            ElementsVanillaParticles.render(partial,face);
        }finally{ArkFxDraw.fadeEye=null;}
    }
}
