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

/** Original Calamity particle textures and update curves, rendered through the shared world/depth FX pipeline. */
public final class BiomeEffects {
    private BiomeEffects(){}
    private static final Random RNG=new Random();
    private static final List<Particle> LIVE=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static final Map<UUID,Integer> SEEN=new HashMap<>();
    private static Object world;
    private static final List<Particle> DISPLAY=new ArrayList<>();
    private static int phase;
    private record State(Vec3 at,double rotation,double scale,double opacity,double sourceOpacity,int age){}
    private static final class Particle {
        String type;Vec3 at,previous,velocity;AttackFrame frame;float[] color,bloom,fade;
        Vec3 gravity=Vec3.ZERO;double scale,rotation,opacity=1,sourceOpacity,spin,bloomScale=1,born,died=1.01,fadeIn;
        int age,life,owner,variant,dustAlpha;boolean noGravity;
        final State[] states=new State[4];
        State state(){return new State(at,rotation,scale,opacity,sourceOpacity,age);}
        boolean update(){
            previous=at;at=at.add(velocity);age++;double t=age/(double)life,sign=velocity.dot(frame.forward())>0?1:-1;
            switch(type){
                case "spark"->{opacity=Math.cos(t*Math.PI/2);velocity=velocity.scale(.8);rotation+=spin*sign*(t>.5?1:.5);}
                case "sparkle","snow"->{opacity=Math.sin(t*Math.PI);velocity=velocity.scale(.95);rotation+=spin*sign;}
                case "bloom","generic_bloom"->{opacity=Math.sin(t*Math.PI);velocity=velocity.scale(.95);}
                case "smoke","mist"->{rotation+=spin*sign;velocity=velocity.scale(.85);if(sourceOpacity>90){scale+=.01;sourceOpacity-=3;}else{scale*=.975;sourceOpacity-=2;}opacity=Math.max(0,sourceOpacity/255);return sourceOpacity>=0;}
                case "stone"->{velocity=velocity.scale(.95).add(0,-.1/16d,0);opacity=t<.5?1:Math.sin(t*Math.PI);rotation+=spin*(velocity.dot(frame.forward())>0?1:-1);}
                case "dust137"->{if(!noGravity)velocity=velocity.add(0,-.1/16d,0);velocity=velocity.multiply(.99,1,.99);rotation+=velocity.dot(frame.forward())*8;scale-=.01;if(noGravity){velocity=velocity.scale(.92);scale-=.04;}return scale>=.1;}
                case "dust75"->{velocity=velocity.multiply(.99,1,.99).scale(.92);scale-=.05;rotation+=velocity.dot(frame.forward())*8;return scale>=.1;}
                case "water"->{opacity*=.985;rotation+=spin*sign;velocity=velocity.scale(.85);scale*=.975;}
                case "dust","dust_big"->{velocity=velocity.add(gravity).scale(.95);opacity=Math.sin(t*Math.PI);rotation+=spin*(velocity.dot(frame.forward())>0?1:-1);scale*=.98;}
                case "dust33"->{
                    velocity=velocity.add(0,-.1/16,0);dustAlpha+=2;scale-=.005;
                    var level=Minecraft.getInstance().level;
                    if(level!=null&&!level.getFluidState(net.minecraft.core.BlockPos.containing(at)).isEmpty()){dustAlpha+=20;scale-=.1;}
                    if(velocity.y<-.25)velocity=new Vec3(velocity.x,-.25,velocity.z);
                    velocity=velocity.multiply(.99,1,.99);rotation+=velocity.dot(frame.forward())*8;
                    if(fadeIn>0){scale+=.03;if(scale>fadeIn)fadeIn=0;}else scale-=.01;
                    opacity=(255-dustAlpha)/255d;return dustAlpha<=255&&scale>=.1;
                }
                case "dust16"->{velocity=velocity.scale(.98).multiply(.99,1,.99);rotation+=velocity.dot(frame.forward())*8;scale-=.01;velocity=velocity.scale(.92);scale-=.04;return scale>=.1;}
            }return age<life;
        }
    }
    private static void reset(){Object now=Minecraft.getInstance().level;if(now!=world){world=now;LIVE.clear();DISPLAY.clear();EVENTS.clear();SEEN.clear();}}
    public static void receive(CompoundTag s){reset();EVENTS.add(s.copy());}
    private static double range(double a,double b){return a+(b-a)*RNG.nextDouble();}
    private static float[] rgb(int color){return new float[]{(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1};}
    private static float[] mul(float[] c,double m){float[] r=c.clone();for(int i=0;i<4;i++)r[i]*=(float)m;return r;}
    private static float[] mix(float[] a,float[] b,double t){t=clamp(t);float[] r=a.clone();for(int i=0;i<4;i++)r[i]+=(b[i]-a[i])*t;return r;}
    private static AttackFrame frame(CompoundTag s){return new AttackFrame(vector(s,"F"),vector(s,"R"));}
    private static Vec3 circle(AttackFrame f,double pixels){return f.direction(range(0,Math.PI*2)).scale(Math.sqrt(RNG.nextDouble())*pixels/16);}
    // Rotate source scatter around its emission axis. Lengths and source cone angles are unchanged.
    private static Vec3 spatial(Vec3 v,Vec3 axis){
        axis=axis.normalize();if(axis.lengthSqr()<.5)return v;double a=range(0,Math.PI*2);
        return v.scale(Math.cos(a)).add(axis.cross(v).scale(Math.sin(a))).add(axis.scale(axis.dot(v)*(1-Math.cos(a))));
    }
    private static Particle add(String type,CompoundTag s,Vec3 at,Vec3 v,int color,double scale,int life){
        Particle p=new Particle();p.type=type;p.owner=s.getInt("Owner");p.at=p.previous=at;p.velocity=v;p.frame=frame(s);p.color=rgb(color);p.bloom=p.color;p.fade=rgb(type.equals("mist")?0xADD8E6:0x828282);p.scale=scale;p.life=life;p.variant=RNG.nextInt(3);p.rotation=range(0,Math.PI*2);
        p.born=phase/3d;
        if(type.equals("spark")){p.color=rgb(0xFFFFFF);p.spin=1;p.bloomScale=3;if(s.getString("Fx").equals("critical")||type.equals("spark")&&s.getInt("Kind")==BiomeEntity.ORB){p.spin=.4;p.bloomScale=.6;}}
        if(type.equals("sparkle")){p.spin=1;p.bloomScale=2;}
        if(type.equals("snow")){p.color=rgb(0xFFFFFF);p.spin=.5;}
        if(type.equals("water")){p.opacity=.5;p.spin=.1;}
        if(type.equals("mist")||type.equals("smoke")){p.sourceOpacity=life;p.life=200;p.spin=type.equals("mist")?.1:0;}
        if(type.equals("stone")){p.spin=.1;}
        if(type.equals("smoke")&&s.getString("Fx").equals("monolith_burst"))p.spin=.1;
        if(type.equals("smoke")||type.equals("stone")){p.velocity=spatial(v,vector(s,"Direction"));if(s.contains("At")){Vec3 origin=vector(s,"At");p.at=p.previous=origin.add(spatial(at.subtract(origin),vector(s,"Direction")));}}
        if(type.equals("dust")||type.equals("dust_big")){p.spin=.1;p.gravity=vector(s,"Velocity").scale(-.1).add(0,-.1/16,0);}
        if(type.equals("dust75")||type.equals("dust33")||type.equals("dust16")){
            p.rotation=0;p.scale*=1+RNG.nextInt(-20,21)*.01;
            if(type.equals("dust75")){p.scale*=.7;p.dustAlpha=100;}
            else{
                Vec3 random=p.frame.forward().scale(RNG.nextInt(-20,21)*.1/16).add(0,RNG.nextInt(-20,21)*.1/16,0);
                p.velocity=v.add(random);p.at=p.previous=at.add(p.frame.forward().scale((RNG.nextInt(type.equals("dust33")?19:5)+4)/16d)).add(0,-.25,0);
                if(type.equals("dust33")){p.velocity=p.velocity.scale(.5).add(0,-1/16d,0);p.dustAlpha=170;p.fadeIn=3;}
            }
        }
        Arrays.fill(p.states,p.state());LIVE.add(p);DISPLAY.add(p);return p;
    }
    public static void tick(){
        reset();var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        DISPLAY.clear();DISPLAY.addAll(LIVE);for(Particle p:LIVE){p.born=0;p.died=1.01;Arrays.fill(p.states,p.state());}
        for(phase=0;phase<3;phase++){
            frozenPlayerDust();
            for(var entity:mc.level.entitiesForRendering()){
                if(entity instanceof BiomeEntity b)sample(b,b.view());
                else if(entity instanceof net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity b)sampleBroken(b);
            }
            if(phase==2){for(var event:EVENTS)burst(event);EVENTS.clear();}
            for(var it=LIVE.iterator();it.hasNext();){Particle p=it.next();boolean alive=p.update();p.states[phase+1]=p.state();if(!alive){p.died=(phase+1)/3d;it.remove();}}
        }
        if(mc.level.getGameTime()%200==0){Set<UUID> ids=new HashSet<>();for(var e:mc.level.entitiesForRendering())if(e instanceof BiomeEntity||e instanceof net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity)ids.add(e.getUUID());SEEN.keySet().retainAll(ids);}
    }
    // GlacialState.DrawEffects: one 1/4 roll per 60 Hz source frame, original Dust 137.
    private static void frozenPlayerDust(){
        var mc=Minecraft.getInstance();
        for(var player:mc.level.players())if(player.hasEffect(net.everla.projectc.arkofcosmos.ArkOfCosmos.BROKEN_BIOME_FREEZE.get())&&RNG.nextInt(4)==0){
            Vec3 horizontal=new Vec3(-Math.cos(Math.toRadians(player.getYRot())),0,-Math.sin(Math.toRadians(player.getYRot())));
            CompoundTag s=new CompoundTag();s.putInt("Owner",player.getId());vector(s,"F",horizontal);vector(s,"R",new Vec3(0,1,0));
            Vec3 at=player.getBoundingBox().getCenter().add(horizontal.scale((RNG.nextInt(16)-6)/16d)).add(0,-(RNG.nextInt(16)-6)/16d,0);
            Vec3 velocity=horizontal.scale(RNG.nextInt(-20,21)*.02/16).add(0,-RNG.nextInt(-20,21)*.02/16,0);
            Particle p=add("dust137",s,at,velocity,0xFFFFFF,1,100);p.noGravity=RNG.nextBoolean();p.rotation=0;
            p.velocity=p.velocity.add(0,range(.4,.6)/16,0);p.scale=range(.4,.7);Arrays.fill(p.states,p.state());
        }
    }
    private static void sampleBroken(net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity entity){
        CompoundTag state=entity.view();int last=SEEN.getOrDefault(entity.getUUID(),0);ListTag samples=state.getList("Samples",10);
        for(int i=phase*samples.size()/3;i<(phase+1)*samples.size()/3;i++){
            CompoundTag sample=samples.getCompound(i);
            if(sample.getInt("Age")>last){if(sample.getInt("Kind")==1)emit(sample);last=sample.getInt("Age");}
        }
        SEEN.put(entity.getUUID(),last);
    }
    private static void sample(BiomeEntity entity,CompoundTag state){
        if(state.isEmpty())return;int last=SEEN.getOrDefault(entity.getUUID(),0);ListTag samples=state.getList("Samples",10);
        int start=phase*samples.size()/3,end=(phase+1)*samples.size()/3;
        if(samples.isEmpty()){if(phase==2&&state.getInt("Age")>last){emit(state);last=state.getInt("Age");}}
        else for(int i=start;i<end;i++){CompoundTag s=samples.getCompound(i);if(s.getInt("Age")>last){emit(s);last=s.getInt("Age");}}
        SEEN.put(entity.getUUID(),last);
    }
    private static void emit(CompoundTag s){
        int kind=s.getInt("Kind"),mode=s.getInt("Mode");Vec3 at=vector(s,"Position"),v=vector(s,"Velocity"),center=vector(s,"Center"),dir=vector(s,"Direction");AttackFrame f=frame(s);
        if(kind==BiomeEntity.BEAM||kind==BiomeEntity.SIGIL){
            Vec3 randomVelocity=f.forward().scale(RNG.nextInt(-20,21)*.015/16).add(f.right().scale(RNG.nextInt(-10,6)*.05/16));
            Vec3 offset=f.forward().scale((RNG.nextInt(28)-12)/16d).add(f.right().scale((RNG.nextInt(28)-12)/16d));
            add("dust75",s,at.add(spatial(offset,f.forward())),randomVelocity.add(v.scale(kind==BiomeEntity.BEAM?.1:0)),0xFFFFFF,.9,100);
        }

        if(kind==BiomeEntity.MIST&&s.getInt("Alpha")<=140&&RNG.nextInt(15)==0)add("snow",s,at.add(circle(f,17*s.getDouble("Scale"))),Vec3.ZERO,0x4BB1FA,range(.3,1.5),40);
        if(kind==BiomeEntity.BLADE&&mode==ASTRAL&&s.getInt("State")==1){double a=range(-Math.PI/4,Math.PI/4),strength=(1+Math.sin(a*2+Math.PI/2))*2*range(7.5,20);Vec3 velocity=vector(s,"OwnerVelocity").subtract(f.direction(f.angle(dir)+a).scale(strength/16));add("spark",s,at,velocity,RNG.nextBoolean()?0x48D1CC:0xFF8C00,range(.1,1.6),20+RNG.nextInt(30));}
        if(kind==BiomeEntity.ORB){
            Vec3 side=new Vec3(dir.x,0,dir.z).normalize();
            if(s.getBoolean("Water")){
                for(int i=0;i<5;i++)add("dust33",s,at.add(circle(f,10)),side.scale(-3.7/16d).add(0,6/16d,0),0xFFFFFF,range(1,3),90);
                add("dust16",s,at.add(circle(f,10)),side.scale(2.7/16d).add(0,3/16d,0),0xFFFFFF,1.4,90);
                if(RNG.nextBoolean()){double a=range(-Math.PI/2,Math.PI/2);add("spark",s,at.add(f.direction(f.angle(v)+a).scale((10+50*Math.abs(a)/(Math.PI/2)*s.getDouble("Scale"))/16)),v.scale(-1),0x6495ED,range(1,2),30).color=rgb(0x87CEEB);}
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
            case "cold_mist"->{Vec3 point=at.add(spatial(circle(f,30),dir));Particle p=add("mist",s,point,point.subtract(vector(s,"Center")).scale(.2).add(ownerV),0xACEEFF,range(.5,1.5),245-RNG.nextInt(50));p.fade=rgb(0x91AABC);p.spin=.02;}
            case "cold_snow"->{add("snow",s,at,dir.scale(3/16d),0x4BB1FA,range(.3,1.5),40);}
            case "orb_end"->{for(int i=0;i<4;i++)add("mist",s,at.add(circle(f,26)),vector(s,"Velocity").scale(.5),0x778899,range(1,2),245-RNG.nextInt(50));}
            case "attuned"->{int color=COLORS[Math.max(0,s.getInt("Mode"))];for(int i=0;i<6;i++)add("generic_bloom",s,at.add(f.right().scale(range(-20,20)/16)),new Vec3(0,range(1,5)/16,0),color,range(.02,.22),20+RNG.nextInt(30));for(int i=0;i<11;i++)add("sparkle",s,at.add(f.right().scale(range(-16,16)/16)),new Vec3(0,range(1,5)/16,0),color,range(.3,.7),20+RNG.nextInt(30));}
            case "holy_throw"->{for(int color:new int[]{0xFF69B4,0x00FFFF})for(int i=0;i<9;i++){double a=range(-Math.PI/4,Math.PI/4),m=(1+Math.sin(a*2+Math.PI/2))*(color==0xFF69B4?2:1)*range(7.5,20);add("spark",s,at,ownerV.add(f.direction(f.angle(dir)+a).scale(m/16)),color,range(2,3.5),20+RNG.nextInt(30)).bloomScale=2;}}
            case "beam_end"->{for(int i=0;i<16;i++)add("dust75",s,at.add(dir.scale((-.5+i/15d)*88/16)),vector(s,"Velocity"),0xBFFF3D,2,60);}
            case "pogo"->{for(int i=0;i<8;i++){Vec3 offset=f.direction(f.angle(dir)+Math.PI/2).scale(range(-10,10)/16);Vec3 back=f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(-1);add("smoke",s,at.add(offset),back.scale(9/16d),0xFF4500,range(1.8,2.6),115-RNG.nextInt(30));add("bloom",s,at.subtract(offset.scale(3)),dir.scale(-6/16d*range(.5,1)),0xFFA500,range(.01,.21),20+RNG.nextInt(40)).color=mul(rgb(0xFFA500),.5);}for(int i=0;i<3;i++)add("stone",s,at.add(f.direction(f.angle(dir)+Math.PI/2).scale(range(-30,30)/16)),f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(-range(3,6)/16),0xF5F5DC,range(1,1.4),30+RNG.nextInt(50));}
            case "hot_end"->{
                double time=Minecraft.getInstance().level.getGameTime()/20d,ratio=clamp(s.getDouble("Shred")/250);
                for(int i=0;i<4;i++){
                    double wobble=Math.sin(time*5+i*Math.PI/2),a=f.angle(dir)+wobble*Math.PI/10-wobble*Math.PI/9*ratio;
                    Vec3 base=at.add(dir.scale(Math.sin(time*7)*10/16d)).add(f.direction(f.angle(dir)+Math.PI/2).scale(Math.sin(wobble)*(20+40*ratio)/16));
                    for(int j=0;j<4;j++){Particle p=add("sparkle",s,base.add(f.direction(a).scale((60+j*50)/16d)),dir.scale(vector(s,"Velocity").length()*range(.9,1.1)),0xFF4500,range(.3,.7),20+RNG.nextInt(30));p.bloom=rgb(0xFF4500);p.color=mix(rgb(0x00FFFF),rgb(0xFFA500),(j+1)/4d);}
                }
            }
            case "monolith_start"->{for(int i=0;i<20;i++)add("spark",s,at,spatial(circle(f,1).scale(range(7.5,20)),f.forward()),RNG.nextBoolean()?0x48D1CC:0xFF8C00,range(.1,1.6),20+RNG.nextInt(30));}
            case "monolith_burst"->{double size=s.getDouble("Size");for(int i=0;i<8;i++)add("smoke",s,at.add(f.direction(f.angle(dir)+Math.PI/2).scale(range(0,10)/16)),f.direction(f.angle(dir)+range(-Math.PI/2,Math.PI/2)).scale(range(5,15)/16),0xFF8C00,range(2.8,3.6)*size,165-RNG.nextInt(30)).color=mix(rgb(0xFF8C00),rgb(0x48D1CC),RNG.nextDouble());for(int i=0;i<6;i++)add("stone",s,at.add(f.direction(f.angle(dir)+Math.PI/2).scale(range(30,90)/16)),f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(range(3,6)/16),0x778899,range(1,3.4)*size,30+RNG.nextInt(50)).color=mix(rgb(0x483D8B),rgb(0x778899),RNG.nextDouble());}
            case "dash_hit"->{for(int i=0;i<3;i++){Particle p=add("spark",s,at,f.direction(f.angle(dir)+range(-Math.PI/4,Math.PI/4)).scale(9/16d),0xDC143C,range(1,2),30);p.spin=.4;p.bloomScale=.6;}}
            case "critical"->{for(int i=0;i<(s.getString("Event").equals("broken_biome_fx")?3:4);i++)add("spark",s,at,f.direction(f.angle(dir)+range(-Math.PI/2,Math.PI/2)).scale(9/16d),0x32CD32,range(1,2),30).spin=.4;}
        }
    }
    private static int drawOrder(Particle p){return p.type.startsWith("dust")&&!p.type.equals("dust_big")&&!p.type.equals("dust")?0:p.type.equals("smoke")||p.type.equals("stone")||p.type.equals("water")?1:2;}
    public static void render(PoseStack pose,MultiBufferSource buffers,Vec3 camera,float partial){
        AttackFrame face=NativeSprites.billboard();
        for(Particle p:DISPLAY.stream().sorted(Comparator.comparingInt(BiomeEffects::drawOrder)).toList()){
            if(partial<p.born||partial>=p.died)continue;
            int step=Math.min(2,(int)(partial*3));double t=partial*3-step;State a=p.states[step],b=p.states[step+1];
            Vec3 at=a.at.lerp(b.at,t);double scale=lerp(a.scale,b.scale,t),rotation=lerp(a.rotation,b.rotation,t),opacity=lerp(a.opacity,b.opacity,t);
            var d=new TrueBiomeDraw(new NativeSprites.Draw(pose,buffers,camera,p.owner,true));float[] c=mul(p.color,opacity);
            if(p.type.equals("dust33")||p.type.equals("dust16")||p.type.equals("dust75")||p.type.equals("dust137")){
                int type=p.type.equals("dust33")?33:p.type.equals("dust16")?16:p.type.equals("dust137")?137:75;
                float[] dust=mul(rgb(0xFFFFFF),type==75?1:opacity);if(type==75)dust[3]=25/255f;
                int light=type==75?15728880:net.minecraft.client.renderer.LevelRenderer.getLightColor(Minecraft.getInstance().level,net.minecraft.core.BlockPos.containing(at));
                d.dust(at,face,rotation,scale,dust,type,p.variant,light,null);
                if(type!=75&&type!=137)d.dust(at,face,rotation,scale,dust,type,p.variant,light,new float[]{1,1,1,1});
                continue;
            }
            String texture=BiomeAssets.texture(p.type.equals("spark")?"thin_spark":p.type.equals("generic_bloom")?"light":p.type);int w=NativeSprites.width(texture),h=NativeSprites.height(texture);
            if(p.type.equals("spark")||p.type.equals("sparkle")||p.type.equals("snow")){
                double bs=scale*p.bloomScale*h/NativeSprites.height(BiomeAssets.texture("bloom"));
                d.centered(BiomeAssets.texture("bloom"),at,face,0,bs,bs,mul(p.bloom,opacity*.5),true);
                if(p.type.equals("snow")){for(int i=0;i<3;i++)d.centered(texture,at,face,2*rotation+i*Math.PI/3,scale,scale,mul(c,.5),true);}
                else{
                    if(p.type.equals("sparkle"))d.centered(texture,at,face,rotation+Math.PI/4,scale*.75,scale*.75,mul(c,.5),true);
                    d.centered(texture,at,face,rotation,scale,scale,c,true);
                }
            }else if(p.type.equals("stone")||p.type.equals("mist")||p.type.equals("dust")||p.type.equals("dust_big")){
                h/=p.type.equals("stone")?5:3;
                if(p.type.equals("mist"))c=mul(mix(p.color,p.fade,(255-lerp(a.sourceOpacity,b.sourceOpacity,t)-100)/80),opacity);
                d.region(texture,at,face,rotation,scale,w/2d,h/2d,c,0,p.variant*h,w,h,!p.type.equals("stone"),15728880);
            }else{
                if(p.type.equals("smoke"))c=mul(mix(p.color,p.fade,(255-lerp(a.sourceOpacity,b.sourceOpacity,t)-100)/80),opacity);
                d.centered(texture,at,face,rotation,scale,scale,c,p.type.equals("bloom")||p.type.equals("generic_bloom"));
            }
        }
    }
}
