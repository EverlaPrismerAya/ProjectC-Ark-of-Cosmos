package net.everla.projectc.arkofcosmos.entity;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import static net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter.Attack.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Infernum movement/collision with weapon-only presentation. No player weapon AI runs here. */
public final class MahouInfernumAttackEntity extends Entity {
    public enum Kind { DART, FINAL_DART, DARK_SOUL, REDIRECT_SOUL, ARROW, HELLBLAST, FLAME, BOMB, GIGA, EXPLOSION, WARNING, BOMB_WARNING, PILLAR_WARNING, PILLAR, JEWEL_BEAM, OVERLOAD_BEAM, ORB, RITUAL, HEART_STAR, BONE, SOUL_BOMB, SHADOW_BLAST, SHADOW_GIGA, SHADOW_BOLT, SHADOW_SPARK, FIST, SLASH, LOST_SOUL, REDIRECT_HELLFIRE }
    public static final int OVERLOAD_WARNING=1;
    static final int FINALE_WARNING=2,BROTHER_WARNING=3;
    static final double OVERLOAD_INITIAL_TURN=.006;
    private static final double BOMB_DRAG=.99,BOMB_BLAST_SCALE=.7,BLAST_HIT_SCALE=.8,BLAST_GROWTH=.1;
    private static final int BOMB_BLAST_LIFE=84;
    private static final double BOMB_PEAK_HIT_SCALE=bombPeakHitScale();
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(MahouInfernumAttackEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final net.minecraft.nbt.ListTag samples=new net.minecraft.nbt.ListTag();
    private UUID owner,anchor;
    private Kind kind=Kind.DART;
    private Vec3 velocity=Vec3.ZERO,plane=new Vec3(1,0,0),start=Vec3.ZERO;
    private int age,remaining,parameter;
    private float damage;
    private double radius,pixels=16,beamLength;
    private boolean tail,exploded,splitChild;
    private long spawnedAt,arenaFade=-1;
    public MahouInfernumAttackEntity(EntityType<? extends MahouInfernumAttackEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    static MahouInfernumAttackEntity spawn(MahouTsukaiEntity boss,Kind kind,Vec3 at,Vec3 velocity,Vec3 plane,int life,float damage,double radius,boolean tail){
        var e=new MahouInfernumAttackEntity(ArkOfCosmos.MAHOU_INFERNUM_ATTACK.get(),boss.level());
        e.owner=boss.getUUID();e.spawnedAt=boss.level().getGameTime();e.kind=kind;e.setPos(at);e.start=at;e.velocity=velocity;e.plane=plane;e.remaining=life;e.damage=damage;e.radius=radius;e.tail=tail;e.pixels=boss.infernum().pixels();
        if(kind==Kind.EXPLOSION){e.start=new Vec3(radius,0,0);e.radius=0;}
        var journal=CalamitasBattleData.get((ServerLevel)boss.level());journal.begin(e.owner);
        if(journal.add(e.owner,e.getUUID(),"attack")&&boss.level().addFreshEntity(e))e.publish();else {journal.forget(e.owner,e.getUUID());e.discard();}
        return e;
    }
    public boolean ownedBy(MahouTsukaiEntity boss){return boss.getUUID().equals(owner);}
    public boolean tail(){return tail;}
    public Kind kind(){return level().isClientSide&&!view().isEmpty()?Kind.valueOf(view().getString("Kind")):kind;}
    public CompoundTag view(){return entityData.get(VIEW);}
    void point(Vec3 at,Vec3 direction,Vec3 attackPlane){setPos(at);velocity=direction;plane=attackPlane;}
    void parameter(int n){parameter=n;publish();}
    void followSummon(UUID summon){anchor=summon;}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<512*512;}
    @Override public void tick(){
        super.tick();
        if(level() instanceof ServerLevel server&&owner!=null){var data=CalamitasBattleData.get(server);if(!data.active(owner)||data.confirmedDead(owner,getUUID()))retire();}
    }
    void sourceStep(MahouTsukaiEntity boss){
        if(isRemoved()||boss.getTarget()==null)return;
        int updates=kind==Kind.EXPLOSION||kind==Kind.PILLAR||kind==Kind.SLASH?2:1;
        for(int update=0;update<updates&&!isRemoved()&&boss.getTarget()!=null;update++)advance(boss);
    }
    private void advance(MahouTsukaiEntity boss){
        age++;
        if(arenaFade>=0){setPos(position().add(velocity));if(level().getGameTime()-arenaFade>=5)retire();else publish();return;}
        Vec3 before=position();var ai=boss.infernum();Vec3 target=boss.getTarget().getBoundingBox().getCenter();
        switch(kind){
            case ARROW->velocity=velocity.scale(1.025);
            case HELLBLAST->{if(velocity.length()<18/pixels)velocity=velocity.scale(1.03);}
            case FIST->velocity=velocity.multiply(1,0,1).add(0,-Math.sin((age-1)/5d)*5/pixels,0);
            case FLAME->{if(velocity.length()<16/pixels)velocity=velocity.scale(1.019);}
            case DART->{if(velocity.length()<(parameter==1?10:14)/pixels)velocity=velocity.scale(1.01);if(tail&&ai.finalState==2&&velocity.length()>7.6/pixels)velocity=velocity.normalize().scale(7.6/pixels);}
            case FINAL_DART->velocity=velocity.scale(1.01).normalize().scale(Math.min(7.6/pixels,velocity.length()*1.01));
            case SHADOW_BLAST->velocity=velocity.scale(.995);
            case SHADOW_SPARK->{if(velocity.length()<20/pixels)velocity=velocity.scale(1.016);}
            case SHADOW_BOLT->velocity=velocity.scale(.99);
            case BONE->{if(velocity.length()<34/pixels)velocity=velocity.scale(1.03);}
            case LOST_SOUL->{if(velocity.length()<33/pixels)velocity=velocity.scale(1.03);}
            case ORB->{double t=Math.max(0,Math.min(1,(age-25)/45d));t=t*t*(3-2*t);radius=(12+513*t)/pixels*(1-Math.max(0,Math.min(1,(age-515)/60d)));}
            case SOUL_BOMB->{
                radius+=(500/pixels-radius)*.02;
                if(parameter>0){if(--parameter<18)radius+=(25/pixels-radius)*.15;if(parameter==0){expire(boss);return;}}
                else if(anchor!=null && ((ServerLevel)level()).getEntity(anchor) instanceof MahouInfernumSummonEntity head && head.isAlive())
                    setPos(head.position().add(ai.unit(head.sourceVelocity()).scale((Math.pow(radius*pixels/500,2.3)*460+45)/pixels)));
                else {retire();return;}
            }
            case DARK_SOUL->{double h=velocity.dot(plane);if(Math.abs(h)<17.5/pixels)velocity=velocity.add(plane.scale(h*.03));velocity=velocity.multiply(1,.99,1);}
            case REDIRECT_SOUL->{
                if(remaining>140)velocity=rotate(velocity,Math.PI*Math.cos(getId()%6/6d+position().dot(plane)*pixels/380-getY()*pixels/160)/120).scale(.98);
                else if(remaining>30){double a=.056+(.12-.056)*Math.max(0,Math.min(1,(140-remaining)/110d));a=a*a*(3-2*a);velocity=velocity.lerp(ai.unit(target.subtract(position())).scale(41*.6/pixels),a);}}
            case BOMB->velocity=velocity.scale(BOMB_DRAG);
            case WARNING->{if(!updateWarning(ai,target)){retire();return;}}
            case GIGA,SHADOW_GIGA->{double speed=velocity.length();velocity=ai.unit(velocity.scale(24).add(ai.unit(target.subtract(position())).scale(speed))).scale(speed);}
            case REDIRECT_HELLFIRE->{
                if(ai.living(MahouInfernumSummonEntity.Kind.CATACLYSM)==0){retire();return;}
                double speed=velocity.length();
                if(age<=30&&speed<15/pixels)velocity=velocity.scale(1.012);
                if(age>30&&age<=100&&position().distanceTo(target)>380/pixels)
                    velocity=ai.unit(velocity.lerp(ai.unit(target.subtract(position())).scale(speed),.09)).scale(speed);
                if(age>100&&speed<37/pixels)velocity=velocity.scale(1.028);
            }
            case EXPLOSION->radius+=(start.x-radius)*BLAST_GROWTH;
            case JEWEL_BEAM->{if(age>1)velocity=rotate(velocity,radius);}
            case OVERLOAD_BEAM->{double a=OVERLOAD_INITIAL_TURN+(.0174-OVERLOAD_INITIAL_TURN)*Math.pow(Math.max(0,Math.min(1,(age-1-16)/134d)),1.75);velocity=rotate(velocity,a);}
            case HEART_STAR->{double t=ai.timer-(ai.shortRitual?30:60),spin=ai.shortRitual?60:150,focusTime=ai.shortRitual?38:92;
                double fade=Math.max(0,Math.min(1,(spin+focusTime+90-t)/45)),focus=Math.max(0,Math.min(1,(t-spin)/focusTime));
                double a=parameter/12d*Math.PI*2+ai.counter,f=-Math.PI/2+Math.sin(parameter/12d*Math.PI*2+ai.timer/19d)*.87;
                a+=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(f-a))*Math.PI/180*focus;
                double r=(2+133*Math.max(0,Math.min(1,t/(ai.shortRitual?30d:60d))))*fade/pixels;
                setPos(ai.center().add(plane.scale(Math.cos(a)*r)).add(0,-Math.sin(a)*r,0));}
            default->{}
        }
        if(arenaBoundary(boss))return;
        if(!stationary() && (kind!=Kind.SOUL_BOMB||parameter>0))setPos(position().add(velocity));
        if(kind==Kind.GIGA||kind==Kind.SHADOW_GIGA){
            var collision=level().clip(new net.minecraft.world.level.ClipContext(before,position(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
            if(collision.getType()!=HitResult.Type.MISS){setPos(collision.getLocation());expire(boss);return;}
        }
        if(kind==Kind.JEWEL_BEAM){
            setPos(ai.center());double length=0;Vec3 perpendicular=rotate(velocity,Math.PI/2);double width=Math.min(2,1+age*.15)*24/pixels;
            for(int i=0;i<24;i++){
                Vec3 from=position().add(perpendicular.scale((-1+2*i/23d)*width/2));
                var hit=level().clip(new net.minecraft.world.level.ClipContext(from,from.add(velocity.scale(3330/pixels)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                length+=hit.getLocation().distanceTo(from);
            }
            beamLength=length/24;
        }else if(kind==Kind.OVERLOAD_BEAM)beamLength=3950/pixels;
        if(damaging()){
            double hitRadius=collisionRadius();Vec3 end=position();
            if(kind==Kind.JEWEL_BEAM||kind==Kind.OVERLOAD_BEAM)end=position().add(velocity.scale(Math.max(0,beamLength-(kind==Kind.OVERLOAD_BEAM?50/pixels:0))));
            else if(kind==Kind.PILLAR)end=position().add(0,pillarShape(age,pixels).top(),0);
            Vec3 from=kind==Kind.PILLAR?position().add(0,pillarShape(age,pixels).bottom(),0):kind==Kind.EXPLOSION?position():stationary()?position():before;
            AABB query=new AABB(from,end).inflate(hitRadius);
            for(var victim:level().getEntitiesOfClass(LivingEntity.class,query,v->v!=boss&&v.isAlive()&&!boss.isAlliedTo(v)&&!(v instanceof MahouInfernumSummonEntity m&&m.ownedBy(boss))&&(!(v instanceof net.minecraft.world.entity.player.Player p)||!p.isCreative()&&!p.isSpectator()))){
                boolean hit=kind==Kind.EXPLOSION||kind==Kind.SOUL_BOMB?sphereHits(victim.getBoundingBox(),position(),hitRadius):capsuleHits(victim.getBoundingBox(),from,end,hitRadius);
                if(hit&&net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(victim,new net.minecraft.world.damagesource.DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK),this,boss),damage)&&(kind==Kind.GIGA||kind==Kind.SHADOW_GIGA)){expire(boss);return;}
            }
        }
        if(--remaining<=0){expire(boss);return;}
        var sample=new CompoundTag();sample.putInt("Age",age);vectorSample(sample);samples.add(sample);while(samples.size()>4)samples.remove(0);
        if(age%3==0)publish();
    }
    private boolean updateWarning(MahouInfernumAI ai,Vec3 target){
        if(anchor!=null){
            if(!(((ServerLevel)level()).getEntity(anchor) instanceof MahouInfernumSummonEntity summon)||!summon.isAlive())return false;
            point(summon.position(),ai.unit(target.subtract(summon.position())),ai.plane);
        }else if(parameter!=0){
            Vec3 at;
            switch(parameter){
                case OVERLOAD_WARNING->{if(ai.attack!=FireLaserSpin)return false;at=ai.origin;}
                case FINALE_WARNING->{if(ai.finalState!=1||!ai.tailAttack())return false;at=ai.center();}
                case BROTHER_WARNING->{if(ai.attack!=SummonBrothers)return false;at=ai.brotherSpawn(-Math.signum(velocity.dot(plane)));}
                default->{return true;}
            }
            point(at,MahouInfernumAI.turnPlane(velocity,plane,ai.plane),ai.plane);
        }
        return true;
    }
    private Vec3 bombDetonation(){
        // Each source step applies drag before movement, including the fuse's last step.
        return position().add(velocity.scale(BOMB_DRAG*(1-Math.pow(BOMB_DRAG,remaining))/(1-BOMB_DRAG)));
    }
    private static boolean blastDamaging(int framesLeft){return Math.max(0,Math.min(1,(framesLeft-8)/34d))*.55>=.37;}
    private static double bombPeakHitScale(){
        int frames=BOMB_BLAST_LIFE;
        while(!blastDamaging(BOMB_BLAST_LIFE-frames+1))frames--;
        return BOMB_BLAST_SCALE*BLAST_HIT_SCALE*(1-Math.pow(1-BLAST_GROWTH,frames));
    }
    private boolean arenaBoundary(MahouTsukaiEntity boss){
        var bounds=boss.arenaBounds();if(bounds==null)return false;
        boolean straight=switch(kind){case DART,FINAL_DART,ARROW,HELLBLAST,FLAME,BONE,DARK_SOUL,LOST_SOUL,SHADOW_SPARK,SHADOW_BOLT,SLASH->true;default->false;};
        int action=net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena.projectileBoundary(bounds,position(),position().add(velocity),collisionRadius(),pixels,straight,splitChild,level().getGameTime()-spawnedAt);
        if(action==2){retire();return true;}
        if(action==1){arenaFade=level().getGameTime();setPos(position().add(velocity));publish();return true;}
        return false;
    }
    private Vec3 rotate(Vec3 vector,double angle){
        Vec3 axis=plane.cross(new Vec3(0,-1,0)).normalize();
        return vector.scale(Math.cos(angle)).add(axis.cross(vector).scale(Math.sin(angle))).add(axis.scale(axis.dot(vector)*(1-Math.cos(angle))));
    }
    private static boolean sphereHits(AABB box,Vec3 center,double radius){
        double x=Math.max(box.minX,Math.min(center.x,box.maxX)),y=Math.max(box.minY,Math.min(center.y,box.maxY)),z=Math.max(box.minZ,Math.min(center.z,box.maxZ));
        return center.distanceToSqr(new Vec3(x,y,z))<=radius*radius;
    }
    /** Exact segment/AABB distance. Inflating an AABB creates false diagonal hits. */
    private static boolean capsuleHits(AABB box,Vec3 from,Vec3 to,double radius){
        double[] origin={from.x,from.y,from.z},delta={to.x-from.x,to.y-from.y,to.z-from.z};
        double[] low={box.minX,box.minY,box.minZ},high={box.maxX,box.maxY,box.maxZ},cuts=new double[8];
        int count=2;cuts[1]=1;
        for(int axis=0;axis<3;axis++)if(Math.abs(delta[axis])>1e-12){
            double a=(low[axis]-origin[axis])/delta[axis],b=(high[axis]-origin[axis])/delta[axis];
            if(a>0&&a<1)cuts[count++]=a;if(b>0&&b<1)cuts[count++]=b;
        }
        Arrays.sort(cuts,0,count);
        for(int i=0;i<count-1;i++){
            double midpoint=(cuts[i]+cuts[i+1])*.5,numerator=0,denominator=0;
            for(int axis=0;axis<3;axis++){
                double value=origin[axis]+delta[axis]*midpoint;
                if(value>=low[axis]&&value<=high[axis])continue;
                double boundary=value<low[axis]?low[axis]:high[axis];
                numerator+=delta[axis]*(origin[axis]-boundary);denominator+=delta[axis]*delta[axis];
            }
            double t=denominator==0?midpoint:Math.max(cuts[i],Math.min(cuts[i+1],-numerator/denominator));
            if(sphereHits(box,from.lerp(to,t),radius))return true;
        }
        return false;
    }
    void holdFor(UUID head){anchor=head;velocity=Vec3.ZERO;}
    void launchSoul(Vec3 speed){velocity=speed;parameter=48;}
    private boolean stationary(){return switch(kind){case WARNING,BOMB_WARNING,PILLAR_WARNING,PILLAR,JEWEL_BEAM,OVERLOAD_BEAM,EXPLOSION,ORB,RITUAL,HEART_STAR->true;default->false;};}
    private boolean damaging(){
        if(damage<=0)return false;
        return switch(kind){
            case GIGA,SHADOW_GIGA->age>20&&remaining>=20;
            case EXPLOSION->blastDamaging(remaining);
            case DART,FINAL_DART->remaining>=60;
            case REDIRECT_SOUL->remaining<=228&&remaining>=24;
            case LOST_SOUL->age>=10;
            case HELLBLAST->remaining>=51;
            case FIST->age>=12&&remaining>=12;
            case SLASH->age>=8&&remaining>=8;
            case SOUL_BOMB->parameter>0;
            case PILLAR->age>=23;
            case JEWEL_BEAM->age>2;
            case BOMB,WARNING,BOMB_WARNING,PILLAR_WARNING,ORB,RITUAL,HEART_STAR->false;
            default->true;
        };
    }
    public static double pillarGrowth(double age){return Math.max(0,Math.min(1,Math.sin(Math.PI*age/105)*2))*Math.min(1,age*.04);}
    public static double pillarHeight(double age){return 4+2396*pillarGrowth(age);}
    public static double pillarWidth(double age){return 3+87*pillarGrowth(age);}
    /** World-space capsule spine offsets and radius, shared by damage, flame and telegraph. */
    public record PillarShape(double bottom,double top,double radius) {}
    public static PillarShape pillarShape(double age,double pixels){
        double height=pillarHeight(age);
        return new PillarShape((height*.5-2440)/pixels,(height*1.5-2440)/pixels,pillarWidth(age)*.5/pixels);
    }
    private double collisionRadius(){return switch(kind){
        case EXPLOSION->radius*BLAST_HIT_SCALE;
        case SOUL_BOMB->radius*.72;
        case PILLAR->pillarShape(age,pixels).radius();
        case JEWEL_BEAM->10/pixels*Math.min(2,1+age*.15);
        case OVERLOAD_BEAM->30/pixels*Math.min(2-(2-.051)*Math.max(0,Math.min(1,(age-390)/60d)),.05+age*.04);
        case GIGA,SHADOW_GIGA->25/pixels;
        case BOMB->12/pixels;
        case ARROW,SHADOW_BOLT->13/pixels;
        case DART,FINAL_DART->9/pixels;
        case DARK_SOUL,FLAME->17/pixels;
        case REDIRECT_SOUL,LOST_SOUL->14/pixels;
        case HELLBLAST->20/pixels;
        case SHADOW_BLAST->26/pixels;
        case SHADOW_SPARK->12/pixels;
        case REDIRECT_HELLFIRE->8/pixels;
        case FIST->22/pixels;
        case SLASH->30/pixels;
        default->10/pixels;
    };}
    private MahouInfernumAttackEntity child(MahouTsukaiEntity boss,Kind type,Vec3 v,int life,float dmg,double r){var child=spawn(boss,type,position(),v,plane,life,dmg,r,tail);child.splitChild=true;child.publish();return child;}
    private void expire(MahouTsukaiEntity boss){
        if(exploded)return;exploded=true;
        var ai=boss.infernum();
        switch(kind){
            case BOMB->{spawn(boss,Kind.EXPLOSION,position(),Vec3.ZERO,plane,BOMB_BLAST_LIFE,16,radius*BOMB_BLAST_SCALE,tail);
                if(parameter==1)for(int i=0;i<6;i++)child(boss,Kind.DART,rotate(plane,i*Math.PI/3).scale(7.4/pixels),690,14,0);}
            case GIGA->{int n=parameter>=2?parameter:45;for(int i=0;i<n;i++)child(boss,Kind.DART,rotate(plane,i*Math.PI*2/n).scale(5/pixels),690,damage,0).parameter(1);}
            case BOMB_WARNING->{child(boss,Kind.BOMB,velocity.scale(19.5/pixels),135+boss.getRandom().nextInt(50),0,radius);}
            case PILLAR_WARNING->child(boss,Kind.PILLAR,Vec3.ZERO,105,18.333334f,0);
            case RITUAL->MahouInfernumSummonEntity.spawn(boss,MahouInfernumSummonEntity.Kind.BOMBER,position(),0).inheritAttack(plane,tail);
            case SHADOW_GIGA->{
                double spread=Math.PI/2*.12,startAngle=Math.atan2(velocity.dot(plane),-velocity.y)-spread/2;
                for(int i=0;i<15;i++){double angle=startAngle+spread/30*(i+i*i)/2+32*i;
                    Vec3 speed=plane.scale(Math.sin(angle)*12/pixels).add(0,-Math.cos(angle)*12/pixels,0);
                    child(boss,Kind.SHADOW_BOLT,speed,180,damage,0);child(boss,Kind.SHADOW_BOLT,speed.scale(-1),180,damage,0);
                }
            }
            case SHADOW_BLAST->{for(int i=0;i<6;i++)child(boss,Kind.SHADOW_SPARK,rotate(plane,i*Math.PI/3).scale(7/pixels),240,14,0);}
            case SOUL_BOMB->{if(ai.living(MahouInfernumSummonEntity.Kind.WORM)>0)for(int ring=0;ring<3;ring++){
                int n=15+4*ring;double speed=8-2.5*ring;
                for(int i=0;i<n;i++)child(boss,Kind.DART,rotate(plane,i*Math.PI*2/n+(ring%2==0?Math.PI/n:0)).scale(speed/pixels),690,14,0);
            }}
            default->{}
        }
        retire();
    }
    @Override public void remove(RemovalReason reason){
        if(reason.shouldDestroy()&&!isRemoved()&&level() instanceof ServerLevel server&&owner!=null)CalamitasBattleData.get(server).forget(owner,getUUID());
        super.remove(reason);
    }
    void retire(){if(level() instanceof ServerLevel server&&owner!=null)CalamitasBattleData.get(server).forget(owner,getUUID());discard();}
    private CompoundTag state(){var t=new CompoundTag();t.putString("Kind",kind.name());t.putInt("Age",age);t.putInt("Left",remaining);t.putInt("Parameter",parameter);t.putFloat("Damage",damage);t.putDouble("Radius",radius);t.putDouble("BeamLength",beamLength);t.putDouble("Pixels",pixels);t.putBoolean("Tail",tail);t.putBoolean("SplitChild",splitChild);t.putLong("SpawnedAt",spawnedAt);t.putLong("ArenaFade",arenaFade);t.putFloat("Opacity",arenaFade<0?1:Math.max(0,1-(level().getGameTime()-arenaFade)/5f));if(owner!=null)t.putUUID("Owner",owner);if(anchor!=null)t.putUUID("Anchor",anchor);vector(t,"Velocity",velocity);vector(t,"Plane",plane);vector(t,"Start",start);return t;}
    private void vectorSample(CompoundTag t){MahouInfernumAttackEntity.vector(t,"Position",position());MahouInfernumAttackEntity.vector(t,"Velocity",velocity);}
    private void publish(){var t=state();t.put("Samples",samples.copy());
        if(kind==Kind.BOMB){vector(t,"Detonation",bombDetonation());t.putDouble("BlastRadius",radius*BOMB_PEAK_HIT_SCALE);}
        entityData.set(VIEW,t);
    }
    @Override protected void addAdditionalSaveData(CompoundTag t){t.put("Infernum",state());}
    @Override protected void readAdditionalSaveData(CompoundTag root){var t=root.getCompound("Infernum");if(t.isEmpty())return;
        try{kind=Kind.valueOf(t.getString("Kind"));}catch(IllegalArgumentException e){remaining=0;return;}
        owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;anchor=t.hasUUID("Anchor")?t.getUUID("Anchor"):null;age=t.getInt("Age");remaining=t.getInt("Left");parameter=t.getInt("Parameter");damage=t.getFloat("Damage");radius=t.getDouble("Radius");beamLength=t.getDouble("BeamLength");pixels=t.getDouble("Pixels");if(!Double.isFinite(pixels)||pixels<=0)pixels=16;tail=t.getBoolean("Tail");splitChild=t.getBoolean("SplitChild");spawnedAt=t.contains("SpawnedAt")?t.getLong("SpawnedAt"):level().getGameTime();arenaFade=t.contains("ArenaFade")?t.getLong("ArenaFade"):-1;velocity=vector(t,"Velocity");plane=vector(t,"Plane");start=vector(t,"Start");publish();
    }
    public static void vector(CompoundTag t,String key,Vec3 v){t.putDouble(key+"X",v.x);t.putDouble(key+"Y",v.y);t.putDouble(key+"Z",v.z);}
    public static Vec3 vector(CompoundTag t,String key){return new Vec3(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));}
}

