package net.everla.projectc.arkofcosmos.calamity.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.entity.GalaxiaEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkFxParticle.Type.*;

/** Source projectile AI emission runs per synced Terraria substep; particles update at 60 Hz. */
public final class GalaxiaEffects {
    static final class Visual {
        int step=-1;
        int state=-1;
        final List<CompoundTag> history=new ArrayList<>();
        ArkFxParticle head,smear;
        final ArkFxParticle[] rings=new ArkFxParticle[3];
    }
    private static final Map<Integer,Visual> visuals=new HashMap<>();
    static void clear(){visuals.clear();}
    static Visual visual(int id){return visuals.get(id);}
    static AttackFrame frame(CompoundTag s){return new AttackFrame(vector(s,"F"),vector(s,"R"));}
    static Vec3 vector(CompoundTag s,String key){return new Vec3(s.getDouble(key+"X"),s.getDouble(key+"Y"),s.getDouble(key+"Z"));}
    private static Vec3 circular(AttackFrame f,double radius){return f.direction(random()*Math.PI*2).scale(random()*radius/16);}
    static Vec3 rotate(AttackFrame f,Vec3 v,double angle){return f.direction(f.angle(v)+angle).scale(v.length());}
    static void tick(int phase){
        var level=Minecraft.getInstance().level;if(level==null)return;
        Set<Integer> live=new HashSet<>();
        for(var entity:level.entitiesForRendering())if(entity instanceof GalaxiaEntity e){
            live.add(e.getId());consume(e.getId(),e.view(),phase);
        }
        if(phase==2)visuals.keySet().removeIf(id->!live.contains(id));
    }
    static void consume(int id,CompoundTag state,int phase){
        Visual v=visuals.computeIfAbsent(id,key->new Visual());
        var path=state.getList("Samples",10);int count=path.size()-1;
        if(count<=0)return;
        int from=phase<0?1:1+phase*count/3,to=phase<0?count:(phase+1)*count/3;
        galaxiaOwner(state.getInt("Owner"),()->{
            for(int i=from;i<=to;i++){
                CompoundTag s=path.getCompound(i);int step=s.getInt("Age");
                if(step<=v.step)continue;v.step=step;
                v.history.add(0,s.copy());if(v.history.size()>24)v.history.remove(v.history.size()-1);
                emit(s,v);
            }
        });
    }
    private static ArkFxParticle make(ArkFxParticle.Type type,Vec3 p,Vec3 velocity,float[] c,double scale,int life,AttackFrame f){
        ArkFxParticle a=particle(type,p,velocity,c,scale,life,f);
        if(type==SMOKE||type==SMEAR||type==CONSTELLATION)a.rotation=a.previousRotation=0;
        return a;
    }
    private static ArkFxParticle sparkle(Vec3 p,float[] bloom,double scale,double spin,double bloomScale,AttackFrame f){
        ArkFxParticle a=make(SPARKLE,p,Vec3.ZERO,WHITE,scale,2,f);a.bloom=bloom;a.spin=spin;a.bloomScale=bloomScale;return a;
    }
    private static void renew(ArkFxParticle p){
        p.time=0;
        if(!particles.contains(p)){p.beginFrame();add(p);}
    }
    private static void smokeAt(Vec3 p,Vec3 velocity,float[] c,int life,double scale,double opacity,boolean glow,double hue,AttackFrame f){
        ArkFxParticle a=make(SMOKE,p,velocity,c,scale,life,f);a.opacity=opacity;a.glowing=glow;a.hueShift=hue;
    }
    private static void crit(Vec3 p,Vec3 velocity,float[] c,float[] bloom,double scale,int life,double spin,double bloomScale,AttackFrame f){
        ArkFxParticle a=make(CRIT,p,velocity,c,scale,life,f);a.bloom=bloom;a.spin=spin;a.bloomScale=bloomScale;
    }
    private static void emit(CompoundTag s,Visual v){
        int kind=s.getInt("Kind"),mode=s.getInt("Mode"),state=s.getInt("State"),charge=s.getInt("Charge"),left=s.getInt("Left")+1;
        double scale=s.getFloat("Scale"),rotation=s.getFloat("Rotation"),time=s.getDouble("Time");
        AttackFrame f=frame(s);Vec3 p=vector(s,"Position"),center=vector(s,"Center"),vel=vector(s,"Velocity"),ov=vector(s,"OwnerVelocity"),dir=vector(s,"Direction");
        float[] midnight=mix(rgb(25,25,112),rgb(75,0,130),Math.sin(time*6));
        float[] purple=mix(rgb(128,0,128),rgb(75,0,130),Math.sin(time*6));
        if(kind==BLADE && mode==0 && state==1 && v.state!=1){
            for(int i=0;i<=8;i++){
                double angle=range(-Math.PI/4,Math.PI/4),amount=Math.sin(angle*2+Math.PI/2);
                crit(p,ov.add(rotate(f,dir,angle).scale((1+amount)*2*range(7.5,20)/16)),rgb(255,127,80),CHOCOLATE,2+range(0,1.5),20+rng.nextInt(30),1,2,f);
            }
        }
        v.state=state;
        if(kind==BOLT){
            if(v.head==null)v.head=sparkle(p,net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.hsl(s.getFloat("Hue"),100,50,1),1.2,.06,3,f);
            else{renew(v.head);v.head.position=p.add(vel.scale(.5));v.head.scale+=Math.sin(time*6)*.02*scale;}
            if(rng.nextBoolean()){
                smokeAt(p,vel.scale(.5),midnight,30,range(.6,1.2)*scale,.8,false,0,f);
                if(rng.nextInt(3)==0)smokeAt(p,vel.scale(.5),hsl(s.getFloat("Hue"),.7),20,range(.4,.7)*scale,.8,true,.005,f);
            }
        }else if(kind==STAR){
            if(v.head==null)v.head=sparkle(p,CORNFLOWER,scale*2,.1,5,f);
            else{renew(v.head);v.head.position=p;v.head.scale=scale*2;}
            Vec3 velocity=circular(f,10).add(vel.scale(.5));
            smokeAt(p,velocity,purple,30,range(.6,1.2),.8,false,0,f);
            if(rng.nextInt(3)==0)smokeAt(p,velocity,hsl(.55,.5),20,range(.4,.7),.8,true,.01,f);
        }else if(kind==BLADE && mode==0 && state==0){
            if(charge>=300 && rng.nextBoolean()){
                double amount=clamp((charge/600d-.5)*3),opacity=amount*.75;
                Vec3 perpendicular=rotate(f,dir,-Math.PI/2);
                for(double j=0;j<=1;j+=.5){
                    Vec3 at=p.add(dir.scale((60+40*j)*scale/16)).add(perpendicular.scale(30*amount*random()/16));
                    Vec3 velocity=perpendicular.scale(20*amount/16).add(ov);
                    smokeAt(at,velocity,mix(rgb(25,25,112),rgb(75,0,130),j),10+rng.nextInt(5),amount*range(2.8,3.1),opacity+range(0,.2),false,0,f);
                    if(rng.nextInt(3)==0)smokeAt(at,velocity,ORANGE_RED,7,amount*range(2,2.4),opacity*2,true,.001,f);
                }
            }
            if(charge>=450){
                float[] color=mul(CHOCOLATE,(charge/600d-.75)/.25*.8);
                if(v.smear==null){v.smear=make(SMEAR,center,Vec3.ZERO,color,scale*1.5,2,f);v.smear.texture="circularsmearsmokey";v.smear.rotation=rotation;}
                else{renew(v.smear);v.smear.plane=f;v.smear.rotation=rotation+Math.PI/2;v.smear.position=center;v.smear.scale=scale*1.9;v.smear.color=color;}
                if(rng.nextBoolean()){
                    double radius=scale*1.9*78;Vec3 offset=circular(f,radius);
                    Vec3 velocity=rotate(f,offset,Math.PI/2).normalize().scale(2*(1+offset.length()*16/15)/16).add(ov);
                    crit(center.add(offset),velocity,rng.nextInt(3)==0?rgb(64,224,208):rgb(255,127,80),color,1+offset.length()*16/radius,10,.05,3,f);
                }
            }
        }else if(kind==BLADE && mode==1){
            if(v.smear==null){v.smear=make(SMEAR,p,Vec3.ZERO,rgb(186,85,211),scale,2,f);v.smear.texture="circularsmearsmokey";}
            renew(v.smear);v.smear.position=p;v.smear.rotation=rotation+Math.PI*.75;v.smear.scale=scale;v.smear.color[3]=(float)clamp(s.getInt("Age")/50d);
            if(rng.nextBoolean()){
                double radius=scale*82;Vec3 offset=circular(f,radius),velocity=rotate(f,offset,Math.PI/2).normalize().scale(2*(1+offset.length()*16/15)/16);
                crit(p.add(offset),velocity,rng.nextInt(3)==0?rgb(255,105,180):rgb(221,160,221),rgb(153,50,204),1+offset.length()*16/radius,10,.05,3,f);
            }
            Vec3 offset=circular(f,scale*62),velocity=rotate(f,offset,Math.PI/2).normalize().scale(.1*(1+offset.length()*16/15)/16);
            smokeAt(p.add(offset),velocity,mix(rgb(0,0,128),rgb(75,0,130),Math.sin(time*6)),30,range(.4,1)*scale,.8,false,0,f);
            if(rng.nextInt(3)==0)smokeAt(p.add(offset),velocity,hsl(.85,.5),20,range(.4,1)*scale,.8,true,.01,f);
        }else if(kind==BLADE && mode==2){
            double ratio=clamp(charge/325d);
            for(int j=0;j<3;j++){
                ArkFxParticle ring=v.rings[j];
                if(ring==null){
                    ring=make(CONSTELLATION,center.add(dir.scale((25+120*scale*.33*j)/16)),Vec3.ZERO,rgb(153,50,204),scale*.25*j,2,f);
                    ring.rotation=rotation;ring.variant=3+j;ring.expansion=range(0,Math.PI*2);v.rings[j]=ring;
                }else{
                    double x=clamp((charge-30*j)/650d)*650,bounce=x<=50?x/50:x<=65?1+.15*Math.sin((x-50)/15*Math.PI):1;
                    renew(ring);ring.plane=f;ring.position=center.add(dir.scale((.9+.1*Math.sin(time*10-j*.5))*(25+120*scale*.33*(j+1))*bounce/16));
                    ring.rotation=rotation;ring.scale=scale*.25*(j+1);ring.opacity=.5+.5*ratio;
                }
            }
            if(v.head==null)v.head=sparkle(center.add(dir.scale(1/16d)),CORNFLOWER,scale,.05,5,f);
            else{renew(v.head);v.head.position=center.add(dir.scale(46*scale/16));v.head.rotation+=(1+Math.sin(time*4))*.02;v.head.scale=scale*2;}
            if(rng.nextBoolean()){
                Vec3 velocity=rotate(f,dir,range(-.2356194704771042,.2356194704771042)).scale(range(10,30)*(ratio*.5+1)/16).add(ov);
                smokeAt(p,velocity,purple,30,range(.6,1.2)*(ratio*.5+1),.8,false,0,f);
                if(rng.nextInt(3)==0)smokeAt(p,velocity,hsl(.55,.5+.2*ratio),20,range(.4,.7)*(ratio*.5+1),.8,true,.01,f);
            }
        }else if(kind==BLADE && mode==3){
            if(state==0 && charge>=360 && rng.nextBoolean()){
                Vec3 velocity=rotate(f,dir,range(-.2356194704771042,.2356194704771042)).scale(range(10,30)*.9/16).add(ov),at=p.add(dir.scale(50/16d));
                smokeAt(at,velocity,purple,30,range(.6,1.2),.8,false,0,f);
                if(rng.nextInt(3)==0)smokeAt(at,velocity,hsl(.85,.8),20,range(.4,.7),.8,true,.01,f);
            }
            if(state==1){
                double angle=range(-Math.PI/4,Math.PI/4),amount=Math.sin(angle*2+Math.PI/2);
                crit(p,dir.scale(30/16d).subtract(rotate(f,dir,angle).scale((1+amount)*2*range(7.5,20)/16)),WHITE,rng.nextBoolean()?rgb(72,209,204):rgb(255,140,0),.1+range(0,1.5),20+rng.nextInt(30),1,3,f);
            }
        }else if(kind==SPROUT){
            // The surface-normal frame is supplied by the server for the approved annular conversion.
            AttackFrame surface=surfaceFrame(f,dir);double r=surface.angle(dir);
            if(left<29 && rng.nextInt(3)==0){
                Vec3 velocity=surface.direction(r-Math.PI/4+range(-Math.PI/8,Math.PI/8)).scale(range(15,35)/16);
                smokeAt(p,velocity,midnight,30,range(.4,1.3)*scale,.8,false,0,surface);
                if(rng.nextInt(3)==0)smokeAt(p,velocity,rgb(255,0,0),20,range(.1,.7)*scale,.8,true,.01,surface);
            }
            if(left==2)for(int i=0;i<8;i++){
                Vec3 at=p.add(dir.scale(range(0,10)/16)),velocity=surface.direction(r-Math.PI/2+range(-Math.PI/2,Math.PI/2)).scale(range(5,15)/16);
                smokeAt(at,velocity,midnight,30,range(1.6,2.2)*scale,.8,false,0,surface);
                if(rng.nextInt(3)==0)smokeAt(at,velocity,rgb(255,0,0),20,range(1.4,1.7)*scale,.8,true,.005,surface);
            }
        }
    }
    static AttackFrame surfaceFrame(AttackFrame original,Vec3 normal){
        Vec3 side=original.right().subtract(normal.scale(original.right().dot(normal))).normalize();
        if(side.lengthSqr()<.5)side=original.forward().subtract(normal.scale(original.forward().dot(normal))).normalize();
        return new AttackFrame(normal,side);
    }
    public static void receive(CompoundTag data){
        if(data.getString("Event").equals("galaxia_sound")){GalaxiaAudio.receive(data);return;}
        if(Minecraft.getInstance().level==null)return;
        int id=data.getInt("Id");consume(id,data,-1);
        galaxiaOwner(data.getInt("Owner"),()->{
            AttackFrame f=frame(data);Vec3 at=vector(data,"At"),vel=vector(data,"Velocity");
            int kind=data.getInt("Kind"),mode=data.getInt("Mode");
            if(data.getString("Event").equals("galaxia_death")){
                if(kind==STAR)for(int i=0;i<10;i++){
                    crit(at,circular(f,1).scale(range(17.5,25)*data.getFloat("Scale")),WHITE,rng.nextBoolean()?CORNFLOWER:rgb(123,104,238),.4+range(0,3.5),20+rng.nextInt(30),1,3,f);
                    Vec3 velocity=circular(f,20).add(vel.scale(.5));
                    smokeAt(at,velocity,mix(rgb(139,0,0),rgb(75,0,130),Math.sin(data.getDouble("Time")*6)),30,range(1.5,2.2),.8,false,0,f);
                    if(rng.nextInt(3)==0)smokeAt(at,velocity,hsl(.55,.5),20,range(1.4,1.5),.8,true,.01,f);
                }
                Visual v=visuals.remove(id);
                if(v!=null && kind==BLADE && mode==0 && v.smear!=null)v.smear.time=v.smear.lifetime;
            }else if(kind==BLADE && mode==1){
                Vec3 towards=at.subtract(vector(data,"Center")).normalize();
                for(int i=0;i<2;i++)crit(at,rotate(f,towards,range(-Math.PI/2,Math.PI/2)).scale(9/16d),WHITE,rgb(255,105,180),1+random(),30,.4,1,f);
                double a=random()*Math.PI*2;Vec3 offset=f.forward().scale(Math.cos(a)*50/16).add(f.right().scale(Math.sin(a)*100/16));
                ArkFxParticle line=make(LINE,at.subtract(offset),Vec3.ZERO,mul(rgb(255,105,180),.6),.2,6,f);line.line=offset.scale(2);line.planeSprite=true;
            }else if(kind==SLASH){
                ArkFxParticle bloom=make(BLOOM,at,vector(data,"TargetVelocity"),mul(rgb(220,20,60),.5),1,30,f);bloom.bloom=bloom.color;
                Vec3 towards=vector(data,"Center").subtract(at).normalize();
                for(int i=0;i<3;i++)crit(at,rotate(f,towards,range(-Math.PI/4,Math.PI/4)).scale(9/16d),WHITE,CORNFLOWER,1+random(),30,.4,.6,f);
            }
        });
    }
}

