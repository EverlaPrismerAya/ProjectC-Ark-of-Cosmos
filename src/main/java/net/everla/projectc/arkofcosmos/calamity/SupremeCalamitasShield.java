package net.everla.projectc.arkofcosmos.calamity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Source-frame shield state. Native dimensions consume the pre-lerp integer source hitbox. */
public final class SupremeCalamitasShield {
    private float shieldOpacity=1, scale=1, opacity=1, visualScale=1;
    private int music=2082;
    private int hitboxWidth=44, hitboxHeight=44;
    private boolean postMusic, preparing, charging, willCharge;
    private Vec3 direction=new Vec3(1,0,0), toward=new Vec3(1,0,0), away=new Vec3(1,0,0);
    public void step(SupremeCalamitasEncounter encounter, SupremeCalamitasCombat combat, float lifeRatio, Vec3 aim, Vec3 planeX) {
        beginFrame();
        var phase=encounter.phase(); charging=phase==SupremeCalamitasEncounter.Phase.COMBAT && combat.mode()==2; willCharge=combat.willCharge();
        boolean casting=phase==SupremeCalamitasEncounter.Phase.HEART_CAST || phase==SupremeCalamitasEncounter.Phase.BROTHERS_CAST;
        int length=switch(combat.mode()) {case 0,4->encounter.secondStage()?240:300;case 2->70;case 3->encounter.secondStage()?300:480;default->0;};
        boolean nearEnd=combat.elapsed()>=length-30;
        boolean blocked=casting || phase==SupremeCalamitasEncounter.Phase.BROTHERS || phase==SupremeCalamitasEncounter.Phase.BROTHERS_WARNING
                || phase==SupremeCalamitasEncounter.Phase.TRANSITION
                || phase==SupremeCalamitasEncounter.Phase.BARRAGE && encounter.phaseFrame()!=0;
        preparing=combat.willCharge() && !charging && nearEnd && !blocked;
        Vec3 target=aim.lengthSqr()<1e-12?new Vec3(0,-1,0):aim.normalize();
        Vec3 normal=planeX.cross(new Vec3(0,-1,0)).normalize();
        Vec3 perpendicular=normal.cross(target).add(normal.scale(normal.dot(target))).normalize();
        toward=turn(toward,perpendicular,.04,0); away=turn(away,perpendicular.scale(-1),.04,0);
        if(phase==SupremeCalamitasEncounter.Phase.ACCEPTANCE || phase==SupremeCalamitasEncounter.Phase.FINISHED) {
            shieldOpacity=lerp(shieldOpacity,0,.08f);scale=lerp(scale,0,.08f);
        } else if((preparing || charging) && !blocked) {
            if(!charging)direction=turn(direction,target,.125,.18);
            scale=lerp(scale,.45f,.08f);shieldOpacity=lerp(shieldOpacity,1,.08f);
        } else {shieldOpacity=lerp(shieldOpacity,0,.08f);scale=lerp(scale,1,.08f);}
        // Source early cast/first-three-barrage returns precede the music counter.
        boolean musicReached=!casting && !(phase==SupremeCalamitasEncounter.Phase.BARRAGE && encounter.round()<=3);
        if(musicReached) {
            if(lifeRatio<=.3f && music>-120)music--;
            if(music<=126 && music>0) {opacity=lerp(opacity,.4f,.06f);shieldOpacity=lerp(shieldOpacity,0,.065f);}
            if(music<=42 && music>0)visualScale=lerp(visualScale,.45f,.095f);
            if(music==0 && !postMusic) {opacity=.7f;visualScale=1.5f;postMusic=true;}
            if(postMusic && phase!=SupremeCalamitasEncounter.Phase.ACCEPTANCE && !charging)visualScale=lerp(visualScale,1,.095f);
        }
        if(lifeRatio<.05f)opacity=.75f;
        if(lifeRatio<=.01f)opacity=.6f;
    }
    public void lockDash(Vec3 velocity) { if(velocity.lengthSqr()>1e-12)direction=velocity.normalize();charging=true;preparing=false; }
    public void despawn(float npcOpacity) {opacity=Math.max(0,Math.min(1,(npcOpacity-.1f)/.5f));}
    public void beginFrame() {hitboxWidth=(int)Math.max(42,scale*216f/1.4142f);hitboxHeight=(int)Math.max(44,scale*216f/1.4142f);}
    public int hitboxWidth() {return hitboxWidth;}
    public int hitboxHeight() {return hitboxHeight;}
    private static float lerp(float from,float to,float amount){return from+(to-from)*amount;}
    private static Vec3 turn(Vec3 from,Vec3 to,double fraction,double fixed) {
        double angle=Math.acos(Math.max(-1,Math.min(1,from.dot(to))));if(angle<1e-8)return to;
        // AngleLerp, then AngleTowards, preserving source .04-radian dead zone for the skull.
        if(fixed>0 && angle<=.04)return from;
        Vec3 axis=from.cross(to);
        if(axis.lengthSqr()<1e-12)axis=from.cross(Math.abs(from.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0));
        axis=axis.normalize();double step=Math.min(angle,angle*fraction+fixed);
        return from.scale(Math.cos(step)).add(axis.cross(from).scale(Math.sin(step))).add(axis.scale(axis.dot(from)*(1-Math.cos(step)))).normalize();
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putFloat("ShieldOpacity",shieldOpacity);tag.putFloat("Scale",scale);tag.putFloat("Opacity",opacity);tag.putFloat("VisualScale",visualScale);
        tag.putInt("Music",music);tag.putBoolean("PostMusic",postMusic);tag.putBoolean("Preparing",preparing);tag.putBoolean("Charging",charging);tag.putBoolean("WillCharge",willCharge);
        tag.putInt("HitboxWidth",hitboxWidth);tag.putInt("HitboxHeight",hitboxHeight);
        put(tag,"Direction",direction);put(tag,"Toward",toward);put(tag,"Away",away);return tag;
    }
    private static void put(CompoundTag tag,String key,Vec3 vector){tag.putDouble(key+"X",vector.x);tag.putDouble(key+"Y",vector.y);tag.putDouble(key+"Z",vector.z);}
    private static Vec3 vector(CompoundTag tag,String key){Vec3 v=new Vec3(tag.getDouble(key+"X"),tag.getDouble(key+"Y"),tag.getDouble(key+"Z"));if(!Double.isFinite(v.lengthSqr()) || Math.abs(v.lengthSqr()-1)>1e-6)throw new IllegalArgumentException("Invalid shield direction");return v;}
    public void load(CompoundTag tag) {
        if(tag.isEmpty())return;
        float s=tag.getFloat("ShieldOpacity"),z=tag.getFloat("Scale"),o=tag.getFloat("Opacity"),v=tag.getFloat("VisualScale");int m=tag.getInt("Music");
        if(!Float.isFinite(s)||s<0||s>1||!Float.isFinite(z)||z<0||z>1||!Float.isFinite(o)||o<0||o>1||!Float.isFinite(v)||v<0||v>1.5||m< -120||m>2082)throw new IllegalArgumentException("Invalid shield state");
        Vec3 d=vector(tag,"Direction"),t=vector(tag,"Toward"),a=vector(tag,"Away");
        int w=tag.contains("HitboxWidth")?tag.getInt("HitboxWidth"):(int)Math.max(42,z*216f/1.4142f);
        int h=tag.contains("HitboxHeight")?tag.getInt("HitboxHeight"):(int)Math.max(44,z*216f/1.4142f);
        if(w<42||w>152||h<44||h>152)throw new IllegalArgumentException("Invalid shield hitbox");
        hitboxWidth=w;hitboxHeight=h;
        shieldOpacity=s;scale=z;opacity=o;visualScale=v;music=m;postMusic=tag.getBoolean("PostMusic");preparing=tag.getBoolean("Preparing");charging=tag.getBoolean("Charging");willCharge=tag.getBoolean("WillCharge");direction=d;toward=t;away=a;
    }
}
