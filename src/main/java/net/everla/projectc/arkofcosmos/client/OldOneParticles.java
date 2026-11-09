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

/** Dust/Gore used by the sixteen legacy Galaxia2 derivatives. Source: Terraria 1.3.5.3 and AstralOrange. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class OldOneParticles {
    // Reuse native storage across frames, including frames after a large particle burst.
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource BUFFERS=
            net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(256));
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    // Forge listener wrapper names omit the enclosing class; keep this simple name unique.
    public static final class OldOneParticlesRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){
            e.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.ARK_OF_THE_OLD_ONE.get(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),
                (stack,level,entity,seed)->entity!=null&&!entity.level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.OldOneEntity.class,entity.getBoundingBox().inflate(16),a->a.ownerId()==entity.getId()&&a.kind()==Kind.SWORD&&a.level().getGameTime()-a.renderState().getLong("StartTick")<5).isEmpty()?1:0));
        }
    }
    private static final Random RNG=new Random(),SPATIAL=new Random();
    private static final List<Dust> LIVE=new ArrayList<>(),DISPLAY=new ArrayList<>();
    private static final List<CompoundTag> EVENTS=new ArrayList<>();
    private static final Map<UUID,Long> SEEN=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static int red=255,green,blue,style,sourceStep;
    private static final int ASTRAL=-1;
    public static int[] disco(){return new int[]{red,green,blue};}
    private record State(Vec3 at,double scale,double rotation,int alpha){}
    private static final class Dust {
        final int type,variant;final AttackFrame plane;boolean noGravity,noLight,gore;
        Vec3 at,velocity;double scale,rotation,fadeIn;int alpha;float[] color={0,0,0,0};
        final State[] states=new State[4];double born,died=1;
        Dust(int type,Vec3 at,Vec3 velocity,double scale,int alpha,AttackFrame plane){this(type,at,velocity,scale,alpha,plane,false);}
        Dust(int type,Vec3 at,Vec3 velocity,double scale,int alpha,AttackFrame plane,boolean gore){
            this.type=type;this.at=at;this.velocity=velocity;this.scale=scale;this.alpha=alpha;this.plane=plane;
            born=sourceStep/3d;variant=gore?0:RNG.nextInt(3);Arrays.fill(states,state());
        }
        State state(){return new State(at,scale,rotation,alpha);}
        boolean update(){
            if(gore){velocity=velocity.scale(.98);rotation+=velocity.dot(plane.forward())*1.6;scale-=type==16||type==17?.01:.007;at=at.add(velocity);return scale>=.1&&alpha<255;}
            if(type!=ASTRAL&&scale>10)return false;
            at=at.add(velocity);
            if(type==ASTRAL){scale=Math.max(0,scale-.02);return scale>=.1;}
            // Terraria 1.3.5.3 Dust.UpdateDust: the first type chain, then rotation/fadeIn,
            // then the noGravity tail. Fire/smoke special cases still run that tail.
            if(type==6||type==62||type==75||type==229||type==242){if(!noGravity)velocity=velocity.add(0,-.05/16,0);}
            else if(type==31){velocity=velocity.scale(.98);if(noGravity){velocity=velocity.scale(1.02);scale+=.02;alpha+=4;if(alpha>255){scale=.0001;alpha=255;}}}
            else if(type==267){rotation+=velocity.dot(plane.forward())<0?-1:1;velocity=velocity.scale(.98);scale+=.02;noLight=false;}
            else if(type==71||type==72||type==73)velocity=velocity.scale(.98);
            else if(!noGravity)velocity=velocity.add(0,-.1/16,0);
            // Dust.MidUpdate's ordinary horizontal drag applies only to the chain's default.
            // These used types have no later special motion branch (92 only emits light).
            velocity=new Vec3(velocity.x*.99,velocity.y,velocity.z*.99);
            rotation+=velocity.dot(plane.forward())*8;
            if(fadeIn>0&&fadeIn<100){scale+=.03;if(scale>fadeIn)fadeIn=0;}else scale-=.01;
            if(noGravity){velocity=velocity.scale(.92);if(fadeIn==0)scale-=.04;}
            return scale>=.1;
        }
    }
    public static void receive(CompoundTag data){
        if(world!=Minecraft.getInstance().level)clear();
        if(data.hasUUID("Attack")){
            UUID id=data.getUUID("Attack");long sequence=data.getLong("Sequence");
            if(sequence<=SEEN.getOrDefault(id,0L))return;SEEN.put(id,sequence);
        }
        EVENTS.add(data.copy());
    }
    private static void clear(){LIVE.clear();DISPLAY.clear();EVENTS.clear();SEEN.clear();world=Minecraft.getInstance().level;red=255;green=blue=style=0;}
    private static Dust add(Dust d){LIVE.add(d);DISPLAY.add(d);return d;}
    private static Dust dust(int type,Vec3 center,int width,int inset,Vec3 initial,double scale,int alpha,boolean noGravity,AttackFrame f){
        int area=Math.max(5,width-inset*2);
        Vec3 at=center.subtract(f.forward().add(f.right()).scale((width/2d-inset)/16));
        at=at.add(f.forward().scale((4+RNG.nextInt(Math.max(1,area-4)))/16d)).add(f.right().scale((4+RNG.nextInt(Math.max(1,area-4)))/16d));
        Vec3 jitter=f.forward().scale((RNG.nextInt(41)-20)*.1/16).add(f.right().scale((RNG.nextInt(41)-20)*.1/16));
        Dust d=new Dust(type,at,initial.add(jitter),scale,alpha,f);d.scale*=1+(RNG.nextInt(41)-20)*.01;
        if(type==6||type==62||type==75||type==242){double x=d.velocity.dot(f.forward())*.3,y=(RNG.nextInt(16)-10)*.1/16;d.velocity=f.forward().scale(x).add(f.right().scale(y));d.scale*=.7;}
        d.noGravity=noGravity||type==ASTRAL;return add(d);
    }
    private static Dust cloneDust(Dust a){
        // CloneDust allocates through NewDust before copying every visible field.
        Dust d=dust(a.type,a.at,0,0,Vec3.ZERO,1,0,false,a.plane);
        d.at=a.at;d.velocity=a.velocity;d.scale=a.scale;d.alpha=a.alpha;d.rotation=a.rotation;d.fadeIn=a.fadeIn;d.noGravity=a.noGravity;d.noLight=a.noLight;d.color=a.color.clone();return d;
    }
    private static Dust gore(int type,Vec3 at,Vec3 speed,AttackFrame f){
        double y=(10+RNG.nextInt(21))*.1/16,x=(RNG.nextInt(41)-20)*.1/16;
        Vec3 v=speed.add(f.forward().scale(x)).subtract(f.right().scale(y));
        Dust d=new Dust(type,at,v,type==16||type==17?.7:1,type==16||type==17?100:0,f,true);d.gore=true;return add(d);
    }
    private static void spread(Dust d){
        double z=SPATIAL.nextDouble()*2-1,a=SPATIAL.nextDouble()*Math.PI*2,r=Math.sqrt(1-z*z);
        d.velocity=new Vec3(r*Math.cos(a),z,r*Math.sin(a)).scale(d.velocity.length());Arrays.fill(d.states,d.state());
    }
    private static Vec3 randomDirection(AttackFrame f){return f.direction(RNG.nextDouble()*Math.PI*2);}
    private static Vec3 radial(Vec3 at,AttackFrame f,double radius,boolean disk){return at.add(randomDirection(f).scale(radius/16*(disk?RNG.nextDouble():1)));}
    private static float[] hsl(double h,double s,double l){
        double c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs(h*6%2-1)),m=l-c/2;int i=(int)(h*6)%6;
        double[] a=switch(i){case 0->new double[]{c,x,0};case 1->new double[]{x,c,0};case 2->new double[]{0,c,x};case 3->new double[]{0,x,c};case 4->new double[]{x,0,c};default->new double[]{c,0,x};};
        return new float[]{(float)Math.rint((a[0]+m)*255)/255,(float)Math.rint((a[1]+m)*255)/255,(float)Math.rint((a[2]+m)*255)/255,1};
    }
    private static void emit(CompoundTag s){
        Kind k=Kind.values()[s.getInt("Kind")];Vec3 at=ElementsEntity.vector(s,"Position"),v=ElementsEntity.vector(s,"Velocity");
        AttackFrame f=new AttackFrame(ElementsEntity.vector(s,"F"),ElementsEntity.vector(s,"R"));
        boolean death=s.getString("Event").equals("old_one_death");if(s.getString("Event").equals("old_one_bounce"))return;
        int before=LIVE.size(),u=s.getInt("Updates");double scale=s.getDouble("Scale"),rot=s.getDouble("Rotation");
        switch(k){
            case ASTRAL_STAR->astral(at,v,f,death);
            case FLOWER_POW->{if(death)for(int i=0;i<5;i++)dust(40,at,14,0,Vec3.ZERO,1,0,false,f);}
            case ICE_BLADE->{for(int i=0;i<(death?10:2);i++){
                Dust d=dust(92,at,10,0,death?Vec3.ZERO:v,death?1:1.2,death?0:50,!death,f);
                if(!death)d.velocity=d.velocity.scale(.3);else if(RNG.nextInt(3)!=0){d.velocity=d.velocity.scale(2);d.scale*=1.75;d.noGravity=true;}else d.scale*=.5;
            }}
            case WATER_BOLT->{
                if(death){for(int i=0;i<30;i++){dust(172,at,16,0,v.scale(.1),1,100,true,f);dust(172,at,16,0,v.scale(.1),.5,100,false,f);}}
                else{for(int i=0;i<5;i++){Dust d=dust(172,at,16,4,Vec3.ZERO,1.2,100,true,f);d.velocity=d.velocity.scale(.1).add(v.scale(.1));d.at=d.at.subtract(v.scale(i/3d));}
                    if(RNG.nextInt(5)==0){Dust d=dust(172,at,16,4,Vec3.ZERO,.6,100,false,f);d.velocity=d.velocity.scale(.25).add(v.scale(.5));}}
            }
            case ICHOR->{if(!death&&u>4){for(int i=0;i<3;i++){Dust d=dust(170,at,32,14,Vec3.ZERO,1,100,true,f);d.velocity=d.velocity.scale(.1).add(v.scale(.5));d.at=d.at.subtract(v.scale(i/3d));}
                if(RNG.nextInt(8)==0){Dust d=dust(170,at,32,16,Vec3.ZERO,.5,100,false,f);d.velocity=d.velocity.scale(.25).add(v.scale(.5));}}}
            case CURSED_FLAME,FIREBALL->{int type=k==Kind.CURSED_FLAME?75:6;
                if(death){for(int i=0;i<20;i++)for(int j=0;j<2;j++){Dust d=dust(type,at,16,0,v.scale(-.2),(j==0?2:1)*(type==75?scale:1),100,j==0,f);d.velocity=d.velocity.scale(2);}}
                else if(type==75)dust(type,at.add(v),16,0,v,3*scale,100,true,f);
                else for(int i=0;i<2;i++){Dust d=dust(6,at,16,0,v.scale(.2),2,100,true,f);d.velocity=d.velocity.scale(.3);}
            }
            case FLAIRON_BUBBLE->{if(death)for(int i=0;i<20;i++){
                Dust d=dust(212,at,20,0,Vec3.ZERO,1,0,true,f);Vec3 dir=d.at.subtract(at).normalize();d.at=at.add(dir.scale(10*scale/16));d.velocity=dir.scale(d.velocity.length());
                float[] c=hsl(.4+RNG.nextDouble()*.2,.9,.5);for(int j=0;j<3;j++)c[j]=c[j]*.7f+.3f;d.color=c;d.scale=.7;
            }}
            case HOLY_ARROW->holy(at,v,f,death,u);
            case RAINBOW_CRYSTAL->crystal(at,v,f,s,death);
            case NEBULA->nebula(at,v,f,s,death);
            case STARDUST_CELL->{if(death)for(int i=0;i<10;i++){Dust d=dust(229,at,16,0,Vec3.ZERO,1,0,true,f);d.velocity=d.velocity.scale(3);}}
            case SOLAR_EXPLOSION->{if(!death&&u==1)solar(at,v,f,s.getInt("Size"));}
            case VORTEX_ROCKET->{if(death)vortex(at,v,f);else if(s.getInt("Alpha")+25<170)for(int i=0;i<3;i++){
                Dust d=dust(229,at,1,0,Vec3.ZERO,1,0,true,f);d.at=at.subtract(v.scale(i/3d));d.velocity=Vec3.ZERO;d.alpha=200;d.scale=.5;
            }}
            // Native 131 and 604 have no Dust/Gore in their reachable AI/Kill branches.
            case MUSHROOM,TERRARIAN->{}
            default->{}
        }
        // Spatial randomness is separate from the source RNG and never changes counts or speed magnitudes.
        for(int i=before;i<LIVE.size();i++)spread(LIVE.get(i));
    }
    private static void astral(Vec3 at,Vec3 v,AttackFrame f,boolean death){
        if(!death){
            if(RNG.nextInt(16)==0){Vec3 dir=f.direction((RNG.nextDouble()*2-1)*Math.PI/2+f.angle(v));Dust d=dust(ASTRAL,at,24,0,v.scale(.5),1.2,150,true,f);d.velocity=dir.scale(.66/16);d.at=at.add(dir.scale(12d/16));}
            if(RNG.nextInt(48)==0){Dust d=gore(16,at,v.scale(.2),f);d.velocity=d.velocity.scale(.66).add(v.scale(.3));}
        }else{
            for(int i=0;i<5;i++){Dust d=dust(ASTRAL,at,50,0,Vec3.ZERO,1.2,100,true,f);d.velocity=d.velocity.scale(3);if(RNG.nextInt(2)==0){d.scale=.5;d.fadeIn=1+RNG.nextInt(10)*.1;}}
            for(int i=0;i<5;i++){Dust d=dust(ASTRAL,at,50,0,Vec3.ZERO,1.7,100,true,f);d.velocity=d.velocity.scale(5);d=dust(ASTRAL,at,50,0,Vec3.ZERO,1,100,true,f);d.velocity=d.velocity.scale(2);}
            for(int i=0;i<3;i++)gore(16+RNG.nextInt(2),at.subtract(f.forward().add(f.right()).scale(25d/16)),v.scale(.05),f);
        }
    }
    private static void holy(Vec3 at,Vec3 v,AttackFrame f,boolean death,int updates){
        if(!death){
            if(updates>5)for(int i=0;i<3;i++){Dust d=dust(240,at,10,0,Vec3.ZERO,1,0,true,f);d.velocity=d.velocity.scale(.3);d.noLight=true;}
            if(updates==5)for(int i=0;i<16;i++){double a=i*Math.PI*2/16;Vec3 offset=f.forward().scale(Math.sin(a)/16).add(f.right().scale(-Math.cos(a)*4/16));offset=f.direction(f.angle(offset)+f.angle(v)).scale(offset.length());
                Dust d=dust(62,at,0,0,Vec3.ZERO,1,0,true,f);d.scale=1.5;d.noLight=true;d.at=at.add(offset);d.velocity=d.velocity.scale(4).add(v.scale(.3));}
            return;
        }
        for(int i=0;i<4;i++){Dust d=dust(240,at,40,0,Vec3.ZERO,1.5,100,false,f);d.at=radial(at,f,20,true);}
        for(int i=0;i<20;i++){
            Dust d=dust(62,at,40,0,Vec3.ZERO,3.7,200,true,f);d.at=radial(at,f,20,true);d.noLight=true;d.velocity=d.velocity.scale(3).add(d.at.subtract(at).normalize().scale((2+RNG.nextFloat()*4)/16));
            d=dust(62,at,40,0,Vec3.ZERO,1.5,100,true,f);d.at=radial(at,f,20,true);d.velocity=d.velocity.scale(2).add(d.at.subtract(at).normalize().scale(.5));d.fadeIn=1;d.color=new float[]{110/255f,10/255f,30/255f,127/255f};d.noLight=true;
        }
        for(int i=0;i<20;i++){Dust d=dust(62,at,40,0,Vec3.ZERO,2.7,0,true,f);d.at=radial(at,f,20,false);d.velocity=d.velocity.scale(3).add(d.at.subtract(at).normalize().scale(2d/16));d.noLight=true;}
        for(int i=0;i<70;i++){Dust d=dust(240,at,40,0,Vec3.ZERO,1.5,0,true,f);d.at=radial(at,f,20,false);d.velocity=d.velocity.scale(3).add(d.at.subtract(at).normalize().scale(3d/16));}
    }
    private static void solar(Vec3 at,Vec3 v,AttackFrame f,int width){
        for(int i=0;i<4;i++){Dust d=dust(31,at,width,0,Vec3.ZERO,1.5,100,false,f);d.at=radial(at,f,width/2d,true);}
        for(int i=0;i<10;i++){
            Dust d=dust(6,at,width,0,Vec3.ZERO,2.7,200,true,f);d.at=radial(at,f,width/2d,true);d.velocity=d.velocity.scale(3);
            d=dust(6,at,width,0,Vec3.ZERO,1.5,100,true,f);d.at=radial(at,f,width/2d,true);d.velocity=d.velocity.scale(2);d.fadeIn=2.5;
        }
        for(int i=0;i<5;i++){Dust d=dust(6,at,width,0,Vec3.ZERO,2.7,0,true,f);d.at=radial(at,f,width/2d,false);d.velocity=d.velocity.scale(3);}
        for(int i=0;i<10;i++){Dust d=dust(31,at,width,0,Vec3.ZERO,1.5,0,true,f);d.at=radial(at,f,width/2d,false);d.velocity=d.velocity.scale(3);}
    }
    private static void vortex(Vec3 at,Vec3 v,AttackFrame f){
        for(int i=0;i<4;i++)dust(31,at,80,0,Vec3.ZERO,1.5,100,false,f);
        for(int i=0;i<40;i++){Dust d=dust(229,at,80,0,Vec3.ZERO,2.5,200,true,f);d.velocity=d.velocity.scale(2);d=dust(229,at,80,0,Vec3.ZERO,1.5,200,true,f);d.velocity=d.velocity.scale(1.2);}
        Vec3 pos=at.subtract(f.forward().add(f.right()).scale(50d/16)).add(f.forward().scale(80*RNG.nextInt(100)/100d/16)).add(f.right().scale(80*RNG.nextInt(100)/100d/16));
        Dust g=gore(61+RNG.nextInt(3),pos,Vec3.ZERO,f);g.velocity=g.velocity.scale(.3).add(f.forward().scale((RNG.nextInt(21)-10)*.05/16)).add(f.right().scale((RNG.nextInt(21)-10)*.05/16));
    }
    private static void crystal(Vec3 at,Vec3 v,AttackFrame f,CompoundTag s,boolean death){
        float[] color=hsl(0,1,.5);double local=s.getDouble("Local0"),opacity=1-s.getInt("Alpha")/255d,rotation=s.getDouble("Rotation");
        if(death){Vec3 initial=f.direction(RNG.nextDouble()*Math.PI*2).scale(3d/16);int n=7+RNG.nextInt(6);
            for(int pass=0;pass<2;pass++)for(int i=0;i<n;i++){
                double angle=f.angle(initial)+Math.PI*2*i/n;Vec3 dir=f.forward().scale(Math.cos(angle)*2.1).add(f.right().scale(Math.sin(angle)*2));
                Dust d=dust(267,at,0,0,Vec3.ZERO,1,0,true,f);d.color=color.clone();d.at=at;d.velocity=dir.scale(initial.length()*(.8+RNG.nextFloat()*.4));
                if(pass==0){d.scale=2;d.fadeIn=RNG.nextFloat()*2;}else{d.velocity=d.velocity.scale(RNG.nextFloat()*.8);d.scale=RNG.nextFloat();d.fadeIn=RNG.nextFloat()*2;}
                Dust c=cloneDust(d);c.scale/=2;c.fadeIn/=2;c.color=new float[]{1,1,1,1};
            }return;
        }
        for(int pass=0;pass<2;pass++)for(int i=0;i<2;i++)if(RNG.nextInt(10)==0){
            Dust d=dust(267,at,0,0,Vec3.ZERO,1.5,225,true,f);d.noLight=true;d.color=color.clone();d.scale=opacity*local;d.at=at;d.velocity=f.direction(Math.PI/2+i*Math.PI+(pass==0?rotation:0)).scale(2.5/16);
        }
        if(RNG.nextInt(10)==0){double speed=1+RNG.nextFloat()*2,fade=1+RNG.nextFloat(),size=1+RNG.nextFloat();
            Vec3 direction=f.forward().scale(RNG.nextFloat()*2-1).add(f.right().scale(RNG.nextFloat()*2-1)).normalize();Vec3 pos=at.add(direction.scale((20+RNG.nextFloat()*100)/16));
            var level=Minecraft.getInstance().level;var block=net.minecraft.core.BlockPos.containing(pos);
            if(level!=null&&level.isInWorldBounds(block)&&!level.getBlockState(block).isSolidRender(level,block)){
                Dust d=dust(267,pos,0,0,Vec3.ZERO,1,127,true,f);d.at=pos;d.velocity=f.right().scale(-speed*(RNG.nextFloat()*.9+1.6)/16);d.fadeIn=fade;d.scale=size;d.noLight=true;d.color=color.clone();
                Dust c=cloneDust(d);c.scale*=.65;c.fadeIn*=.65;c.color=new float[]{1,1,1,1};
            }
        }
    }
    private static void nebula(Vec3 at,Vec3 v,AttackFrame f,CompoundTag s,boolean death){
        int[] types={242,73,72,71,255};int selected=types[RNG.nextInt(types.length)];double rotation=s.getDouble("Rotation"),local=s.getDouble("Local0");
        if(death){Vec3 drift=f.direction(rotation-Math.PI/2).scale(v.length()*3);
            for(int i=0;i<40;i++){
                selected=types[RNG.nextInt(types.length)];Dust d=dust(selected,at,50,0,Vec3.ZERO,1.7,200,true,f);d.at=radial(at,f,25,true);d.velocity=d.velocity.scale(3).add(drift.scale(RNG.nextFloat()));
                d=dust(255,at,50,0,Vec3.ZERO,.8,100,true,f);d.at=radial(at,f,25,true);d.velocity=d.velocity.scale(2).add(drift.scale(RNG.nextFloat()));d.fadeIn=1;d.color=new float[]{110/255f,10/255f,30/255f,127/255f};
            }
            for(int i=0;i<20;i++){Dust d=dust(255,at,50,0,Vec3.ZERO,2,0,true,f);d.at=radial(at,f,50d/3,false);d.velocity=d.velocity.scale(.5).add(drift.scale(.6+.6*RNG.nextFloat()));}return;
        }
        if((s.contains("NebulaSpiral")?s.getBoolean("NebulaSpiral"):local!=0&&s.getInt("Alpha")==0))for(int i=0;i<2;i++){
            double a=local*Math.PI/24+i*Math.PI;Vec3 offset=f.forward().scale(Math.sin(a)*8/16).add(f.right().scale(-Math.cos(a)*10/16)).subtract(f.direction(rotation).scale(10d/16));
            Dust d=dust(255,at,0,0,Vec3.ZERO,1,160,true,f);d.scale=1.2;d.at=at.add(offset).add(v.scale(2));d.velocity=at.add(v.scale(16)).subtract(d.at).normalize().scale(2d/16).add(v.scale(2));
        }
        if(RNG.nextInt(12)==0){Dust d=nebulaTrail(31,at,v,f,Math.PI/16,1,100);d.velocity=d.velocity.scale(.1);d.at=d.at.add(v.scale(2));d.fadeIn=.9;}
        if(RNG.nextInt(64)==0){Dust d=nebulaTrail(31,at,v,f,Math.PI/8,.8,155);d.velocity=d.velocity.scale(.3);if(RNG.nextInt(2)==0)d.fadeIn=1.4;}
        if(RNG.nextInt(4)==0)for(int i=0;i<2;i++){Dust d=nebulaTrail(selected,at,v,f,Math.PI/4,1.2,0);d.velocity=d.velocity.scale(.3);d.noGravity=true;if(RNG.nextInt(2)==0)d.fadeIn=1.4;}
        if(RNG.nextInt(12)==0){Dust d=nebulaTrail(255,at,v,f,Math.PI/16,1,100);d.velocity=d.velocity.scale(.3);d.fadeIn=.9;d.noGravity=true;}
    }
    private static Dust nebulaTrail(int type,Vec3 at,Vec3 v,AttackFrame f,double range,double scale,int alpha){
        Vec3 direction=f.direction(f.angle(v)+Math.PI+(RNG.nextDouble()*2-1)*range);Dust d=dust(type,at,40,0,Vec3.ZERO,scale,alpha,false,f);d.at=at.add(direction.scale(20d/16));return d;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(world!=Minecraft.getInstance().level)clear();
        if(world==null){clear();return;}if(Minecraft.getInstance().isPaused())return;
        DISPLAY.clear();DISPLAY.addAll(LIVE);for(Dust d:LIVE){Arrays.fill(d.states,d.state());d.born=0;d.died=1;}
        for(int step=1;step<=3;step++){
            sourceStep=step-1;
            for(CompoundTag s:EVENTS)if(s.getInt("SourceStep")==sourceStep)emit(s);
            if(style==0&&(green+=7)>=255){green=255;style++;}if(style==1&&(red-=7)<=0){red=0;style++;}
            if(style==2&&(blue+=7)>=255){blue=255;style++;}if(style==3&&(green-=7)<=0){green=0;style++;}
            if(style==4&&(red+=7)>=255){red=255;style++;}if(style==5&&(blue-=7)<=0){blue=0;style=0;}
            for(var it=LIVE.iterator();it.hasNext();){Dust d=it.next();boolean alive=d.update();d.states[step]=d.state();if(!alive){d.died=step/3d;it.remove();}}
        }EVENTS.clear();
        // Drop deduplication keys once the attack and its final network events have expired.
        if(SEEN.size()>4096){Set<UUID> active=new HashSet<>();for(var entity:world.entitiesForRendering())if(entity instanceof net.everla.projectc.arkofcosmos.entity.OldOneEntity)active.add(entity.getUUID());SEEN.keySet().retainAll(active);}
    }
    private static float[] tint(Dust d,int alpha){
        if(d.type==267)return new float[]{d.color[0],d.color[1],d.color[2],0};
        if(d.type==92||d.type==255)return new float[]{1,1,1,0};
        if(d.type==170)return new float[]{200/255f,200/255f,200/255f,100/255f};
        if(d.type==172)return new float[]{250/255f,250/255f,250/255f,150/255f};
        if(d.type==71)return new float[]{200/255f,200/255f,200/255f,0};
        if(d.type==72)return new float[]{200/255f,200/255f,200/255f,200/255f};
        float value=(255-alpha)/255f;if(d.type==73)value=(value+3)/4;
        if(d.type==6||d.type==75||d.type==229||d.type==242)return new float[]{1,1,1,25/255f};
        return new float[]{d.gore&&(d.type==16||d.type==17)?1:value,d.gore&&(d.type==16||d.type==17)?1:value,d.gore&&(d.type==16||d.type==17)?1:value,value};
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||DISPLAY.isEmpty())return;
        var pose=new com.mojang.blaze3d.vertex.PoseStack();
        pose.last().pose().set(e.getPoseStack().last().pose());
        pose.last().normal().set(e.getPoseStack().last().normal());
        Vec3 camera=e.getCamera().getPosition();float partial=e.getPartialTick();
        if(net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat.defer(()->renderParticles(pose,camera,partial),true))return;
        renderParticles(pose,camera,partial);
    }
    private static void renderParticles(com.mojang.blaze3d.vertex.PoseStack pose,Vec3 camera,float partial){
        if(DISPLAY.isEmpty())return;
        var buffers=BUFFERS;double phase=partial*3;int index=Math.min(2,(int)phase);double t=phase-index;
        try {
        for(Dust d:DISPLAY){if(partial<d.born||partial>d.died)continue;State a=d.states[index],b=d.states[index+1];if(a==null||b==null)continue;
            double scale=a.scale+(b.scale-a.scale)*t;float[] color=tint(d,(int)(a.alpha+(b.alpha-a.alpha)*t));
            String texture=d.gore?(d.type==16||d.type==17?"elements/gore_":"old_one/gore_")+d.type:d.type==ASTRAL?"old_one/astral_orange":"old_one/dust";
            int tx=d.gore||d.type==ASTRAL?0:(d.type%100)*10,ty=d.gore?0:d.type==ASTRAL?d.variant*10:(d.type/100)*30+d.variant*10;
            int width=d.gore?NativeSprites.width(texture):8,height=d.gore?NativeSprites.height(texture):8;
            Vec3 at=a.at.lerp(b.at,t);if(d.gore)at=at.add(d.plane.forward().scale(width/32d)).add(d.plane.right().scale(height/32d));double rotation=a.rotation+(b.rotation-a.rotation)*t;
            OldOneRenderer.region(pose,buffers,camera,texture,at,NativeSprites.billboard(),rotation,scale,scale,tx,ty,width,height,color);
            if(!d.gore&&(d.color[0]!=0||d.color[1]!=0||d.color[2]!=0||d.color[3]!=0)){
                float[] overlay=new float[4];for(int i=0;i<4;i++)overlay[i]=Math.max(0,d.color[i]-(1-color[i]));
                OldOneRenderer.region(pose,buffers,camera,texture,at,NativeSprites.billboard(),rotation,scale,scale,tx,ty,width,height,overlay);
            }
        }
        } finally { buffers.endBatch(); }
    }
}
