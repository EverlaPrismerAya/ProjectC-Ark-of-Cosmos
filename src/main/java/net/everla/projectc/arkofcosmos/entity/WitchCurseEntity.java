package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.everla.projectc.arkofcosmos.combat.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Source curse actors: no terrain destruction, source timers run at 60 frames/sec. */
public final class WitchCurseEntity extends Monster implements OwnableEntity {
    public enum Kind { DEMON,PORTAL,ORB,ARM,MANA,BOMBER,HEAL,HEART,EXPLOSION,SMALL_EXPLOSION }
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(WitchCurseEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID owner,portal;
    private net.minecraft.world.item.ItemStack sourceItem=net.minecraft.world.item.ItemStack.EMPTY;
    private Kind kind=Kind.DEMON;
    private int age,hand,frame;
    private float damage;
    private boolean friendly,ending,rewarded,flipped;
    private Vec3 velocity=Vec3.ZERO;
    private final java.util.Map<UUID,Integer> hitUntil=new java.util.HashMap<>();
    public WitchCurseEntity(EntityType<? extends WitchCurseEntity> type,net.minecraft.world.level.Level level){super(type,level);noPhysics=true;setNoGravity(true);xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(CalamitasContent.MAX_HEALTH.get(),360).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override public double getAttributeValue(Attribute attribute){return super.getAttributeValue(attribute==Attributes.MAX_HEALTH?CalamitasContent.MAX_HEALTH.get():attribute);}
    @Override public AttributeInstance getAttribute(Attribute attribute){return super.getAttribute(attribute==Attributes.MAX_HEALTH?CalamitasContent.MAX_HEALTH.get():attribute);}
    @Override public double getAttributeBaseValue(Attribute attribute){return super.getAttributeBaseValue(attribute==Attributes.MAX_HEALTH?CalamitasContent.MAX_HEALTH.get():attribute);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(VIEW,new CompoundTag());}
    public Kind kind(){return level().isClientSide&&entityData.hasItem(VIEW)?Kind.valueOf(entityData.get(VIEW).getString("Kind").isEmpty()?"DEMON":entityData.get(VIEW).getString("Kind")):kind==null?Kind.DEMON:kind;}
    public CompoundTag view(){return entityData.get(VIEW);}
    @Override public UUID getOwnerUUID(){return level().isClientSide && entityData.hasItem(VIEW) && view().hasUUID("Owner")?view().getUUID("Owner"):owner;}
    @Override public Player getOwner(){UUID id=getOwnerUUID();return id==null?null:level().getPlayerByUUID(id);}
    public static boolean exists(Player p,Kind kind){return !p.level().getEntitiesOfClass(WitchCurseEntity.class,p.getBoundingBox().inflate(256),e->p.getUUID().equals(e.owner)&&e.kind==kind&&!e.isRemoved()).isEmpty();}
    public static boolean armExists(Player p,int hand){
        if(!(p.level() instanceof ServerLevel server))return false;
        WitchCurseEntity existing=null;
        var duplicates=new java.util.ArrayList<WitchCurseEntity>();
        // The source arm swing and idle offsets extend well beyond 16 blocks.
        for(var entity:server.getAllEntities())
            if(entity instanceof WitchCurseEntity arm && p.getUUID().equals(arm.owner) && arm.kind==Kind.ARM && arm.hand==hand && !arm.isRemoved()){
                if(existing==null && arm.isAlive())existing=arm;else duplicates.add(arm);
            }
        duplicates.forEach(WitchCurseEntity::finish);
        return existing!=null;
    }
    private static long count(Player p,Kind kind){long count=0;if(p.level() instanceof ServerLevel server)for(var entity:server.getAllEntities())if(entity instanceof WitchCurseEntity actor && actor.kind==kind && actor.isAlive())count++;return count;}
    public static void demon(Player p){if(count(p,Kind.DEMON)<2)spawn(p,Kind.DEMON,p.getEyePosition().add(p.getLookAngle().scale(12)),0,0);}
    public static void portal(Player p){if(count(p,Kind.PORTAL)<2){Vec3 v=new Vec3(p.getRandom().nextGaussian(),p.getRandom().nextGaussian(),p.getRandom().nextGaussian()).normalize();spawn(p,Kind.PORTAL,p.position().add(v.scale((270+p.getRandom().nextDouble()*150)/16)),0,0);}}
    public static WitchCurseEntity spawn(Player p,Kind kind,Vec3 at,float damage,int hand){
        if(!(p.level() instanceof ServerLevel server))return null;
        var e=CalamitasContent.CURSE_ACTOR.get().create(server);if(e==null)return null;
        e.owner=p.getUUID();e.sourceItem=p.getMainHandItem().copy();e.kind=kind;e.hand=hand;e.damage=damage;e.setPos(at);e.velocity=p.getLookAngle().scale(.28);
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue((kind==Kind.PORTAL?25000:kind==Kind.ORB?180000:100000)*net.everla.projectc.arkofcosmos.ProjectCCommonConfig.calamitasValue("health_multiplier"));e.setHealth(e.getMaxHealth());
        e.refreshDimensions();e.publish();return server.addFreshEntity(e)?e:null;
    }
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox(){return super.makeBoundingBox().move(0,-getBbHeight()/2,0);}
    @Override public EntityDimensions getDimensions(Pose pose){float size=switch(kind()){case PORTAL->60/16f;case DEMON->68/16f;case ORB->28/16f;case MANA->52/16f;case BOMBER->3;default->.25f;};return EntityDimensions.scalable(size,size);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor){super.onSyncedDataUpdated(accessor);if(accessor==VIEW)refreshDimensions();}
    private void publish(){CompoundTag tag=new CompoundTag();tag.putString("Kind",kind.name());tag.putInt("Age",age);tag.putInt("Hand",hand);tag.putInt("Frame",frame);if(getOwner()!=null)tag.putLong("SwingLeft",Math.max(0,getOwner().getPersistentData().getLong("ArkWitchArmSwing")-level().getGameTime()*3));tag.putBoolean("Friendly",friendly);if(owner!=null)tag.putUUID("Owner",owner);entityData.set(VIEW,tag);}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel))return;
        if(isDeadOrDying())return;
        Player p=getOwner();if(p==null || !p.isAlive()){finish();return;}
        if(kind==Kind.ORB && Curse.of(p.getMainHandItem())!=Curse.LECHEROUS || kind==Kind.ARM && (Curse.of(p.getMainHandItem())!=Curse.TAINTED || !p.getMainHandItem().is(sourceItem.getItem()))){finish();return;}
        setDeltaMovement(Vec3.ZERO);
        for(int i=0;i<3&&!isRemoved();i++){step(p);age++;}
        if(!isRemoved())publish();
    }
    private void step(Player p){
        Vec3 center=p.getBoundingBox().getCenter();
        switch(kind){
            case PORTAL -> {
                if(getHealth()<=1 && !flipped){flipped=true;for(var entity:((ServerLevel)level()).getAllEntities())if(entity instanceof WitchCurseEntity demon && demon.kind==Kind.BOMBER && demon.friendly && getUUID().equals(demon.portal) && java.util.Objects.equals(owner,demon.owner)){demon.friendly=false;demon.publish();}}
                if(age==300)for(int i=0;i<6;i++){
                    var child=spawn(p,Kind.BOMBER,position(),17000*.06f,i);
                    if(child!=null){child.portal=getUUID();child.hand=-random.nextInt(41);child.friendly=getHealth()<=1;child.velocity=new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).normalize().scale(.25);child.publish();}
                }
                if(age>=420)finish();
            }
            case DEMON -> {
                if(age<15)return;
                int timer=(age-15)%120;
                if(timer>90)velocity=velocity.scale(.94);
                else if(position().distanceTo(center)>150/16d){Vec3 ideal=center.subtract(position()).normalize();velocity=velocity.scale(29).add(ideal).scale(1d/30).lerp(ideal,.025);}
                if(timer==119)velocity=center.subtract(position()).normalize().scale(23.5/16);
                moveAndHit(p,p.getMaxHealth()*270/400f,false,1);
            }
            case ORB -> {
                if(frame==0){if(random.nextInt(150)==0)frame=1;else if(random.nextInt(300)==0)frame=8;}
                else if(frame<8 && age%6==5){if(++frame>=8)frame=0;}else if(frame>=8 && age%5==4){if(++frame>=25)frame=0;}
                Vec3 target=center.lerp(ArkCombat.aim(p),.625);Vec3 to=target.subtract(position());
                setPos(position().lerp(target,.035).add(to.normalize().scale(Math.min(.5,to.length()))));
            }
            case ARM -> {
                Vec3 forward=p.getLookAngle(),up=new Vec3(0,1,0);double vertical=hand==0?0:-80;
                long left=p.getPersistentData().getLong("ArkWitchArmSwing")-level().getGameTime()*3+age%3;
                boolean swing=left>0;double ratio=Math.max(0,Math.min(1,1-left/25d));
                double angle=CurseCombat.smooth(-1.87,3.79,ratio)-(hand==1?.7:0);
                angle*=Math.max(0,Math.min(1,ratio/.16));
                Vec3 destination=swing?center.add(forward.scale(Math.sin(angle)*600/16)).add(up.scale(Math.cos(angle)*600/16)):
                    center.add(forward.scale((vertical*.6+180)/16)).add(0,-(330+vertical+(hand==1?540:0))/16,0);
                Vec3 old=position();if(swing)setPos(position().lerp(destination,.19));
                Vec3 offset=destination.subtract(position());setPos(position().add(offset.normalize().scale(Math.min(offset.length(),.75+p.getDeltaMovement().length()))));
                if(position().distanceTo(destination)>300/16d)setPos(destination);
                damage=(float)(p.getAttributeValue(Attributes.ATTACK_DAMAGE)*5);
                var geometry=TaintedGeometry.of(p,center,position(),hand,age,ratio,swing);
                Vec3 bladeStart=geometry.blade().subtract(geometry.direction().scale(.5)),bladeEnd=geometry.blade().add(geometry.direction().scale(.5));
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(bladeStart,bladeEnd).inflate(60/32d),v->ArkCombat.enemy(p,v)))
                    if(victim.getBoundingBox().inflate(60/32d).clip(bladeStart,bladeEnd).isPresent() || victim.getBoundingBox().inflate(60/32d).contains(bladeStart))strike(p,victim,damage);
            }
            case MANA -> {
                if(age<50){if(velocity.length()<27/16d)velocity=velocity.scale(1.05);}
                else if(age<95){Vec3 ideal=center.subtract(position()).normalize(),direction=velocity.normalize();double angle=Math.acos(Math.max(-1,Math.min(1,direction.dot(ideal))));double t=angle<.0001?1:Math.min(1,.09/angle);Vec3 turn=direction.lerp(ideal,t).normalize();velocity=turn.scale(velocity.length()+(22/16d-velocity.length())*.1);}
                else if(velocity.length()<35/16d)velocity=velocity.scale(1.04);
                if(age>=30 && age<=285)moveAndHit(p,damage,true,52/32d);else setPos(position().add(velocity));if(age>=300)finish();
            }
            case BOMBER -> {
                LivingEntity target=friendly?ArkCombat.nearest(p,position(),1360/16d):p;
                for(var other:level().getEntitiesOfClass(WitchCurseEntity.class,getBoundingBox().inflate(4),e->e!=this&&e.kind==Kind.BOMBER)){
                    Vec3 away=position().subtract(other.position());if(away.lengthSqr()<60*60/256d)velocity=velocity.add(away.normalize().scale(.08/16));
                }
                int timer=age+hand;
                if(timer<45)velocity=velocity.lerp(new Vec3(0,5/16d,0),.06);
                else if(timer<90){double speed=Math.max(0,velocity.length()-.4/16)*.95;velocity=velocity.normalize().scale(speed);}
                else if(target!=null){double inertia=friendly?8:25,speed=(friendly?26.75:18.5)/16;velocity=velocity.scale(inertia-1).add(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed)).scale(1/inertia);}
                setPos(position().add(velocity));
                if(timer>=90 && target!=null && target.getBoundingBox().inflate(.5).contains(position())){
                    if(target instanceof Player player)hurtPlayer(player,360);else strike(p,target,damage);
                    finish();
                }
                if(age>=300)finish();
            }
            case EXPLOSION,SMALL_EXPLOSION -> {
                double radius=kind==Kind.EXPLOSION?5:108/32d;
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),v->ArkCombat.enemy(p,v) || kind==Kind.EXPLOSION && v instanceof Player player && !player.isCreative()&&!player.isSpectator())){
                    if(victim instanceof Player player){if(age>=hitUntil.getOrDefault(player.getUUID(),-1)){hurtPlayer(player,6000);hitUntil.put(player.getUUID(),age+10);}}else strike(p,victim,damage);
                }
                if(age>=29)finish();
            }
            case HEAL,HEART -> {
                if(age%3==0 && level() instanceof ServerLevel server)server.sendParticles(kind==Kind.HEART?net.minecraft.core.particles.ParticleTypes.HEART:net.minecraft.core.particles.ParticleTypes.FLAME,getX(),getY(),getZ(),1,0,0,0,0);
                if(kind==Kind.HEAL) {velocity=velocity.lerp(center.subtract(position()).normalize().scale(20/16d),.15);setPos(position().add(velocity));}
                if(position().distanceToSqr(center)<2){CurseCombat.heal(p,kind==Kind.HEAL?10:20);finish();}
                if(age>=(kind==Kind.HEAL?640:18000))finish();
            }
        }
    }
    private void moveAndHit(Player p,float amount,boolean indiscriminate,double radius){
        Vec3 old=position(),next=old.add(velocity);setPos(next);
        for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(old,next).inflate(radius),v->v!=this && v.isAlive() && !(v instanceof WitchCurseEntity) && !(v instanceof BrimstoneWitchEntity) && (indiscriminate || v==p))){
            if(victim instanceof Player player){if(!player.isCreative()&&!player.isSpectator() && !hitUntil.containsKey(player.getUUID())){boolean hurt=hurtPlayer(player,kind==Kind.MANA?360:270);if(hurt&&kind==Kind.MANA)player.addEffect(new net.minecraft.world.effect.MobEffectInstance(CalamitasContent.VULNERABILITY_HEX.get(),60));hitUntil.put(player.getUUID(),age+20);}}
            else strike(p,victim,amount);
        }
        hitUntil.entrySet().removeIf(e->e.getValue()<=age);
    }
    private boolean hurtPlayer(Player player,double source){
        return ArkDamage.hurtWithVanillaCooldown(player,damageSources().mobAttack(this),(float)CurseMath.health(source,player.getMaxHealth()));
    }
    public float capDamage(float amount){return kind==Kind.PORTAL?Math.min(amount,Math.max(0,getHealth()-1)):amount;}
    private void strike(Player p,LivingEntity target,float amount){
        if(age<hitUntil.getOrDefault(target.getUUID(),-1))return;
        hitUntil.put(target.getUUID(),age+((kind==Kind.EXPLOSION||kind==Kind.SMALL_EXPLOSION)?10:kind==Kind.ARM?(p.getPersistentData().getLong("ArkWitchArmSwing")>level().getGameTime()*3?3:24):5));
        WeaponDamage.hurt(target,new DamageSource(p.damageSources().playerAttack(p).typeHolder(),this,p),amount);
    }
    public static void explode(Player p,Vec3 at,float damage,double radius){
        spawn(p,radius>=5?Kind.EXPLOSION:Kind.SMALL_EXPLOSION,at,damage,0);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(kind!=Kind.DEMON&&kind!=Kind.PORTAL&&kind!=Kind.ORB)return false;
        if(kind==Kind.PORTAL){if(getHealth()<=1)return false;amount=Math.min(amount,getHealth()-1);}
        boolean result=super.hurt(source,amount);if(kind==Kind.PORTAL && getHealth()<1)setHealth(1);return result;
    }
    @Override public void die(DamageSource source){
        if(!level().isClientSide && !rewarded){
            rewarded=true;Player p=getOwner();
            if(p!=null && kind==Kind.DEMON)for(int i=0;i<4;i++)spawn(p,Kind.HEAL,getBoundingBox().getCenter(),0,i);
            if(p!=null && kind==Kind.ORB){int count=(int)(7*Math.max(0,Math.min(1,(age-45)/495d)));CurseCombat.heal(p,count*20d);}
        }
        super.die(source);
    }
    public void finish(){ending=true;discard();}
    @Override public void remove(RemovalReason reason){if(reason==RemovalReason.DISCARDED&&!level().isClientSide&&!ending)return;super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.put("SourceItem",sourceItem.save(new CompoundTag()));if(owner!=null)tag.putUUID("CurseOwner",owner);if(portal!=null)tag.putUUID("Portal",portal);tag.putString("CurseKind",kind.name());tag.putInt("SourceAge",age);tag.putInt("Frame",frame);tag.putInt("Hand",hand);tag.putFloat("Damage",damage);tag.putBoolean("Friendly",friendly);tag.putBoolean("Flipped",flipped);tag.putBoolean("Rewarded",rewarded);tag.putDouble("VX",velocity.x);tag.putDouble("VY",velocity.y);tag.putDouble("VZ",velocity.z);
        var hits=new net.minecraft.nbt.ListTag();hitUntil.forEach((id,until)->{if(until>age){var hit=new CompoundTag();hit.putUUID("Target",id);hit.putInt("Until",until);hits.add(hit);}});tag.put("HitUntil",hits);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);sourceItem=net.minecraft.world.item.ItemStack.of(tag.getCompound("SourceItem"));owner=tag.hasUUID("CurseOwner")?tag.getUUID("CurseOwner"):null;portal=tag.hasUUID("Portal")?tag.getUUID("Portal"):null;try{kind=Kind.valueOf(tag.getString("CurseKind"));}catch(IllegalArgumentException e){kind=Kind.DEMON;}age=tag.getInt("SourceAge");frame=tag.getInt("Frame");hand=tag.getInt("Hand");damage=tag.getFloat("Damage");friendly=tag.getBoolean("Friendly");flipped=tag.getBoolean("Flipped");rewarded=tag.getBoolean("Rewarded");velocity=new Vec3(tag.getDouble("VX"),tag.getDouble("VY"),tag.getDouble("VZ"));hitUntil.clear();for(var entry:tag.getList("HitUntil",net.minecraft.nbt.Tag.TAG_COMPOUND)){var hit=(CompoundTag)entry;if(hit.hasUUID("Target")&&hit.getInt("Until")>age)hitUntil.put(hit.getUUID("Target"),hit.getInt("Until"));}publish();refreshDimensions();}
}
