package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.client.TrueBiomeAssets;
import net.everla.projectc.arkofcosmos.client.TrueBiomeDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity;
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
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

/** Calamity CritSpark/GenericSparkle/SmallSmoke/StoneDebris and source bursts, native rendering. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class TrueBiomeEffects {
    private TrueBiomeEffects(){}
    private static final List<Particle> LIVE=new ArrayList<>(),DISPLAY=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static final Map<UUID,Integer> SEEN=new HashMap<>();
    private static final Map<UUID,List<Particle>> SMEARS=new HashMap<>();
    private static final Random SPATIAL=new Random();
    private static Object world;private static int phase;
    private static final int CRIT=0,SPARKLE=1,BLOOM=2,SMOKE=3,STONE=4,DUST=5,RING=6,SNOWFLAKE=7,LINE=8,SMEAR=9;
    private record State(Vec3 at,double rotation,double scale,double opacity,int age){}
    private static final class Particle {
        Vec3 line=Vec3.ZERO;double sx=1,sy=1;UUID attack;
        int type,owner,age,life,variant;Vec3 at,velocity;double scale,rotation,spin,opacity=1,bloomScale=1,smokeOpacity,born,died=1.01;float[] color,bloom;AttackFrame f;
        final State[] states=new State[4];
        State state(){return new State(at,rotation,scale,opacity,age);}
        boolean update(){
            at=at.add(velocity);age++;double t=age/(double)life,sign=velocity.dot(f.forward())>0?1:-1;
            switch(type){
                case CRIT -> {opacity=Math.cos(t*Math.PI/2);velocity=velocity.scale(.8);rotation+=spin*sign*(t>.5?1:.5);}
                case SPARKLE,SNOWFLAKE -> {opacity=Math.sin(t*Math.PI);velocity=velocity.scale(.95);rotation+=spin*sign;}
                case BLOOM -> {opacity=Math.sin(t*Math.PI);velocity=velocity.scale(.95);}
                case SMOKE -> {rotation+=spin*sign;velocity=velocity.scale(.85);if(smokeOpacity>90){scale+=.01;smokeOpacity-=3;}else{scale*=.975;smokeOpacity-=2;}opacity=smokeOpacity/255;return smokeOpacity>0;}
                case STONE -> {opacity=t<.5?1:Math.sin(t*Math.PI);velocity=velocity.scale(.95).add(0,-.1/16,0);rotation+=spin*sign;}
                case DUST -> {velocity=velocity.multiply(.99,1,.99).scale(.92);scale-=.05;rotation+=velocity.dot(f.forward())*8;return scale>=.1;}
                case RING -> {opacity=Math.pow(Math.cos(t*Math.PI/2),2);scale=bloomScale*(1+Math.sin((40-age)/60d*Math.PI)*.2);velocity=velocity.scale(.95);}
            }
            return age<life;
        }
    }
    private static void reset(){Object now=Minecraft.getInstance().level;if(now!=world){world=now;LIVE.clear();DISPLAY.clear();EVENTS.clear();SEEN.clear();SMEARS.clear();}}
    public static void receive(CompoundTag s){reset();EVENTS.add(s.copy());}
    private static AttackFrame frame(CompoundTag s){return new AttackFrame(vector(s,"F"),vector(s,"R"));}
    private static Vec3 sphere(double pixels){double y=SPATIAL.nextDouble()*2-1,a=SPATIAL.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);return new Vec3(r*Math.cos(a),y,r*Math.sin(a)).scale(pixels/16);}
    private static Particle add(int type,CompoundTag s,Vec3 at,Vec3 velocity,float[] color,double scale,int life){
        Particle p=new Particle();p.type=type;p.owner=s.getInt("Owner");p.at=at;p.velocity=velocity;p.f=frame(s);p.color=color;p.bloom=color;p.scale=scale;p.life=life;p.rotation=random()*Math.PI*2;p.spin=.1;p.variant=rng.nextInt(type==STONE?3:3);p.born=phase/3d;Arrays.fill(p.states,p.state());LIVE.add(p);DISPLAY.add(p);return p;
    }
    private static void crit(CompoundTag s,Vec3 at,Vec3 velocity,float[] bloom,double scale,int life,double spin,double bloomScale){Particle p=add(CRIT,s,at,velocity,WHITE,scale,life);p.bloom=bloom;p.spin=spin;p.bloomScale=bloomScale;}
    private static void smoke(CompoundTag s,Vec3 at,Vec3 velocity,float[] c,double scale,int opacity){Particle p=add(SMOKE,s,at,velocity,c,scale,200);p.smokeOpacity=opacity;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;reset();var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        DISPLAY.clear();DISPLAY.addAll(LIVE);for(Particle p:LIVE){p.born=0;p.died=1.01;Arrays.fill(p.states,p.state());}
        for(phase=0;phase<3;phase++){
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof TrueBiomeEntity e){var s=e.view();var samples=s.getList("Samples",10);int count=samples.size()-1;
                for(int i=1+phase*Math.max(0,count)/3;i<=((phase+1)*Math.max(0,count)/3);i++){var sample=samples.getCompound(i);int age=sample.getInt("Age");if(age<=SEEN.getOrDefault(e.getUUID(),-1))continue;SEEN.put(e.getUUID(),age);continuous(sample);}
            }
            if(phase==2){for(var s:EVENTS)burst(s);EVENTS.clear();}
            for(var it=LIVE.iterator();it.hasNext();){Particle p=it.next();boolean alive=p.update();p.states[phase+1]=p.state();if(!alive){p.died=(phase+1)/3d;it.remove();}}
        }
        if(mc.level.getGameTime()%200==0){var ids=new HashSet<UUID>();for(var e:mc.level.entitiesForRendering())if(e instanceof TrueBiomeEntity)ids.add(e.getUUID());SEEN.keySet().retainAll(ids);SMEARS.keySet().retainAll(ids);}
    }
    private static void continuous(CompoundTag s){
        int kind=s.getInt("Kind"),mode=s.getInt("Mode"),age=s.getInt("Age");Vec3 at=vector(s,"Position"),center=vector(s,"Center"),dir=vector(s,"Direction");AttackFrame f=frame(s);double scale=s.getDouble("Scale");
        if(kind==TrueBiomeEntity.BEAM||kind==TrueBiomeEntity.SIGIL){Particle p=add(DUST,s,at.add(sphere(range(0,20))),sphere(range(0,2)).scale(.5).add(vector(s,"Velocity").scale(.1)),rgb(255,255,255),.9,100);p.variant=rng.nextInt(3);}
        if(kind==TrueBiomeEntity.BLADE&&mode==0&&s.getInt("Charge")>=450&&rng.nextBoolean()){
            Vec3 offset=f.direction(random()*Math.PI*2).scale(Math.sqrt(random())*scale*1.9*78/16);Vec3 velocity=f.normal().cross(offset).normalize().scale(2*(1+offset.length()*16/15)/16);
            float[] energy=mul(mix(rgb(255,105,180),rgb(173,255,47),Math.sin(Minecraft.getInstance().level.getGameTime()/10d)),(s.getInt("Charge")/600d-.75)/.25*.8);
            crit(s,center.add(offset),vector(s,"OwnerVelocity").add(velocity),energy,1+offset.length()*16/(scale*1.9*78),10,.05,3);
        }
        if(kind==TrueBiomeEntity.BLADE&&mode==3&&s.getInt("State")==1){double a=range(-Math.PI/4,Math.PI/4),factor=Math.sin(a*2+Math.PI/2);
            Vec3 v=dir.scale(30/16d).subtract(f.direction(f.angle(dir)+a).scale((1+factor)*2*range(7.5,20)/16));crit(s,at,v,rng.nextBoolean()?rgb(72,209,204):rgb(255,140,0),range(.1,1.6),20+rng.nextInt(30),1,3);
        }
        if(kind==TrueBiomeEntity.BLADE&&mode==1){
            UUID id=s.getUUID("Attack");List<Particle> smears=SMEARS.computeIfAbsent(id,key->new ArrayList<>());
            if(s.getInt("ChainTimer")%10==1){while(smears.size()<2){Particle p=add(SMEAR,s,center,Vec3.ZERO,mul(rgb(176,224,230),.5),scale*1.5,2);smears.add(p);}for(int i=0;i<2;i++){Particle p=smears.get(i);double a=s.getDouble("Whip"+i);p.at=center.add(f.direction(s.getDouble("Whip0")).scale(400*range(i==0?.7:.8,i==0?1.1:1.3)/16));p.rotation=a+Math.PI;p.sx=range(i==0?3:2,i==0?4:3);p.sy=range(i==0?.5:.9,i==0?1:1.4);}}
            for(Particle p:smears){if(!LIVE.contains(p)){LIVE.add(p);DISPLAY.add(p);}p.age=0;p.rotation+=Math.max(-.01,Math.min(.01,Math.atan2(Math.sin(-p.rotation),Math.cos(-p.rotation))));p.sx*=1.01;p.sy*=.985;}
        }
        if(kind==TrueBiomeEntity.BLADE&&mode==1&&s.getInt("ChainTimer")%10==1){var list=s.getList("Chains",10);for(int i=0;i<list.size();i++)if(rng.nextInt(3)==0){Particle p=add(SNOWFLAKE,s,vector(list.getCompound(i),"P4"),Vec3.ZERO,rgb(175,238,238),range(1,2),30);p.bloom=rgb(72,209,204);p.spin=.4;p.bloomScale=.2;}}
    }
    private static void burst(CompoundTag s){
        String event=s.getString("Fx");Vec3 at=vector(s,"At"),dir=vector(s,"Direction");AttackFrame f=frame(s);double scale=s.getDouble("Scale");
        if(event.equals("pride_direct")){Particle p=add(SPARKLE,s,at,vector(s,"OwnerVelocity"),WHITE,1.5,10);p.bloom=mul(mix(rgb(255,105,180),rgb(173,255,47),Math.sin(Minecraft.getInstance().level.getGameTime()/10d)),(s.getInt("Charge")/600d-.75)/.25*.8);p.spin=.1;p.bloomScale=3;}
        if(event.equals("pride_throw"))for(int color=0;color<2;color++)for(int i=0;i<9;i++){
            double a=range(-Math.PI/4,Math.PI/4),factor=Math.sin(a*2+Math.PI/2);crit(s,at,vector(s,"OwnerVelocity").add(f.direction(f.angle(dir)+a).scale((1+factor)*(color==0?2:1)*range(7.5,20)/16)),color==0?rgb(255,105,180):rgb(173,255,47),range(2,3.5),20+rng.nextInt(30),1,2);
        }
        if(event.equals("hook_start")||event.equals("hook_end")||event.equals("hook_death"))for(int i=0;i<(event.equals("hook_death")?10:4);i++)crit(s,at,sphere(Math.sqrt(random())*range(17.5,25)*scale),rng.nextBoolean()?CYAN:rgb(72,209,204),range(.4,event.equals("hook_death")?1.9:.9),20+rng.nextInt(30),1,3);
        if(event.equals("wheel_end"))for(int i=0;i<10;i++)crit(s,at,sphere(Math.sqrt(random())*range(17.5,25)*scale),rng.nextBoolean()?rgb(220,20,60):rgb(139,0,0),range(.4,3.9),20+rng.nextInt(30),1,3);
        if(event.equals("critical")){double a=random()*Math.PI*2;Vec3 v=f.forward().scale(Math.cos(a)*50/16).add(f.right().scale(Math.sin(a)*100/16));Particle line=add(LINE,s,at.subtract(v),Vec3.ZERO,mul(rgb(175,238,238),.6),.2,6);line.line=v.scale(2);for(int i=0;i<2;i++)crit(s,at,f.direction(f.angle(dir)+range(-Math.PI/2,Math.PI/2)).scale(9/16d),rgb(64,224,208),range(1,2),30,.4,1);}
        if(event.equals("dash_hit")){add(BLOOM,s,at,Vec3.ZERO,mul(rgb(220,20,60),.5),1,30);for(int i=0;i<3;i++)crit(s,at,f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(9/16d),rgb(220,20,60),range(1,2),30,.4,.6);}
        if(event.equals("pogo")){
            for(int i=0;i<8;i++){Vec3 offset=f.normal().cross(dir).scale(range(-10,10)/16),v=f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(-9/16d);smoke(s,at.add(offset),v,rgb(220,20,60),range(1.8,2.6),155-rng.nextInt(30));add(BLOOM,s,at.subtract(offset.scale(3)),dir.scale(-6*range(.5,1)/16),mul(rgb(220,20,60),.5),range(.01,.21),20+rng.nextInt(40));}
            for(int i=0;i<3;i++)add(STONE,s,at.add(f.normal().cross(dir).scale(range(-30,30)/16)),f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(-range(3,6)/16),rgb(245,245,220),range(1,1.4),30+rng.nextInt(50));
        }
        if(event.equals("blast_start")){Particle p=add(SPARKLE,s,at,Vec3.ZERO,WHITE,scale,20);p.bloom=rng.nextBoolean()?rgb(0,255,255):rgb(0,255,127);p.spin=.2;p.bloomScale=2;}
        if(event.equals("blast_burst")){
            Particle ring=add(RING,s,at,Vec3.ZERO,rgb(0,255,255),scale,40);ring.bloomScale=scale/(1+Math.sin(40/60d*Math.PI)*.2);add(BLOOM,s,at,Vec3.ZERO,mul(rng.nextBoolean()?rgb(0,255,255):rgb(0,255,127),.6),scale*range(1,2.5),20);
            for(int i=0;i<10;i++)crit(s,at,sphere(Math.sqrt(random())*range(17.5,25)*scale),rng.nextBoolean()?rgb(72,61,139):CHOCOLATE,range(.1,1.6),20+rng.nextInt(30),1,3);
            for(int i=0;i<20;i++){Vec3 radial=f.direction(i*Math.PI/10);crit(s,at.add(radial.scale(65*scale/16)),radial.scale(10/16d),rng.nextBoolean()?CYAN:rgb(0,255,127),range(.1,1.6),20+rng.nextInt(30),1,3);}
        }
        if(event.equals("monolith_start"))for(int i=0;i<20;i++)crit(s,at,sphere(Math.sqrt(random())*range(7.5,20)),rng.nextBoolean()?rgb(72,209,204):rgb(255,140,0),range(.1,1.6),20+rng.nextInt(30),1,3);
        if(event.equals("monolith_burst")){
            double size=s.getDouble("Size");for(int i=0;i<8;i++)smoke(s,at.add(f.right().scale(range(0,10)/16)),f.direction(f.angle(dir)+range(-Math.PI/2,Math.PI/2)).scale(range(5,15)/16),mix(rgb(255,140,0),rgb(72,209,204),random()),range(2.8,3.6)*size,165-rng.nextInt(30));
            for(int i=0;i<6;i++)add(STONE,s,at.add(f.right().scale(range(30,90)/16)),f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(range(3,6)/16),mix(rgb(72,61,139),rgb(119,136,153),random()),range(1,3.4)*size,30+rng.nextInt(50));
        }
        if(event.equals("beam_end"))for(int i=0;i<16;i++)add(DUST,s,at.add(dir.scale((-.5+i/15d)*88/16)),vector(s,"Velocity"),WHITE,2,100);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||Minecraft.getInstance().level==null)return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();double partial=event.getPartialTick();var face=NativeSprites.billboard();
        for(Particle p:DISPLAY){if(partial<p.born||partial>=p.died)continue;int i=Math.min(2,(int)(partial*3));double t=partial*3-i;State a=p.states[i],b=p.states[i+1];Vec3 at=a.at.lerp(b.at,t);double scale=lerp(a.scale,b.scale,t),r=lerp(a.rotation,b.rotation,t),alpha=lerp(a.opacity,b.opacity,t);
            var draw=new TrueBiomeDraw(new NativeSprites.Draw(event.getPoseStack(),buffers,event.getCamera().getPosition(),p.owner,true));float[] color=mul(p.color,alpha);
            if(p.type==CRIT||p.type==SPARKLE||p.type==SNOWFLAKE){String tex=TrueBiomeAssets.texture(p.type==CRIT?"thin_sparkle":p.type==SNOWFLAKE?"snowflake":"sparkle");double ratio=NativeSprites.height(tex)/(double)NativeSprites.height(TrueBiomeAssets.texture("bloom_circle"));draw.centered(TrueBiomeAssets.texture("bloom_circle"),at,face,0,scale*p.bloomScale*ratio,scale*p.bloomScale*ratio,mul(p.bloom,alpha*.5),true);if(p.type==SPARKLE)draw.centered(tex,at,face,r+Math.PI/4,scale*.75,scale*.75,mul(color,.5),true);if(p.type==SNOWFLAKE){for(int spoke=0;spoke<3;spoke++)draw.centered(tex,at,face,2*r+Math.PI*spoke/3,scale,scale,mul(color,.5),true);}else draw.centered(tex,at,face,r,scale,scale,color,true);}
            else if(p.type==LINE){Vec3 along=p.line.normalize(),side=p.f.normal().cross(along).normalize();var lf=new AttackFrame(side,along);String tex=TrueBiomeAssets.texture("thin_line");draw.sprite(tex,at,lf,Math.PI,scale,p.line.length()*16/NativeSprites.height(tex),NativeSprites.width(tex)/2d,NativeSprites.height(tex),color,true,false,15728880);}
            else if(p.type==SMEAR)draw.centered(TrueBiomeAssets.texture("semi_smear"),at,p.f,r,scale*p.sx,scale*p.sy,color,true);
            else if(p.type==STONE){String tex=TrueBiomeAssets.texture("stone");int w=NativeSprites.width(tex),h=NativeSprites.height(tex)/5;draw.region(tex,at,face,r,scale,w/2d,h/2d,color,0,p.variant*h,w,h,false,15728880);}
            else if(p.type==DUST)draw.region("elements/dust",at,face,r,scale,4,4,mul(WHITE,155/255d),75%100*10,p.variant*10+75/100*30,8,8,false,15728880);
            else{String tex=TrueBiomeAssets.texture(p.type==SMOKE?"smoke":p.type==RING?"bloom_ring":"bloom_circle");if(p.type==SMOKE)color=mul(mix(p.color,rgb(130,130,130),clamp((255-p.smokeOpacity-100)/80)),alpha);draw.centered(tex,at,face,r,scale,scale,color,p.type!=SMOKE);}
        }
        buffers.endBatch();
    }
}
