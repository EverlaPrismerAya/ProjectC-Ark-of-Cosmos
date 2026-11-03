package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.AncientsEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkFxParticle.Type.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Ark of the Ancients source emitters. Shared particle coordinator supplies three 60 Hz steps per tick. */
public final class AncientsEffects {
    private static final List<ArkFxParticle> LIVE = new ArrayList<>(), FRAME = new ArrayList<>();
    private static final List<CompoundTag> TERMINAL = new ArrayList<>();
    private static final Map<UUID, Visual> VISUALS = new HashMap<>();
    // Spatial conversion must not change source emission probability or speed distributions.
    private static final Random SPATIAL = new Random();
    private static final class Visual { int seen; long touched; final Set<String> events = new HashSet<>(); int parriedAt = -1; }
    private static int phase;
    private static Object world;
    private static void resetWorld() { var level=Minecraft.getInstance().level;if(world!=level){world=level;clear();} }
    public static void clear() { LIVE.clear(); FRAME.clear(); TERMINAL.clear(); VISUALS.clear(); }
    private static Visual visual(CompoundTag s) {
        Visual v = VISUALS.computeIfAbsent(s.getUUID("Attack"), k -> new Visual());
        v.touched = Minecraft.getInstance().level.getGameTime(); return v;
    }
    private static AttackFrame frame(CompoundTag s) { return new AttackFrame(vector(s,"F"), vector(s,"R")); }
    static Vec3 sphere(double pixels, Random spatial) {
        double z = spatial.nextDouble()*2-1, a = spatial.nextDouble()*Math.PI*2, radius = Math.sqrt(Math.max(0,1-z*z));
        return new Vec3(radius*Math.cos(a), z, radius*Math.sin(a)).scale(pixels/16);
    }
    private static ArkFxParticle add(ArkFxParticle.Type type, CompoundTag s, Vec3 at, Vec3 velocity, double scale, int life) {
        ArkFxParticle p = new ArkFxParticle(type,at,velocity,WHITE,scale,life,frame(s));
        p.ownerId=s.getInt("Owner"); p.born=phase/3d; LIVE.add(p); FRAME.add(p); return p;
    }
    private static void squish(CompoundTag s, Vec3 at, double low, double high) {
        for(int i=0;i<10;i++) {
            ArkFxParticle p=add(SQUISH,s,at,sphere(range(low,high),SPATIAL),range(.3,.6),60);
            p.color=CYAN.clone(); p.bloom=p.color; p.squish=1.5; p.hueShift=.02;
        }
    }
    public static void receive(CompoundTag s) {
        if(s.getString("Event").equals("ancients_sound")){ElementsAudio.receive(s);return;}
        resetWorld();if(Minecraft.getInstance().level==null || !s.hasUUID("Attack"))return;
        Visual v=visual(s); String event=s.getString("Event");
        if(!v.events.add(event+(s.hasUUID("Target")?s.getUUID("Target").toString():"")))return;
        if(event.equals("ancients_parry")) {v.parriedAt=s.getInt("ParriedAt");return;}
        // Process discrete effects on the same 60 Hz clock as continuous samples.
        TERMINAL.add(s.copy());
    }
    public static int parriedAt(CompoundTag s) {
        Visual v=s.hasUUID("Attack")?VISUALS.get(s.getUUID("Attack")):null;
        return v!=null&&v.parriedAt>=0?v.parriedAt:s.getInt("ParriedAt");
    }
    public static void beginFrame() {
        resetWorld();FRAME.clear();FRAME.addAll(LIVE);for(ArkFxParticle p:FRAME)p.beginFrame();
    }
    public static void tick(int step) {
        phase=step-1;var mc=Minecraft.getInstance();
        for(var e:mc.level.entitiesForRendering())if(e instanceof AncientsEntity a) consume(a.view(),phase);
        for(CompoundTag s:TERMINAL)if(s.getString("Event").equals("ancients_end"))consume(s,phase);
        for(CompoundTag s:TERMINAL) {
            if(step!=3)continue; // Last source samples precede hit/end bursts, including immediate entity removal.
            Vec3 at=vector(s,"At");int kind=s.getInt("Kind");
            if(s.getString("Event").equals("ancients_hit")) {
                if(kind==AncientsEntity.STAR)continue;
                if(kind==AncientsEntity.PARRY) {
                    ArkFxParticle p=add(SPARKLE,s,at,Vec3.ZERO,1.2,35);p.bloom=rgb(255,105,180);p.spin=.1;p.bloomScale=2;
                }
                if(kind==AncientsEntity.BLAST){
                    float[][] colors={rgb(100,149,237),rgb(255,127,80),rgb(173,255,47),rgb(255,215,0)};
                    ArkFxParticle ring=add(RING,s,at,Vec3.ZERO,.05,30);ring.color=colors[rng.nextInt(4)];ring.bloom=ring.color.clone();ring.originalScale=.05;ring.finalScale=.2+random();
                    for(int i=0;i<10;i++){var f=frame(s);Vec3 direction=vector(s,"Velocity").normalize();ArkFxParticle p=add(SQUISH,s,at,f.direction(f.angle(direction)+range(-Math.PI/5,Math.PI/5)).scale(range(2.6,4)/16),range(.3,.6),60);p.color=CYAN.clone();p.bloom=CYAN.clone();p.squish=1.5;p.hueShift=.02;}
                }else squish(s,at,2.6,4);
            } else if(kind==AncientsEntity.STAR) {
                for(int i=0;i<5;i++){ArkFxParticle p=add(CRIT,s,at,sphere(range(4.3,5.2),SPATIAL),range(1.3,1.6),40);p.bloom=CYAN.clone();p.spin=.1;p.bloomScale=3.5;p.hueShift=.02;}
                ElementsVanillaParticles.ancientDeath(s);
            } else if(kind==AncientsEntity.BEAM) squish(s,at.add(vector(s,"Velocity").normalize().scale(40/16d)),1.2,2.3);
        }
        if(step==3)TERMINAL.clear();
        for(var it=LIVE.iterator();it.hasNext();) {
            ArkFxParticle p=it.next();p.prepareFrame();p.update();p.capture(step);
            if(p.time>=p.lifetime){p.died=step/3d;it.remove();}
        }
        if(step==3)VISUALS.values().removeIf(v->mc.level.getGameTime()-v.touched>200);
    }
    private static void consume(CompoundTag state,int phase) {
        if(!state.hasUUID("Attack"))return;Visual v=visual(state);var samples=state.getList("Samples",10);int count=samples.size()-1;
        for(int i=1+phase*Math.max(0,count)/3;i<=((phase+1)*Math.max(0,count)/3);i++) {
            CompoundTag s=samples.getCompound(i);int age=s.getInt("Age");if(age<=v.seen)continue;v.seen=age;
            int kind=s.getInt("Kind");AttackFrame f=frame(s);
            if(kind==AncientsEntity.BLADE && s.getInt("Charge")>0 && rng.nextBoolean()) {
                double rotation=s.getDouble("Rotation");
                Vec3 at=vector(s,"Position").add(f.direction(rotation).scale(85*s.getDouble("Scale")/16));
                Vec3 velocity=f.direction(rotation+range(-Math.PI/4,Math.PI/4)).scale(range(1.2,2)/16).add(vector(s,"OwnerVelocity"));
                ArkFxParticle p=add(CRIT,s,at,velocity,range(.6,1.6),20+rng.nextInt(10));
                p.bloom=CYAN.clone();p.spin=.1;p.bloomScale=1.5;p.hueShift=.02;
            }
            if(kind==AncientsEntity.BEAM)ElementsVanillaParticles.ancientBeam(s,phase+1);
            if(kind==AncientsEntity.STAR) {
                ElementsVanillaParticles.ancientStar(s,phase+1);
                if(age==1){
                    ArkFxParticle p=add(CRIT,s,vector(s,"Position"),Vec3.ZERO,range(2.3,2.6),20);p.bloom=CYAN.clone();p.spin=.1;p.bloomScale=1.5;p.hueShift=.02;
                    for(int j=0;j<4;j++){Vec3 v0=vector(s,"Velocity");ArkFxParticle q=add(SQUISH,s,vector(s,"Position"),f.direction(f.angle(v0)+range(-Math.PI/5,Math.PI/5)).scale(v0.length()*.1),range(.8,1.4),60);q.color=CYAN.clone();q.bloom=CYAN.clone();q.opacity=.3;q.squish=1.5;q.hueShift=.02;}
                }
            }
            if(kind==AncientsEntity.BLAST&&age==1) for(int end=0;end<2;end++){
                ArkFxParticle p=add(SPARKLE,s,vector(s,"Position").add(vector(s,"Velocity").scale(end)),Vec3.ZERO,range(2,2.5),20);p.bloom=end==0?rgb(255,105,180):rgb(238,232,170);p.spin=.1;p.bloomScale=3;
            }
        }
    }
    public static void render(float partial, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 camera) {
        AttackFrame face=NativeSprites.billboard();
        for(ArkFxParticle p:FRAME)p.drawNative(partial,new NativeSprites.Draw(pose,buffers,camera,p.ownerId),face);
    }
}
