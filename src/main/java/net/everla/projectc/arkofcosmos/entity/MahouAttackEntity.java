package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Ephemeral boss attacks; never places or destroys world blocks. */
public final class MahouAttackEntity extends Entity {
    public static final int PILLAR=0,COVER=1,WAVE=2,WARNING=5,BLOOM=6,GRAVITY=7,DOMAIN=8,SWING_WARNING=9,NEEDLE_GATE=10,RAIN=11,CASTING=12,VOID_VORTEX=13,DOMAIN_TEXT=14,RED=15,BLUE=16,BLUE_CHARGE=17,FUSION=18,PURPLE_CORE=19,PURPLE=20,BLUE_DASH=21,RED_WARNING=22;
    private static final EntityDataAccessor<CompoundTag> DATA=SynchedEntityData.defineId(MahouAttackEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID owner;
    private int age,life,delay,kind,swingMode,ownerPhase;
    private float damage;
    private Vec3 velocity=Vec3.ZERO;
    private boolean landed;
    private final Set<Integer> hits=new HashSet<>();
    public MahouAttackEntity(EntityType<? extends MahouAttackEntity> type,Level level) { super(type,level); setNoGravity(true); }
    public static MahouAttackEntity spawn(MahouTsukaiEntity boss,int kind,Vec3 at,Vec3 velocity,int delay,int life,float damage) {
        MahouAttackEntity e=new MahouAttackEntity(ArkOfCosmos.MAHOU_ATTACK.get(),boss.level());
        e.owner=boss.getUUID();e.ownerPhase=boss.phase();e.kind=kind;e.velocity=velocity;e.delay=delay;e.life=life;e.damage=damage;e.setPos(at);
        e.publish();e.refreshDimensions();boss.level().addFreshEntity(e);return e;
    }
    public static MahouAttackEntity swingWarning(MahouTsukaiEntity boss,Vec3 direction,int mode){
        MahouAttackEntity e=spawn(boss,SWING_WARNING,boss.position().add(0,.1,0),direction,0,14,0);
        e.swingMode=mode;e.publish();return e;
    }
    static MahouAttackEntity rain(MahouTsukaiEntity boss,Vec3 at,boolean needle){
        MahouAttackEntity e=spawn(boss,RAIN,at,Vec3.ZERO,20,36,0);e.swingMode=needle?1:0;e.publish();return e;
    }
    static MahouAttackEntity voidVortex(MahouTsukaiEntity boss, Vec3 at){
        return spawn(boss,VOID_VORTEX,at,Vec3.ZERO,0,12000,0);
    }
    public static org.joml.Quaternionf pillarRotation(Vec3 direction){
        if(direction.lengthSqr()<1e-8)return new org.joml.Quaternionf();
        Vec3 axis=direction.normalize();
        return new org.joml.Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z);
    }
    public boolean ownedBy(MahouTsukaiEntity boss){return boss.getUUID().equals(owner);}
    void anchor(Vec3 at){setPos(at);publish();}
    public CompoundTag view() { return entityData.get(DATA); }
    void pointSwingWarning(Vec3 at,Vec3 direction){setPos(at);velocity=direction;publish();}
    void pointWarning(Vec3 at,Vec3 direction){setPos(at);velocity=direction.normalize().scale(52);publish();}
    public int kind() { return view().getInt("Kind"); }
    public static Vec3 velocity(CompoundTag t) { return new Vec3(t.getDouble("VX"),t.getDouble("VY"),t.getDouble("VZ")); }
    public AABB pillarBox() {
        if(kind()!=PILLAR)return new AABB(getX()-1,getY(),getZ()-1,getX()+1,getY()+5,getZ()+1);
        var rotation=pillarRotation(velocity(view()));
        Vec3 center=position();
        AABB box=new AABB(center,center);
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
            var v=new org.joml.Vector3f(x,y*2.5f,z).rotate(rotation);
            box=box.minmax(new AABB(center.add(v.x,v.y,v.z),center.add(v.x,v.y,v.z)));
        }
        return box;
    }
    @Override public boolean canBeCollidedWith() { return !isRemoved() && (kind()==PILLAR||kind()==COVER) && view().getBoolean("Landed"); }
    @Override public EntityDimensions getDimensions(Pose pose) { return kind()==PILLAR||kind()==COVER?EntityDimensions.fixed(2,5):super.getDimensions(pose); }
    @Override protected void defineSynchedData() { entityData.define(DATA,new CompoundTag()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(DATA.equals(key)) {
            EntityDimensions size=getDimensions(getPose());
            if(getBbWidth()!=size.width||getBbHeight()!=size.height)refreshDimensions();
        }
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance<256*256; }
    @Override public void tick() {
        super.tick(); if(level().isClientSide)return;
        Entity entity=owner==null?null:((ServerLevel)level()).getEntity(owner);
        if (!(entity instanceof MahouTsukaiEntity boss) || !boss.isAlive() || boss.phase()!=ownerPhase || boss.phaseRecovering() || boss.isNoAi()) { discard();return; }
        if(kind==VOID_VORTEX&&position().distanceTo(boss.position())>48){
            setPos(boss.position().add(0,6,0));
            velocity=Vec3.ZERO;
        }
        if (++age>life) { discard();return; }
        if(kind==PILLAR&&age<=delay&&MahouPhaseOneAI.valid(boss,boss.getTarget()))
            velocity=boss.getTarget().position().subtract(position()).normalize().scale(1.4);
        if(kind==RAIN&&boss.phaseTransitionTicks()==0){discard();return;}
        if(kind==RAIN&&age==delay+1){
            Vec3 from=position().add(0,14,0);
            if(swingMode==0)GalaxiaEntity.rainBolt(boss,from);
            else ElementsEntity.rainNeedle(boss,from);
        }
        if(kind==NEEDLE_GATE){
            if(age==delay-3&&MahouPhaseOneAI.valid(boss,boss.getTarget()))velocity=boss.getTarget().getBoundingBox().getCenter().subtract(position());
            if(age==delay+1){
                Vec3 direction=velocity.normalize();
                ElementsEntity.bossGateNeedle(boss,damage,position(),
                        GalaxiaEntity.aimFrame(direction,net.everla.projectc.arkofcosmos.util.AttackFrame.capture(boss).right()));
            }
        }
        if (kind==PILLAR || kind==COVER) {
            if(age>delay&&!landed) {
                Vec3 from=position();
                if(kind==PILLAR){
                    // Move the rotated volume without Entity.move rebasing its asymmetric origin.
                    publish();
                    Vec3 actual=Entity.collideBoundingBox(this,velocity,pillarBox(),level(),java.util.List.of());
                    setPos(from.add(actual));
                }else move(MoverType.SELF,velocity);
                // Use the complete 2 x 5 x 2 volume for world collision, not an infinitesimal ray.
                AABB volume=pillarBox();
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,volume.expandTowards(from.subtract(position())),v->MahouPhaseOneAI.valid(boss,v))) hit(boss,victim);
                if (level().getBlockCollisions(this,volume).iterator().hasNext() || position().distanceToSqr(from.add(velocity))>.001) {
                    setPos(from);landed=true;
                    // Snap the bottom to the first supporting surface, retaining cliff/ceiling collision.
                    BlockHitResult floor=level().clip(new ClipContext(from,from.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                    if(floor.getType()!=HitResult.Type.MISS)setPos(getX(),floor.getLocation().y,getZ());
                    ArkOfCosmos.sound(this,"galaxia_impact",1);
                    if(kind==PILLAR) { BlockHitResult impact=level().clip(new ClipContext(from,from.add(0,-6,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                        Vec3 waveAt=impact.getType()==HitResult.Type.MISS?position():impact.getLocation();
                        spawn(boss,WAVE,waveAt.add(0,.1,0),Vec3.ZERO,0,18,10);discard();return; }
                }

            }
        } else if(kind==BLOOM||kind==GRAVITY){
            double r=kind==BLOOM?3:6;
            if(age==delay+1){
                ArkOfCosmos.sound(this,"galaxia_impact",1);
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(r,8,r),v->MahouPhaseOneAI.valid(boss,v))){
                    if(victim.position().multiply(1,0,1).distanceTo(position().multiply(1,0,1))<=r+victim.getBbWidth()/2){
                        hit(boss,victim);
                        if(kind==GRAVITY){victim.push(0,-.8,0);victim.hurtMarked=true;}
                    }
                }
            }
        } else if (kind==VOID_VORTEX) {
            LivingEntity target=boss.getTarget();
            if(MahouPhaseOneAI.valid(boss,target)){
                Vec3 delta=target.getBoundingBox().getCenter().subtract(position());
                if(boss.fusionShield()){
                    Vec3 outward=target.getBoundingBox().getCenter().subtract(boss.getBoundingBox().getCenter()).normalize();
                    if(outward.lengthSqr()<.1)outward=new Vec3(1,0,0);
                    if(delta.length()<15||delta.length()>18||position().distanceTo(boss.getBoundingBox().getCenter())<12)
                        setPos(target.getBoundingBox().getCenter().add(outward.scale(16.5)));
                    velocity=Vec3.ZERO;
                }else{velocity=velocity.scale(.96).add(delta.normalize().scale(.004));if(velocity.length()>.1)velocity=velocity.normalize().scale(.1);setPos(position().add(velocity));}
            }
            if(age%10==0)for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(4),v->MahouPhaseOneAI.valid(boss,v)))
                net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(victim,damageSources().indirectMagic(this,boss),18);
        } else if(kind==FUSION){
            if(age==delay+1){
                ArkOfCosmos.sound(this,"galaxia_eruption",2);
                net.minecraft.nbt.CompoundTag effect=new net.minecraft.nbt.CompoundTag();effect.putString("Event","mahou_fusion");effect.putInt("Owner",boss.getId());effect.putDouble("AtX",boss.getX());effect.putDouble("AtY",boss.getY());effect.putDouble("AtZ",boss.getZ());net.everla.projectc.arkofcosmos.network.ArkNetwork.effect(boss,effect);
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,boss.getBoundingBox().inflate(64),v->MahouPhaseOneAI.valid(boss,v))){
                    if(!insideFusionShield(boss,victim))hit(boss,victim);
                }
            }
        } else if(kind==BLUE_DASH){
            pull(boss,4,true,.025);
            if(age>delay)for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(2),v->MahouPhaseOneAI.valid(boss,v)))hit(boss,victim);
        } else if(kind==RED||kind==BLUE||kind==PURPLE||kind==DOMAIN_TEXT){
            if(kind==PURPLE&&age<=delay){velocity=velocity.scale(.94);setPos(position().add(velocity));}
            else if(age>delay){
                if(kind==PURPLE&&age==delay+1&&MahouPhaseOneAI.valid(boss,boss.getTarget()))velocity=boss.getTarget().getBoundingBox().getCenter().subtract(position()).normalize().scale(1.2);
                if(kind==BLUE&&landed){pull(boss,10,false,.025);if(age%20==0)hits.clear();}
                else{
                    Vec3 from=position(),end=from.add(velocity);
                    var collision=level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,this));
                    if(collision.getType()!=HitResult.Type.MISS){
                        setPos(collision.getLocation());
                        if(kind==BLUE){landed=true;velocity=Vec3.ZERO;life=age+100;}else{discard();return;}
                    }else setPos(end);
                    double radius=kind==DOMAIN_TEXT?1.3:kind==PURPLE?1:1.6;
                    for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,position()).inflate(radius),v->MahouPhaseOneAI.valid(boss,v))){
                        hit(boss,victim);
                        if(kind==RED){
                            for(LivingEntity impactVictim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(2.5),v->MahouPhaseOneAI.valid(boss,v)))hit(boss,impactVictim);
                            ArkOfCosmos.sound(this,"galaxia_impact",1);discard();return;
                        }
                        if(kind==PURPLE){discard();return;}
                    }
                }
            }
        } else if (kind==WAVE) {
            if(age>delay) {
                double radius=radius(age,delay,life),previous=radius(age-1,delay,life);
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(radius+2,6,radius+2),v->MahouPhaseOneAI.valid(boss,v))) {
                    double distance=victim.position().multiply(1,0,1).distanceTo(position().multiply(1,0,1));
                    if(distance<=radius+victim.getBbWidth()/2 && distance>=previous-1.5 && !sheltered(position().add(0,1,0),victim,boss)) hit(boss,victim);
                }
            }
        }
        publish();
    }
    private void pull(MahouTsukaiEntity boss,double radius,boolean playersOnly){pull(boss,radius,playersOnly,.035);}
    private void pull(MahouTsukaiEntity boss,double radius,boolean playersOnly,double strength){
        for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),v->MahouPhaseOneAI.valid(boss,v)&&(!playersOnly||v instanceof net.minecraft.world.entity.player.Player))){
            Vec3 delta=position().subtract(victim.getBoundingBox().getCenter());
            if(delta.length()<=radius){victim.push(delta.normalize().x*strength,delta.normalize().y*strength*.72,delta.normalize().z*strength);victim.hurtMarked=true;}
        }
    }
    public static double radius(double age,int delay,int life) { return Math.max(0,Math.min(1,(age-delay)/Math.max(1,life-delay)))*(delay==0?3:26); }
    private boolean sheltered(Vec3 origin,LivingEntity target,MahouTsukaiEntity boss) {
        Vec3 end=target.getBoundingBox().getCenter();
        for(MahouAttackEntity pillar:level().getEntitiesOfClass(MahouAttackEntity.class,new AABB(origin,end).inflate(2),
                p->p.owner!=null&&p.owner.equals(owner)&&p.kind==COVER&&p.landed))
            if(pillar.pillarBox().clip(origin,end).isPresent())return true;
        return level().clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS;
    }
    private void hit(MahouTsukaiEntity boss,LivingEntity victim) {
        if(hits.add(victim.getId()))net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(victim,kind==PILLAR?damageSources().mobAttack(boss):damageSources().indirectMagic(this,boss),damage);
    }
    /** The shield is a live radius around the boss, so moving into it before the
     * fusion impact resolves must protect the entity as well. */
    private boolean insideFusionShield(MahouTsukaiEntity boss, LivingEntity victim) {
        if (!boss.fusionShield()) return false;
        Vec3 center=victim.getBoundingBox().getCenter();
        double dx=center.x-boss.getX(), dz=center.z-boss.getZ();
        return dx*dx+dz*dz<=MahouTsukaiEntity.FUSION_SHIELD_RADIUS*MahouTsukaiEntity.FUSION_SHIELD_RADIUS;
    }
    private void publish() {
        CompoundTag t=new CompoundTag();t.putInt("Kind",kind);t.putInt("SwingMode",swingMode);t.putInt("Age",age);t.putInt("Life",life);t.putInt("Delay",delay);t.putBoolean("Landed",landed);
        t.putDouble("VX",velocity.x);t.putDouble("VY",velocity.y);t.putDouble("VZ",velocity.z);entityData.set(DATA,t);
    }
}
