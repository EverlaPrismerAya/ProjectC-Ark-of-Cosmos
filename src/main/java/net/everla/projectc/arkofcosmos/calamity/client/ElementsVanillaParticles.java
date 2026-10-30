package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.*;

/** The five vanilla Dust IDs, Gore 16/17 and TechyHoloysquare used by Elements.
 * Dust/Gore velocities spread in 3D at source speed; sprites billboard.
 * Source checkpoints: reference/elements/decompiled/{Dust,Gore,ElementalMix,TechyHoloysquareParticle}.cs.
 */
public final class ElementsVanillaParticles {
    private static final List<Particle> LIVE=new ArrayList<>(),FRAME=new ArrayList<>();
    // Spatial sampling must not consume the source emission RNG.
    private static final Random SPREAD=new Random();
    private static final Map<Integer,Long> RESONANCE=new HashMap<>();
    public static void resonance(int target,long tick){RESONANCE.put(target,tick+2);}
    private static int phase,discoR=255,discoG,discoB,discoStyle;
    private static final int[][] HOLO={{8,0,6,6},{6,8,10,6},{4,16,14,8},{2,26,18,10},{2,38,18,8},{6,48,12,12}};
    private static final float[][] COLORS={rgb(0,191,255),rgb(0,250,154),rgb(255,140,0),rgb(238,130,238)};
    private record State(Vec3 at,double scale,double rotation,double opacity) {}
    static final class Particle {
        final int type,owner,variant;final AttackFrame plane;final float[] color;
        Vec3 at,velocity;double scale,rotation,opacity=1,born,died=1;int age,life,alpha;boolean noGravity;
        final State[] states=new State[4];
        Particle(int type,Vec3 at,Vec3 velocity,double scale,float[] color,AttackFrame plane,int owner){
            this.type=type;this.at=at;this.velocity=velocity;this.scale=scale;this.color=color;this.plane=plane;this.owner=owner;
            variant=rng.nextInt(type==-1?6:3);life=type==-1?8+rng.nextInt(6):Integer.MAX_VALUE;
        }
        State state(){return new State(at,scale,rotation,opacity);}
        boolean update(){
            if(type==-1){ // GeneralParticleHandler moves before Particle.Update.
                at=at.add(velocity);opacity=Math.sqrt(++age/(double)life);rotation=plane.angle(velocity);velocity=velocity.scale(.875);scale*=.96;return age<life;
            }
            if(type==16||type==17){
                velocity=velocity.scale(.98);scale-=.01;if(scale<.1){scale=.1;alpha=255;}
                at=at.add(velocity);return alpha<255;
            }
            at=at.add(velocity);
            double vx=velocity.dot(plane.forward())*16,vy=velocity.dot(plane.right())*16,vz=velocity.dot(plane.normal())*16;
            if(type==247){rotation+=.1*scale;if(alpha<255){scale+=.09;if(scale>=1){scale=1;alpha=255;}}else{if(scale<.8)scale-=.01;if(scale<.5)scale-=.01;}}
            else {if(type==66){rotation+=vx<0?-1:1;scale+=.02;}vx*=.98;vy*=.98;vz*=.98;}
            // Shared tail of Dust.UpdateDust; the noGravity tail is an else-if after special types.
            vx*=.99;vz*=.99;rotation+=vx*.5;scale-=.01;
            if(noGravity){vx*=.92;vy*=.92;vz*=.92;scale-=.04;}
            velocity=plane.forward().scale(vx/16).add(plane.right().scale(vy/16)).add(plane.normal().scale(vz/16));return scale>=.1;
        }
    }
    public static void clear(){RESONANCE.clear();LIVE.clear();FRAME.clear();phase=0;discoR=255;discoG=discoB=discoStyle=0;}
    public static void beginFrame(){FRAME.clear();FRAME.addAll(LIVE);for(Particle p:FRAME){p.born=0;p.died=1;Arrays.fill(p.states,p.state());}}
    static Vec3 spread(Vec3 velocity,Random spatial){
        double z=spatial.nextDouble()*2-1,angle=spatial.nextDouble()*Math.PI*2;
        double radius=Math.sqrt(Math.max(0,1-z*z));
        return new Vec3(radius*Math.cos(angle),radius*Math.sin(angle),z).scale(velocity.length());
    }
    private static void add(Particle p){if(p.type!=-1)p.velocity=spread(p.velocity,SPREAD);p.born=phase/3d;Arrays.fill(p.states,p.state());LIVE.add(p);FRAME.add(p);}
    public static void update(int step){phase=step-1;for(var it=LIVE.iterator();it.hasNext();){Particle p=it.next();boolean alive=p.update();p.states[step]=p.state();if(!alive){p.died=step/3d;it.remove();}}}
    private static Particle dust(int type,Vec3 at,Vec3 speed,double scale,int alpha,float[] color,boolean perfect,boolean noGravity,AttackFrame f,int owner){
        // NewDust's original integer random offset, velocity and scale. NewDustPerfect resets position and its caller sets velocity/scale.
        Vec3 jitter=f.forward().scale((rng.nextInt(41)-20)*.1/16).add(f.right().scale((rng.nextInt(41)-20)*.1/16));
        Particle p=new Particle(type,perfect?at:at.add(f.forward().scale((4+rng.nextInt(10))/16d)).add(f.right().scale((4+rng.nextInt(10))/16d)),perfect?speed:speed.add(jitter),perfect?scale:scale*(1+(rng.nextInt(41)-20)*.01),color,f,owner);
        p.alpha=alpha;p.noGravity=noGravity;add(p);return p;
    }
    private static Particle gore(int type,Vec3 at,Vec3 speed,AttackFrame f,int owner,double multiplier,Vec3 drift){
        Vec3 v=speed.add(f.forward().scale((rng.nextInt(41)-20)*.1/16)).subtract(f.right().scale((10+rng.nextInt(21))*.1/16));
        Particle p=new Particle(type,at,v.scale(multiplier).add(drift),.7,WHITE,f,owner);p.alpha=100;add(p);return p;
    }
    public static void emit(CompoundTag s,int step){
        phase=step-1;int kind=s.getInt("Kind");if(kind!=NEEDLE&&kind!=STAR)return;
        Vec3 at=vector(s,"Position"),speed=vector(s,"Velocity");AttackFrame f=new AttackFrame(vector(s,"F"),vector(s,"R"));int owner=s.getInt("Owner");
        if(kind==NEEDLE&&rng.nextInt(3)==0){
            dust(66,at,speed.scale(.05),1.2,150,rgb(discoR,100,255),false,true,f,owner);
        }
        if(rng.nextInt(3)==0)dust(new int[]{15,57,58}[rng.nextInt(3)],at,speed.scale(.1),1.3,150,new float[]{0,0,0,0},false,false,f,owner);
        if(kind==STAR&&rng.nextInt(48)==0)gore(16,at,speed.scale(.2),f,owner,.66,speed.scale(.3));
    }
    public static void death(CompoundTag s){
        AttackFrame f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        Vec3 at=vector(s,"At").subtract(f.forward().add(f.right()).scale(10/16d));
        for(int i=0;i<3;i++)gore(16+rng.nextInt(2),at,vector(s,"Velocity").scale(.05),f,s.getInt("Owner"),1,Vec3.ZERO);
    }
    private static Vec3 surface(LivingEntity e){
        // Approved target-surface mapping of the original 2D hitbox sampling, with no extra layers/count.
        double angle=random()*Math.PI*2;return e.position().add(Math.cos(angle)*e.getBbWidth()/2,random()*e.getBbHeight(),Math.sin(angle)*e.getBbWidth()/2);
    }
    public static void status(int step){
        phase=step-1;
        // Main.DoUpdate_AnimateDiscoRGB uses six independent if blocks (including same-tick transitions).
        if(discoStyle==0&&(discoG+=7)>=255){discoG=255;discoStyle++;}
        if(discoStyle==1&&(discoR-=7)<=0){discoR=0;discoStyle++;}
        if(discoStyle==2&&(discoB+=7)>=255){discoB=255;discoStyle++;}
        if(discoStyle==3&&(discoG-=7)<=0){discoG=0;discoStyle++;}
        if(discoStyle==4&&(discoR+=7)>=255){discoR=255;discoStyle++;}
        if(discoStyle==5&&(discoB-=7)<=0){discoB=0;discoStyle=0;}
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        // Only the server decides whether an entity has the status; the short lease also expires on untracking.
        RESONANCE.entrySet().removeIf(entry->entry.getValue()<mc.level.getGameTime());
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof LivingEntity e&&RESONANCE.containsKey(e.getId())){
            AttackFrame f=new AttackFrame(NativeSprites.billboard().forward(),new Vec3(0,-1,0));boolean player=e instanceof Player;
            if(player||rng.nextBoolean()){
                float[] color=COLORS[rng.nextInt(4)];Vec3 at=surface(e);
                Particle p=new Particle(-1,at,f.forward().scale(range(-3,3)/16).add(f.right().scale(range(-2.5,-8.3)/16)),range(1.2,player?1.8:3.1),color,f,e.getId());p.rotation=random()*Math.PI*2;add(p);
                int type=rng.nextBoolean()?66:247;
                dust(type,player?surface(e):at,f.forward().scale(range(player?-1:-2,player?1:2)/16).add(f.right().scale(range(player?4:2,player?8:4)/16)).subtract(e.getDeltaMovement().scale(1/6d)),type==66?1.4:1.2,90+rng.nextInt(60),color,true,true,f,e.getId());
            }
            if(!player&&rng.nextInt(5)==0){int type=rng.nextBoolean()?66:247;dust(type,surface(e),f.forward().scale(range(-2,2)/16).add(f.right().scale(range(2,4)/16)),type==66?.9:1.4,35+rng.nextInt(55),COLORS[rng.nextInt(4)],true,true,f,e.getId());}
        }
    }
    public static void render(double partial,AttackFrame face){
        for(Particle p:FRAME){
            if(partial<p.born||partial>=p.died)continue;int n=Math.min(2,(int)(partial*3));double fraction=partial*3-n;
            State a=p.states[n],b=p.states[n+1];Vec3 at=a.at.lerp(b.at,fraction);double size=lerp(a.scale,b.scale,fraction),r=lerp(a.rotation,b.rotation,fraction),opacity=lerp(a.opacity,b.opacity,fraction);
            ArkFxDraw.ownerView(p.owner,Vec3.ZERO);
            if(p.type==-1){int[] rect=HOLO[p.variant];
                for(int channel=0;channel<3;channel++){float[] color=new float[]{0,0,0,1};color[channel]=p.color[channel]*(float)opacity;
                    Vec3 offset=face.direction(r+Math.PI/2).scale((channel-1)*1.5/16);
                    // MultiplyRGB does not multiply alpha: source color alpha is opaque despite RGB channel masks having alpha zero.
                    color[3]=(float)opacity;ArkFxDraw.sprite("elements/techy_holosquare",at.add(offset),face,r,size/2,size/2,rect[2]/2d,rect[3]/2d,color,ArkFxDraw.Blend.ADDITIVE,rect[0],rect[1],rect[2],rect[3]);}
            }else if(p.type==16||p.type==17){int[] dimensions=ArkFxDraw.size("elements/gore_"+p.type);float[] color={1,1,1,(255-p.alpha)/255f};ArkFxDraw.sprite("elements/gore_"+p.type,at.add(p.plane.forward().scale(dimensions[0]/2d/16)).add(p.plane.right().scale(dimensions[1]/2d/16)),face,r,size,size,dimensions[0]/2d,dimensions[1]/2d,color,ArkFxDraw.Blend.ALPHA);
            }else{
                int x=p.type%100*10,y=p.type/100*30+p.variant*10;
                // Main.DrawDust: GetAlpha pass then optional GetColor pass, both premultiplied AlphaBlend.
                float value=p.type==15||p.type==57||p.type==58?((255-p.alpha)/255f+3)/4:1;
                float[] color={value,value,value,p.type==66||p.type==247?0:(255-p.alpha)/255f};
                ArkFxDraw.sprite("elements/dust",at,face,r,size,size,4,4,color,ArkFxDraw.Blend.ALPHA,x,y,8,8);
                if(p.color[0]!=0||p.color[1]!=0||p.color[2]!=0){float[] overlay={Math.max(0,p.color[0]-(1-value)),Math.max(0,p.color[1]-(1-value)),Math.max(0,p.color[2]-(1-value)),Math.max(0,p.color[3]-(1-color[3]))};ArkFxDraw.sprite("elements/dust",at,face,r,size,size,4,4,overlay,ArkFxDraw.Blend.ALPHA,x,y,8,8);}
            }
        }
    }
}
