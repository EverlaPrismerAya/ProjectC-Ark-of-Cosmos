package net.everla.projectc.arkofcosmos.calamity.client;

import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.FracturedEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkFxParticle.Type.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Fractured Ark source emitters. Shared particle coordinator supplies three 60 Hz steps per tick. */
public final class FracturedEffects {
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
        if(s.getString("Event").equals("fractured_sound")){ElementsAudio.receive(s);return;}
        resetWorld();if(Minecraft.getInstance().level==null || !s.hasUUID("Attack"))return;
        Visual v=visual(s); String event=s.getString("Event");
        if(!v.events.add(event))return;
        if(event.equals("fractured_parry")) {v.parriedAt=s.getInt("ParriedAt");return;}
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
        for(var e:mc.level.entitiesForRendering())if(e instanceof FracturedEntity a) consume(a.view(),phase);
        for(CompoundTag s:TERMINAL)if(s.getString("Event").equals("fractured_end"))consume(s,phase);
        for(CompoundTag s:TERMINAL) {
            if(step!=3)continue; // Last source samples precede hit/end bursts, including immediate entity removal.
            Vec3 at=vector(s,"At");int kind=s.getInt("Kind");
            if(s.getString("Event").equals("fractured_hit")) {
                if(kind==FracturedEntity.PARRY) {
                    ArkFxParticle p=add(SPARKLE,s,at,Vec3.ZERO,1.2,35);p.bloom=rgb(255,105,180);p.spin=.1;p.bloomScale=2;
                }
                squish(s,at,2.6,4);
            } else if(kind==FracturedEntity.BEAM) squish(s,at.add(vector(s,"Velocity").normalize().scale(40/16d)),1.2,2.3);
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
            if(kind==FracturedEntity.BLADE && s.getInt("Charge")>0 && rng.nextBoolean()) {
                double rotation=s.getDouble("Rotation");
                Vec3 at=vector(s,"Position").add(f.direction(rotation).scale(75*s.getDouble("Scale")/16));
                Vec3 velocity=f.direction(rotation+range(-Math.PI/4,Math.PI/4)).scale(range(1.2,2)/16).add(vector(s,"OwnerVelocity"));
                ArkFxParticle p=add(CRIT,s,at,velocity,range(.6,1.6),20+rng.nextInt(10));
                p.bloom=CYAN.clone();p.spin=.1;p.bloomScale=1.5;p.hueShift=.02;
            }
            if(kind==FracturedEntity.BEAM)ElementsVanillaParticles.fractured(s,phase+1);
        }
    }
    public static void render(float partial) {
        AttackFrame face=NativeSprites.billboard();
        for(ArkFxParticle p:FRAME){ArkFxDraw.ownerView(p.ownerId,Vec3.ZERO);p.draw(partial,face);}
    }
}
