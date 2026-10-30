package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.everla.projectc.arkofcosmos.calamity.util.ArkCurves;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkFxParticle.Type.*;

/** Calamity particle simulation in original pixels/60 Hz, converted once to world units. */
public final class ArkEffects {
    static final Random rng = new Random();
    public static final float[] WHITE = rgb(255,255,255), CYAN=rgb(0,255,255), ORANGE_RED=rgb(255,69,0),
            CHOCOLATE=rgb(210,105,30), BLUE=rgb(30,144,255), VIOLET=rgb(199,21,133), CORNFLOWER=rgb(100,149,237);
    static final List<ArkFxParticle> particles = new ArrayList<>();
    private static final Map<Integer, Visual> visuals = new HashMap<>();
    private static Object world;
    private static final List<ArkFxParticle> frameParticles=new ArrayList<>();
    private static int phase;
    private static int emittingOwner = -1;
    private static boolean simulating;
    static double shake;
    public static void camera(net.minecraft.client.Camera camera) {
        if(shake<=0||Minecraft.getInstance().isPaused())return;
        // Calamity camera translation uses a random circular offset; one pixel = 1/16 block.
        double angle=random()*Math.PI*2, radius=Math.sqrt(random())*shake/16;
        camera.move(0,Math.sin(angle)*radius,Math.cos(angle)*radius);
    }
    static double random(){ return rng.nextDouble(); }
    public static double range(double a,double b){ return lerp(a,b,random()); }
    static float[] rgba(double r,double g,double b,double a){ return new float[]{(float)r,(float)g,(float)b,(float)a}; }
    public static float[] rgb(int r,int g,int b){ return rgba(r/255d,g/255d,b/255d,1); }
    // XNA Color * scalar multiplies all four components, including RGB.
    public static float[] mul(float[] c,double m){ return rgba(c[0]*m,c[1]*m,c[2]*m,c[3]*m); }
    public static float[] mix(float[] a,float[] b,double t){ t=clamp(t); return rgba(lerp(a[0],b[0],t),lerp(a[1],b[1],t),lerp(a[2],b[2],t),lerp(a[3],b[3],t)); }
    static float[] shift(float[] c,double delta){
        double max=Math.max(c[0],Math.max(c[1],c[2])), min=Math.min(c[0],Math.min(c[1],c[2]));
        double l=(max+min)/2, d=max-min, h=0, s=0;
        if(d>0){ s=l>.5?d/(2-max-min):d/(max+min); h=max==c[0]?(c[1]-c[2])/d+(c[1]<c[2]?6:0):max==c[1]?(c[2]-c[0])/d+2:(c[0]-c[1])/d+4; h/=6; }
        return ArkFxDraw.hsl((float)((h+delta)%1+1)%1,(float)s,(float)l,1);
    }
    public static double lerp(double a,double b,double t){ return a+(b-a)*t; }
    public static double clamp(double x){ return Math.max(0,Math.min(1,x)); }
    static float[] hsl(double h,double l){ return ArkFxDraw.hsl((float)((h%1+1)%1),1,(float)l,1); }
    static Vec3 disk(AttackFrame f,double radius){ return f.direction(random()*Math.PI*2).scale(Math.sqrt(random())*radius/16); }
    static ArkFxParticle add(ArkFxParticle p){ p.ownerId=emittingOwner; particles.add(p); if(simulating){p.born=phase/3d;frameParticles.add(p);} return p; }
    static ArkFxParticle particle(ArkFxParticle.Type t,Vec3 p,Vec3 v,float[] c,double s,int life,AttackFrame f){ return add(new ArkFxParticle(t,p,v,c,s,life,f)); }
    static void smoke(Vec3 p,Vec3 v,float[] c,int life,double s,double opacity,boolean glow,double hue,AttackFrame f){
        ArkFxParticle a=particle(SMOKE,p,v,c,s,life,f); a.opacity=opacity; a.glowing=glow; a.hueShift=hue;
    }
    static void spark(Vec3 p,Vec3 v,float[] c,float[] bloom,double s,int life,double spin,AttackFrame f){
        ArkFxParticle a=particle(CRIT,p,v,c,s,life,f); a.bloom=bloom; a.bloomScale=3; a.spin=spin;
    }
    static void ring(Vec3 p,float[] c,double end,int life,AttackFrame f){
        ArkFxParticle a=particle(RING,p,Vec3.ZERO,c,.05,life,f); a.originalScale=.05; a.finalScale=end;
    }
    static void squish(Vec3 p,Vec3 v,float[] c,double squish,double hue,AttackFrame f){
        ArkFxParticle a=particle(SQUISH,p,v,c,range(.3,.6),60,f); a.squish=squish; a.hueShift=hue;
    }
    public static void receive(CompoundTag data){
        if(data.getString("Event").startsWith("elements_")){ElementsEffects.receive(data);return;}
        if(data.getString("Event").startsWith("galaxia_")){GalaxiaEffects.receive(data);return;}
        int previousOwner = emittingOwner;
        emittingOwner = data.contains("Owner") ? data.getInt("Owner") : -1;
        try { receiveOwned(data); } finally { emittingOwner = previousOwner; }
    }
    private static void receiveOwned(CompoundTag data){
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null)return;
        Vec3 at=ArkAttackEntity.vector(data,"At"), velocity=ArkAttackEntity.vector(data,"Velocity");
        AttackFrame f=new AttackFrame(ArkAttackEntity.vector(data,"F"),ArkAttackEntity.vector(data,"R"));
        int kind=data.getInt("Kind"), mode=data.getInt("Mode"); String event=data.getString("Event");
        if(event.equals("parry")){
            for(int i=0;i<5;i++){
                Vec3 d=disk(f,1); d=f.forward().scale(d.dot(f.forward())*data.getDouble("Height")*1.2*16).add(f.right().scale(d.dot(f.right())*data.getDouble("Width")*2*16));
                double s=range(.5,1.4); ArkFxParticle a=particle(FLARE,at.add(d),d.scale(.01),WHITE,1,20+rng.nextInt(6),f);
                a.bloom=rgb(255,0,0); a.bloomScale=3; a.rotation=a.previousRotation=0; a.originalX=.6*s;a.originalY=s;a.finalX=1.5*s;a.finalY=2.7*s;a.delay=rng.nextInt(7)*2;
            }
        }
        if(event.equals("hit")){
            if(kind==ArkAttackEntity.Kind.BLADE.ordinal()) for(int i=0;i<5;i++){
                Vec3 d=at.subtract(ArkAttackEntity.vector(data,"Position")); double angle=f.angle(d)+range(-Math.PI/5,Math.PI/5);
                squish(at,f.direction(angle).scale(range(3.6,8)/16),ORANGE_RED,2.5,.06,f);
            }
            if(kind==ArkAttackEntity.Kind.NEEDLE.ordinal()||kind==ArkAttackEntity.Kind.PARRY.ordinal()){
                if(kind==ArkAttackEntity.Kind.PARRY.ordinal()){
                    ArkFxParticle a=particle(SPARKLE,at,Vec3.ZERO,WHITE,1.2,35,f); a.bloom=rgb(255,105,180);a.spin=.1;a.bloomScale=2;
                }
                for(int i=0;i<10;i++) squish(at,f.direction(random()*Math.PI*2).scale(range(2.6,4)/16),CYAN,1.5,.02,f);
            }
            if(kind==ArkAttackEntity.Kind.BLAST.ordinal()){
                float[][] colors={rgb(255,165,0),rgb(255,127,80),ORANGE_RED,rgb(255,215,0)};
                ring(at,colors[rng.nextInt(4)],.2+random(),30,f);
                for(int i=0;i<10;i++)squish(at,f.direction(range(-Math.PI/5,Math.PI/5)).scale(range(2.6,4)/16),rgb(255,0,0),1.5,.002,f);
            }
        }
        if(event.equals("death")){
            Visual v=visuals.computeIfAbsent(data.getInt("Id"),id->new Visual());
            var path=data.getList("Samples",10);
            for(int n=1;n<path.size();n++){
                CompoundTag s=path.getCompound(n);if(s.getInt("Steps")<=v.step)continue;
                v.step=s.getInt("Steps");emit(ArkAttackEntity.Kind.values()[kind],f,data,s,v);
            }

            if(kind==ArkAttackEntity.Kind.NEEDLE.ordinal())for(int i=0;i<10;i++)squish(at.add(velocity.normalize().scale(40d/16)),f.direction(random()*Math.PI*2).scale(range(1.2,2.3)/16),CYAN,1.5,.02,f);
            if(kind==ArkAttackEntity.Kind.BLADE.ordinal()&&mode==3){
                Vec3 d=ArkAttackEntity.vector(data,"Direction").normalize().scale(40d/16);
                ArkFxParticle a=particle(LINE,at.subtract(d),Vec3.ZERO,mul(rgb(255,165,0),.7),.2,10,f);a.line=d.scale(2);a.expansion=250;
                if(mc.player!=null&&mc.player.getId()==data.getInt("Owner"))shake=Math.max(shake,3);
            }
        }
    }
    static final class Visual {
        int step=-1,stitches; final double[] stitchAngles=new double[8]; final List<CompoundTag> history=new ArrayList<>();
        final List<ArkFxParticle> chain=new ArrayList<>(); Vec3 anchor; ArkFxParticle head;
    }
    public static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level){world=mc.level;particles.clear();frameParticles.clear();visuals.clear();GalaxiaEffects.clear();shake=0;}
        if(mc.level==null||mc.isPaused())return;
        Set<Integer> live=new HashSet<>();
        frameParticles.clear();frameParticles.addAll(particles);for(ArkFxParticle p:particles)p.beginFrame();
        simulating=true;
        for(phase=0;phase<3;phase++){
            GalaxiaEffects.tick(phase);
            for(var e:mc.level.entitiesForRendering())if(e instanceof ArkAttackEntity a){live.add(a.getId()); consume(a,phase);}
            particles.removeIf(p->{p.prepareFrame();p.update();p.capture(phase+1);if(p.time>=p.lifetime){p.died=(phase+1)/3d;return true;}return false;});
        }
        simulating=false;
        visuals.keySet().removeIf(id->!live.contains(id));
        // Particle handler runs at 60 Hz. Blades emit at 120 Hz, never once per rendered frame.
        
        shake=Math.max(0,shake-.185*3);
    }
    static void galaxiaOwner(int owner,Runnable emit){
        int previous=emittingOwner;emittingOwner=owner;
        try{emit.run();}finally{emittingOwner=previous;}
    }
    static void consume(ArkAttackEntity entity,int phase){
        CompoundTag state=entity.effectSnapshot(); if(state.isEmpty())return;
        Visual v=visuals.computeIfAbsent(entity.getId(),id->new Visual());
        var samples=state.getList("Samples",10);
        int count=samples.size()-1;
        for(int i=1+phase*count/3;i<=((phase+1)*count/3);i++){
            CompoundTag s=samples.getCompound(i); int step=s.getInt("Steps"); if(step<=v.step)continue;
            v.step=step;v.history.add(0,s.copy());int cap=entity.kind()==ArkAttackEntity.Kind.BOLT?20:10;
            if(v.history.size()>cap+7)v.history.remove(v.history.size()-1);
            emit(entity.kind(),entity.attackFrame(),state,s,v);
        }
    }
    static void emit(ArkAttackEntity.Kind kind,AttackFrame f,CompoundTag state,CompoundTag s,Visual v){
        int previousOwner = emittingOwner;
        emittingOwner = state.contains("Owner") ? state.getInt("Owner") : -1;
        try { emitOwned(kind,f,state,s,v); } finally { emittingOwner = previousOwner; }
    }
    private static void emitOwned(ArkAttackEntity.Kind kind,AttackFrame f,CompoundTag state,CompoundTag s,Visual v){
         Vec3 p=ArkAttackEntity.vector(s,"Position"),center=ArkAttackEntity.vector(s,"Center"),ov=ArkAttackEntity.vector(s,"OwnerVelocity"),vel=ArkAttackEntity.vector(s,"Velocity");
        int mode=s.getInt("Mode"),left=s.getInt("Left")+1; double scale=s.getFloat("Scale"),r=s.getFloat("Rotation"),time=(Minecraft.getInstance().level.getGameTime()/20d);
        switch(kind){
            case BLADE -> {
                double completion=(state.getInt("Life")-left)/(double)state.getInt("Life");
                if(mode==2&&left==105)ring(p,ORANGE_RED,1.8,8,f);
                if(mode==3&&left==35){ArkFxParticle a=particle(SPARKLE,p,ov.subtract(vel.normalize().scale(1d/16)),WHITE,range(1,2),10+rng.nextInt(10),f);a.bloom=ORANGE_RED;a.spin=.1;a.bloomScale=3;}
                if(mode==0||mode==3)return;
                if(mode==1)scale=1.6+Math.sin(ArkCurves.swirl((float)completion)*Math.PI)+state.getInt("Charge")/10d*.05;
                boolean swirl=mode==1; double intensity=swirl?clamp(Math.sin((ArkCurves.swirl((float)completion)-.2)*Math.PI)):.7;
                Vec3 origin=swirl?center:p; double radius=scale*78;
                if(rng.nextBoolean()){
                    Vec3 d=disk(f,radius); Vec3 tangent=f.direction(f.angle(d)+(swirl?1:-1)*Math.PI/2).scale(2*(1+d.length()*16/15)/16);
                    spark(origin.add(d),ov.add(tangent),swirl?(rng.nextInt(3)==0?rgb(64,224,208):rgb(255,127,80)):WHITE,swirl?mul(CHOCOLATE,intensity*.8):hsl(random(),.5),1+d.length()*16/radius,10,.05,f);
                }
                if(rng.nextBoolean())for(double t=swirl?0:.5;t<=1;t+=.5){
                    Vec3 at=origin.add(f.direction(r).scale((swirl?30+50*t:60*t)*scale/16)).add(f.direction(r-Math.PI/2).scale(30*intensity*random()/16));
                    Vec3 speed=f.direction(r+(swirl?-Math.PI/2:Math.PI/2)).scale(20*intensity/16).add(ov);
                    double opacity=swirl?clamp(intensity*2)*.25:.25;
                    smoke(at,speed,mix(BLUE,VIOLET,t),(swirl?6:10)+rng.nextInt(5),intensity*range(2.8,3.1),opacity+range(0,.2),false,0,f);
                    if(rng.nextInt(3)==0)smoke(at,speed,rng.nextInt(5)==0?rgb(255,215,0):CHOCOLATE,swirl?5:7,intensity*range(2,2.4),opacity*2.5,true,.004,f);
                }
            }
            case NEEDLE,BOLT -> {
                boolean needle=kind==ArkAttackEntity.Kind.NEEDLE;
                p=p.subtract(vel); // Terraria AI emits before ShouldUpdatePosition advances the projectile.
                if(rng.nextBoolean()){
                    smoke(p,vel.scale(.5),mix(BLUE,VIOLET,Math.sin(time*6)),needle?30:20,range(.6,1.2)*scale*(needle?.6:1),.28,false,0,f);
                    if(rng.nextInt(3)==0)smoke(p,vel.scale(.5),hsl(needle?random():.55,.7),needle?20:15,range(.4,.7)*scale*(needle?.6:1),.8,true,.05,f);
                }
                if(!needle){
                    if(v.head==null){v.head=particle(SPARKLE,p,Vec3.ZERO,WHITE,1.2,2,f);v.head.bloom=ArkFxDraw.hsl(.55f,100,50,1);v.head.bloomScale=3;v.head.spin=.06;}
                    else {v.head.previous=v.head.position;v.head.position=p.add(vel.scale(.5));v.head.time=0;v.head.scale+=Math.sin(time*6)*.02*scale;if(!particles.contains(v.head))add(v.head);}
                }
            }
            case CHAIN -> constellation(s,v,f,p,ArkAttackEntity.vector(s,"Aim").subtract(p));
            case BLAST -> {
                double timer=70-left,snap=clamp(timer/25),hold=clamp((timer-25)/15),stitch=clamp((timer-32.5)/37.5),length=ArkCurves.blastThrust((float)snap)*242;
                if(hold<=.4){
                    if(v.head==null){v.head=particle(SPARKLE,p,Vec3.ZERO,WHITE,scale*2,2,f);v.head.bloom=CORNFLOWER;v.head.bloomScale=5;v.head.spin=.1;}
                    v.head.previous=v.head.position;v.head.position=ArkAttackEntity.vector(s,"Aim");v.head.scale=scale*2;v.head.time=0;if(!particles.contains(v.head))add(v.head);
                }
                int count=(int)Math.ceil((1-Math.sqrt(1-Math.pow(clamp(stitch*3),2)))*8);
                while(v.stitches<count){v.stitchAngles[v.stitches]=range(-Math.PI/4,Math.PI/4)+Math.PI/2;spark(p.add(f.forward().scale(length/16*(.5+v.stitches)/8)),Vec3.ZERO,WHITE,CYAN,3,8,.1,f);v.stitches++;}
                if(timer==26)for(int i=0;i<20;i++){
                    double t=random(),size=range(.05,.4)*(.4+.6*Math.sin(t*Math.PI));Vec3 at=p.add(f.forward().scale(length*t/16));
                    float[] c=rng.nextBoolean()?ORANGE_RED:rng.nextBoolean()?WHITE:rgb(255,165,0);int type=rng.nextInt(3),life=10+rng.nextInt(20);
                    if(type==2)spark(at,f.right().scale(range(-10,-1)/16),WHITE,c,size*7,life,.1,f);
                    else particle(type==0?BLOOM:LIGHT,at,f.right().scale(range(-4,-1)/16),c,size,life,f);
                }
            }
            default -> { }
        }
    }
    static void constellation(CompoundTag s,Visual v,AttackFrame f,Vec3 start,Vec3 size){
        if(size.length()>ArkCombat.MAX_REACH)size=size.normalize().scale(ArkCombat.MAX_REACH);
        if((s.getInt("Steps")-1)%15==0&&s.getInt("Left")>=19){
            for(ArkFxParticle old:v.chain)old.died=phase/3d;particles.removeAll(v.chain);v.chain.clear();double hue=random();Vec3 previous=start;
            chainStar(v,start,rgb(221,160,221),f);
            Vec3 across=f.normal().cross(size.normalize()).normalize();
            for(double t=range(.2,.5);t<1;t+=range(.2,.5)){
                hue=(hue+.16)%1;Vec3 next=start.add(size.scale(t)).add(across.scale(range(-50,50)/16));float[] color=hsl(hue,.8);
                chainStar(v,next,color,f);chainLine(v,previous,next,mul(color,.75),f);
                if(rng.nextInt(3)==0){hue=(hue+.16)%1;next=start.add(size.scale(t)).add(across.scale(range(-50,50)/16));color=hsl(hue,.8);chainStar(v,next,color,f);chainLine(v,previous,next,color,f);}
                previous=next;
            }
            hue=(hue+.16)%1;chainStar(v,start.add(size),hsl(hue,.8),f);chainLine(v,previous,start.add(size),mul(hsl(hue,.8),.75),f);
        }
        Vec3 move=v.anchor==null?Vec3.ZERO:start.subtract(v.anchor);v.anchor=start;
        for(ArkFxParticle a:v.chain){a.previous=a.previous.add(move);a.position=a.position.add(move);a.color=shift(a.color,.02);}
    }
    static void chainStar(Visual v,Vec3 p,float[] bloom,AttackFrame f){ArkFxParticle a=particle(SPARKLE,p,Vec3.ZERO,WHITE,range(1,1.5),20,f);a.bloom=bloom;a.bloomScale=3;v.chain.add(a);}
    static void chainLine(Visual v,Vec3 from,Vec3 to,float[] c,AttackFrame f){ArkFxParticle a=particle(BLOOM_LINE,from,Vec3.ZERO,c,.8,20,f);a.line=to.subtract(from);a.capped=true;v.chain.add(a);}
    static Vec3[] trail(ArkAttackEntity e,float partial){
        Visual v=visuals.get(e.getId()); if(v==null||v.history.isEmpty())return new Vec3[0];
        int cap=e.kind()==ArkAttackEntity.Kind.BOLT?20:10;List<Vec3> result=new ArrayList<>();
        int substeps=e.kind()==ArkAttackEntity.Kind.BLADE?6:3;
        double offset=(1-partial)*substeps;
        for(int i=0;i<cap;i++){
            double index=i+offset;int n=(int)index;if(n+1>=v.history.size())break;
            result.add(ArkAttackEntity.vector(v.history.get(n),"Position").lerp(ArkAttackEntity.vector(v.history.get(n+1),"Position"),index-n));
        }
        return result.toArray(Vec3[]::new);
    }
    static double stitchAngle(ArkAttackEntity entity,int i){Visual v=visuals.get(entity.getId());return v==null?0:v.stitchAngles[i];}
    static float[] rotations(ArkAttackEntity e,float partial){
        Visual v=visuals.get(e.getId());if(v==null)return new float[0];
        int start=(int)((1-partial)*6);int count=Math.min(10,v.history.size()-start-1);if(count<=0)return new float[0];float[] out=new float[count];
        for(int i=0;i<count;i++)out[i]=(float)lerp(v.history.get(start+i).getFloat("Rotation"),v.history.get(start+i+1).getFloat("Rotation"),(1-partial)*6-start);return out;
    }
    public static void render(PoseStack pose,Vec3 camera,float partial){
        Camera c=Minecraft.getInstance().gameRenderer.getMainCamera();var look=c.getLookVector();var u=c.getUpVector();
        Vec3 forward=new Vec3(look.x(),look.y(),look.z()),up=new Vec3(u.x(),u.y(),u.z());
        AttackFrame billboard=new AttackFrame(forward.cross(up).normalize(),up.scale(-1));
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=camera;
        try {
            for(boolean additive:new boolean[]{false,true})for(ArkFxParticle p:frameParticles)if((p.type!=SMOKE||p.glowing)==additive){
                ArkFxDraw.ownerView(p.ownerId,Vec3.ZERO);
                p.draw(partial,billboard);
            }
        } finally { ArkFxDraw.fadeEye = null; }
    }
}
