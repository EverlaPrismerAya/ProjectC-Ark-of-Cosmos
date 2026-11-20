package net.everla.projectc.arkofcosmos.entity;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter.Attack.*;
import static net.everla.projectc.arkofcosmos.entity.MahouInfernumAttackEntity.Kind.*;

/** Infernum summons use their own source AI and share their battle owner's damage policy. */
public final class MahouInfernumSummonEntity extends Monster {
    public enum Kind { WORM, SEGMENT, CATACLYSM, CATASTROPHE, SEEKER, SHADOW, BOMBER }
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(MahouInfernumSummonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final net.minecraft.nbt.ListTag samples=new net.minecraft.nbt.ListTag();
    private UUID owner,parent,previous,soulBomb;
    private int headId;
    private Kind kind=Kind.BOMBER;
    private int age,timer,state,count,index;
    private double counter,angle,offset,pixels=16;
    private Vec3 velocity=Vec3.ZERO;
    private Vec3 plane=new Vec3(1,0,0);
    private boolean tail;
    private final Vec3[] heads={Vec3.ZERO,Vec3.ZERO,Vec3.ZERO},headVelocities={Vec3.ZERO,Vec3.ZERO,Vec3.ZERO};
    private final double[] headOffsets=new double[3],headAngles=new double[3];
    private double shadowScale=1,shadowWidth=50;
    private boolean retiring;
    public MahouInfernumSummonEntity(EntityType<? extends MahouInfernumSummonEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);setPersistenceRequired();xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(ArkOfCosmos.MAHOU_MAX_HEALTH.get(),100).add(Attributes.ATTACK_DAMAGE,0).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override public AttributeInstance getAttribute(Attribute a){return super.getAttribute(a==Attributes.MAX_HEALTH?ArkOfCosmos.MAHOU_MAX_HEALTH.get():a);}
    @Override public double getAttributeValue(Attribute a){var head=head();return a==Attributes.MAX_HEALTH&&head!=null?head.getMaxHealth():super.getAttributeValue(a==Attributes.MAX_HEALTH?ArkOfCosmos.MAHOU_MAX_HEALTH.get():a);}
    @Override public double getAttributeBaseValue(Attribute a){return super.getAttributeBaseValue(a==Attributes.MAX_HEALTH?ArkOfCosmos.MAHOU_MAX_HEALTH.get():a);}
    static MahouInfernumSummonEntity spawn(MahouTsukaiEntity boss,Kind kind,Vec3 at,int index){
        var e=new MahouInfernumSummonEntity(ArkOfCosmos.MAHOU_INFERNUM_SUMMON.get(),boss.level());e.owner=boss.getUUID();e.kind=kind;e.index=index;e.pixels=boss.infernum().pixels();e.plane=boss.infernum().plane;e.tail=boss.infernum().tailAttack();e.refreshDimensions();e.setPos(at);
        Arrays.fill(e.heads,at);
        double health=switch(kind){case WORM->1225;case CATACLYSM,CATASTROPHE->1225;case SEEKER->boss.getMaxHealth()*.02;default->100;};
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);e.setHealth(e.getMaxHealth());
        var data=CalamitasBattleData.get((ServerLevel)boss.level());data.begin(e.owner);
        if(data.add(e.owner,e.getUUID(),"mahou_"+kind.name())&&boss.level().addFreshEntity(e)){
            if(kind==Kind.WORM){UUID before=e.getUUID();for(int i=0;i<40;i++){var segment=spawn(boss,Kind.SEGMENT,at,i);segment.parent=e.getUUID();segment.headId=e.getId();segment.getAttribute(Attributes.MAX_HEALTH).setBaseValue(e.getMaxHealth());segment.previous=before;before=segment.getUUID();segment.publish();}}
            e.publish();
        }else{data.forget(e.owner,e.getUUID());e.retire();}
        return e;
    }
    int sourceOrder(){return kind==Kind.SEGMENT?index+1:kind==Kind.CATASTROPHE?1:0;}
    @Override protected net.minecraft.network.chat.Component getTypeName(){
        return switch(kind()){
            case CATACLYSM->net.minecraft.network.chat.Component.translatable("entity.arkofcosmos.mahou_saint");
            case CATASTROPHE->net.minecraft.network.chat.Component.translatable("entity.arkofcosmos.mahou_fox");
            default->super.getTypeName();
        };
    }
    Vec3 sourceVelocity(){return velocity;}
    public Kind kind(){return level().isClientSide&&!view().isEmpty()?Kind.valueOf(view().getString("Kind")):kind;}
    @Override public EntityDimensions getDimensions(Pose pose){
        Kind type=kind==null?Kind.BOMBER:kind;double pixels=this.pixels>0?this.pixels:16;
        float width=(float)((switch(type){case BOMBER->48;case SHADOW->shadowWidth;case WORM->62*1.25;case SEGMENT->(index<39&&index%2==1?20:48)*1.25;case CATACLYSM,CATASTROPHE->120;case SEEKER->40;})/pixels);
        float height=(float)((type==Kind.WORM?64*1.25:width*pixels)/pixels);
        return EntityDimensions.fixed(width,height);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(VIEW.equals(key)&&!view().isEmpty()){owner=view().hasUUID("Owner")?view().getUUID("Owner"):null;headId=view().getInt("HeadId");pixels=view().getDouble("Pixels");if(pixels<=0)pixels=16;kind=Kind.valueOf(view().getString("Kind"));index=view().getInt("Index");shadowScale=view().contains("ShadowScale")?view().getDouble("ShadowScale"):1;shadowWidth=view().contains("ShadowWidth")?view().getDouble("ShadowWidth"):50;refreshDimensions();}
    }
    public int sourceAge(){return level().isClientSide?view().getInt("Age"):age;}
    public CompoundTag view(){return entityData.get(VIEW);}
    public boolean ownedBy(MahouTsukaiEntity boss){return boss.getUUID().equals(owner);}
    public boolean tail(){return tail;}
    public double shieldDiameter(){return 162/pixels;}
    void inheritAttack(Vec3 lockedPlane,boolean desperation){plane=lockedPlane;tail=desperation;publish();}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(VIEW,new CompoundTag());}
    @Override protected void registerGoals(){}
    @Override public boolean isInWall(){return false;}
    @Override public boolean isAlliedTo(Entity other){return other.getUUID().equals(owner)||other instanceof MahouInfernumSummonEntity m&&Objects.equals(owner,m.owner)||super.isAlliedTo(other);}
    private MahouInfernumSummonEntity member(Kind type){
        if(!(level() instanceof ServerLevel server)||owner==null)return null;
        for(UUID id:CalamitasBattleData.get(server).members(owner,false))if(server.getEntity(id) instanceof MahouInfernumSummonEntity m&&m.kind==type&&m.isAlive())return m;
        return null;
    }
    // Dependants expose the leader's health; they never own a second combat health pool.
    private MahouInfernumSummonEntity head(){
        if(kind!=Kind.SEGMENT&&kind!=Kind.CATASTROPHE)return null;
        if(kind==Kind.CATASTROPHE&&level() instanceof ServerLevel)return member(Kind.CATACLYSM);
        Entity entity=level() instanceof ServerLevel server?(parent==null?null:server.getEntity(parent)):level().getEntity(headId);
        return entity instanceof MahouInfernumSummonEntity m&&(m.kind()==Kind.WORM||m.kind()==Kind.CATACLYSM)?m:null;
    }
    @Override public float getHealth(){var head=head();return head==null?super.getHealth():head.getHealth();}
    @Override public void setHealth(float health){var head=head();if(head!=null)head.setHealth(health);else if(parent==null||kind!=Kind.SEGMENT)super.setHealth(health);}
    @Override public boolean hurt(DamageSource source,float damage){
        if(kind==Kind.SHADOW||kind==Kind.BOMBER||kind==Kind.WORM&&state==0||kind==Kind.SEEKER&&count==0)return false;
        if((kind==Kind.SEGMENT||kind==Kind.CATASTROPHE)&&level() instanceof ServerLevel server){
            Entity leader=kind==Kind.SEGMENT?(parent==null?null:server.getEntity(parent)):member(Kind.CATACLYSM);
            return leader instanceof MahouInfernumSummonEntity m&&m.hurt(source,damage);
        }
        return super.hurt(source,damage);
    }
    @Override public void die(DamageSource source){
        super.die(source);if(level() instanceof ServerLevel server&&owner!=null){CalamitasBattleData.get(server).died(owner,getUUID());
            if(kind==Kind.WORM){CalamitasBattleData.get(server).retireRole(owner,"mahou_SEGMENT");}
            if(kind==Kind.CATACLYSM){var partner=member(Kind.CATASTROPHE);CalamitasBattleData.get(server).retireRole(owner,"mahou_CATASTROPHE");if(partner!=null)partner.retire();}}
    }
    @Override public void tick(){
        super.tick();setDeltaMovement(Vec3.ZERO);
        if(level() instanceof ServerLevel server&&owner!=null){var data=CalamitasBattleData.get(server);if(!data.active(owner)||data.confirmedDead(owner,getUUID()))retire();}
    }
    void sourceStep(MahouTsukaiEntity boss){
        if(!isAlive()||isRemoved()||boss.getTarget()==null)return;age++;timer++;var ai=boss.infernum();
        if(kind==Kind.SEGMENT){
            var server=(ServerLevel)level();if(parent==null||previous==null)return;
            if(CalamitasBattleData.get(server).confirmedDead(owner,parent)||server.getEntity(parent) instanceof MahouInfernumSummonEntity head&&!head.isAlive()){retire();return;}
            if(server.getEntity(previous) instanceof MahouInfernumSummonEntity before){Vec3 delta=position().subtract(before.position());setPos(before.position().add(ai.unit(delta).scale((index<39&&index%2==1?12.5:30)/ai.pixels())));}
        }else switch(kind){
            case BOMBER->bomber(ai);
            case SHADOW->shadow(ai);
            case WORM->worm(ai);
            case SEEKER->seeker(ai);
            case CATACLYSM,CATASTROPHE->brother(ai);
            default->{}
        }
        if(kind==Kind.SHADOW)updateHeads(ai);
        setPos(position().add(velocity));
        if(boss.getTarget()==null)return;
        Vec3 direction=boss.getTarget().position().subtract(position());
        if(kind==Kind.WORM)direction=velocity;
        else if(kind==Kind.SEGMENT&&previous!=null&&((ServerLevel)level()).getEntity(previous) instanceof MahouInfernumSummonEntity before)direction=before.position().subtract(position());
        if(kind==Kind.WORM||kind==Kind.SEGMENT)setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));
        setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));yBodyRot=getYRot();
        if(kind==Kind.WORM&&state>0||kind==Kind.SEGMENT&&parent!=null&&((ServerLevel)level()).getEntity(parent) instanceof MahouInfernumSummonEntity head&&head.state>0||kind==Kind.SEEKER&&count>0||kind==Kind.BOMBER&&age>=90||kind==Kind.SHADOW&&state==1&&ai.attack==ShadowDemon_ShadowGigablastsAndCharges||kind==Kind.CATACLYSM||kind==Kind.CATASTROPHE){
            for(var victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox(),v->v!=boss&&!isAlliedTo(v)&&v.isAlive())){
                var source=new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK),this,boss);
                if(net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(victim,source,kind==Kind.BOMBER?16:kind==Kind.SEGMENT?200/30f:14)&&kind==Kind.BOMBER){fire(ai,EXPLOSION,position(),Vec3.ZERO,48,0,26/ai.pixels());retire();break;}
            }
        }
        var sample=new CompoundTag();sample.putInt("Age",age);vectorSample(sample);samples.add(sample);while(samples.size()>4)samples.remove(0);
        if(age%3==0)publish();
    }
    private void steer(MahouInfernumAI ai,Vec3 at,double speed,double acceleration){Vec3 desired=ai.unit(at.subtract(position())).scale(speed/ai.pixels());velocity=net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasCombat.fly(velocity,desired,acceleration/ai.pixels());}
    private Vec3 target(MahouInfernumAI ai){return ai.boss.getTarget().getBoundingBox().getCenter();}
    private void fire(MahouInfernumAI ai,MahouInfernumAttackEntity.Kind type,Vec3 at,Vec3 v,int life,float damage,double r){MahouInfernumAttackEntity.spawn(ai.boss,type,at,v,kind==Kind.BOMBER||kind==Kind.SEEKER||kind==Kind.WORM?plane:ai.plane,life,damage,r,tail);}
    private void radial(MahouInfernumAI ai,int n,double speed,MahouInfernumAttackEntity.Kind type){for(int i=0;i<n;i++)fire(ai,type,position(),ai.rotate(ai.plane,i*Math.PI*2/n).scale(speed/ai.pixels()),300,14,0);}
    private void bomber(MahouInfernumAI ai){
        if(age<45)velocity=velocity.lerp(new Vec3(0,5/ai.pixels(),0),.06);
        else if(age<90)velocity=velocity.subtract(velocity.length()>0?velocity.normalize().scale(Math.min(velocity.length(),.4/ai.pixels())):Vec3.ZERO).scale(.95);
        else velocity=velocity.scale(31/32d).add(ai.unit(target(ai).subtract(position())).scale(26.5/(32*ai.pixels())));
        for(UUID id:CalamitasBattleData.get((ServerLevel)level()).members(owner,false))
            if(((ServerLevel)level()).getEntity(id) instanceof MahouInfernumSummonEntity other&&other!=this&&other.kind==Kind.BOMBER&&position().distanceTo(other.position())<60/ai.pixels())
                velocity=velocity.add(ai.unit(position().subtract(other.position())).scale(.08/ai.pixels()));
        if(age>=420){
            fire(ai,EXPLOSION,position(),Vec3.ZERO,48,0,26/ai.pixels());
            retire();
        }
    }
    private void seeker(MahouInfernumAI ai){
        plane=ai.plane;
        if(ai.attack!=SummonSeekers){retire();return;}
        boolean outer=index>6;if(ai.timer>=135&&timer>=10&&count<=4)count++;double a=index*Math.PI/3+(outer?1:-1)*Math.toRadians(Math.max(0,timer-10)*.5);
        setPos(ai.arena.origin().add(plane.scale(Math.cos(a)).add(0,-Math.sin(a),0).scale((outer?1000:500)/ai.pixels())));velocity=Vec3.ZERO;
        if(ai.timer<135){timer=0;return;}
        if(timer%136==135&&position().distanceTo(target(ai))>400/ai.pixels()){
            double speed=outer?8:position().distanceTo(target(ai))*ai.pixels()*.012+9;
            var bomb=MahouInfernumAttackEntity.spawn(ai.boss,BOMB,position(),ai.rotate(ai.unit(target(ai).subtract(position())),(getRandom().nextDouble()*2-1)*.3).scale(speed/ai.pixels()),ai.plane,90,0,600/ai.pixels(),false);bomb.parameter(outer?1:0);
        }
    }
    private void shadow(MahouInfernumAI ai){
        double oldScale=shadowScale;boolean active=ai.attack==SummonShadowDemon||ai.attack==ShadowDemon_ReleaseExplodingShadowBlasts||ai.attack==ShadowDemon_ShadowGigablastsAndCharges;
        shadowScale=Math.max(.0001,Math.min(1,shadowScale+(active?.02:-.02)));
        if(oldScale!=shadowScale){shadowWidth=shadowScale*250*.85;refreshDimensions();}
        if(ai.attack!=ShadowDemon_ReleaseExplodingShadowBlasts&&ai.attack!=ShadowDemon_ShadowGigablastsAndCharges){state=count=0;timer=0;steer(ai,target(ai),11,.2);return;}
        boolean blasts=ai.attack==ShadowDemon_ReleaseExplodingShadowBlasts;
        Vec3 hoverAt=target(ai).subtract(ai.plane.scale((target(ai).subtract(position()).dot(ai.plane)>=0?1:-1)*500/ai.pixels())).add(0,(blasts?275:120)/ai.pixels(),0);
        if(state==0){
            if(blasts&&timer>=60)velocity=velocity.scale(.98);
            else if(blasts)steer(ai,hoverAt,11,.2);
            else{Vec3 desired=ai.unit(hoverAt.subtract(position())).scale(Math.min(hoverAt.distanceTo(position()),30/ai.pixels()));velocity=net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasCombat.fly(velocity,desired,.4/ai.pixels()).lerp(desired,.12);}
            int hover=blasts?(count==0?210:105):(count==0?247:67);
            if(timer>=hover||!blasts&&count>=1&&position().distanceTo(hoverAt)<75/ai.pixels()){state=1;timer=0;if(!blasts){velocity=ai.unit(target(ai).subtract(position())).scale(36/ai.pixels());fire(ai,SHADOW_GIGA,heads[1],ai.unit(target(ai).subtract(heads[1])).scale(12.5/ai.pixels()),90,15,0);}}
        }else if(blasts&&state==1){
            if(timer==8)for(int head=0;head<3;head++){Vec3 at=heads[head];for(int j=0;j<3;j++)fire(ai,SHADOW_BLAST,at,ai.rotate(ai.planarAim(target(ai).subtract(at)),-.44+j*.44+(getRandom().nextDouble()*2-1)*.04).scale(23/ai.pixels()),32,15,0);}
            if(timer>=20){state=2;timer=0;velocity=ai.unit(target(ai).subtract(position())).scale(32/ai.pixels());}
        }else{
            if(blasts)velocity=velocity.scale(.984);
            else if(timer%8==7){Vec3 head=heads[getRandom().nextInt(3)];if(head.distanceTo(target(ai))>300/ai.pixels())fire(ai,SHADOW_BOLT,head,ai.unit(target(ai).subtract(head)).scale(14.5/ai.pixels()),180,14,0);}
            if(timer>=(blasts?40:56)){state=timer=0;if(++count>=(blasts?4:5)){count=0;ai.next();}}
        }
    }
    private void updateHeads(MahouInfernumAI ai){
        if(age%160==2)for(int i=0;i<3;i++){headOffsets[i]=180+getRandom().nextDouble()*120;headAngles[i]=-.87+.87*i+(getRandom().nextDouble()*2-1)*.17;}
        for(int i=0;i<3;i++){
            Vec3 destination=position().add(ai.rotate(new Vec3(0,1,0),headAngles[i]).scale(headOffsets[i]*shadowScale/ai.pixels()));
            Vec3 delta=destination.subtract(heads[i]),desired=ai.unit(delta).scale(Math.min(delta.length(),velocity.length()+20/ai.pixels()));
            Vec3 next=headVelocities[i].lerp(desired,.085),change=desired.subtract(next);
            headVelocities[i]=next.add(ai.unit(change).scale(Math.min(change.length(),.8/ai.pixels())));heads[i]=heads[i].add(headVelocities[i]);
        }
    }
    private Vec3 rotate(Vec3 vector,double angle){
        Vec3 axis=plane.cross(new Vec3(0,-1,0)).normalize();
        return vector.scale(Math.cos(angle)).add(axis.cross(vector).scale(Math.sin(angle))).add(axis.scale(axis.dot(vector)*(1-Math.cos(angle))));
    }
    private void worm(MahouInfernumAI ai){
        if(timer==1||state==5&&timer%172==1){Vec3 horizontal=target(ai).subtract(position()).multiply(1,0,1);if(horizontal.lengthSqr()>1e-9)plane=horizontal.normalize();}
        double speed=ai.rage()?76:36;
        if(state==0){
            Vec3 toward=ai.unit(target(ai).subtract(position()));double t=Math.max(0,Math.min(1,(timer-40)/70d));
            Vec3 initial=toward.multiply(7,-13,7).scale(1/ai.pixels());
            Vec3 desired=position().distanceTo(target(ai))>180/ai.pixels()?initial.lerp(toward.scale(18/ai.pixels()),t):initial;
            velocity=velocity.lerp(desired,.035);if(timer>=150){state=1;timer=count=0;}}
        else if(state==1){
            if(timer==1)counter=getRandom().nextBoolean()?1:-1;
            if(timer%33==32){offset+=.23;if(offset>=1){angle+=counter*Math.PI/2;offset--;}
                Vec3 off=rotate(plane.scale((-1080+2160*offset)/ai.pixels()).add(0,1080/ai.pixels(),0),angle);var b=MahouInfernumAttackEntity.spawn(ai.boss,BOMB,target(ai).add(off),rotate(new Vec3(0,-17/ai.pixels(),0),angle),plane,180,0,720/ai.pixels(),false);b.parameter(1);}
            if(timer<90){if(timer%30>=25)velocity=ai.unit(velocity.lerp(ai.unit(target(ai).subtract(position())).scale(1/ai.pixels()),.4)).scale(speed*.425/ai.pixels());else if(position().distanceTo(target(ai))>200/ai.pixels()){double t=timer*Math.PI*2/100;velocity=rotate(velocity,(Math.sin(t*Math.PI)+Math.sin(t*Math.E))*.05);}}
            else if(timer<102){Vec3 desired=ai.unit(target(ai).subtract(position())).scale(speed/ai.pixels());velocity=velocity.lerp(desired,.2);if(velocity.length()<speed*.4/ai.pixels())velocity=velocity.scale(1.32);if(timer==101)velocity=desired;}
            if(timer>=144){timer=0;if(++count>=4){state=2;count=0;}}
        }else if(state==2){
            Vec3 at=target(ai).subtract(plane.scale((target(ai).subtract(position()).dot(plane)>=0?1:-1)*500/ai.pixels())).add(0,400/ai.pixels(),0);
            Vec3 desired=ai.unit(at.subtract(position())).scale((25+timer/15d)/ai.pixels()),delta=desired.subtract(velocity);
            velocity=ai.turnTowards(velocity.add(ai.unit(delta).scale(Math.min(delta.length(),.4/ai.pixels()))),desired,.05);
            if(timer>=270||position().distanceTo(at)<95/ai.pixels()){state=3;timer=0;}
        }else if(state==3){Vec3 desired=ai.unit(target(ai).subtract(position())).scale((ai.rage()?78:36.5)/ai.pixels());velocity=velocity.lerp(desired,.15);if(timer>=10){velocity=desired;state=4;timer=0;}}
        else if(state==4){if(timer%4==3){Vec3 d=rotate(ai.unit(velocity),Math.PI/2).scale(4/ai.pixels());fire(ai,BONE,position(),d,300,14,0);fire(ai,BONE,position(),d.scale(-1),300,14,0);}
            if(timer>=40){timer=0;state=++count>=3?5:2;if(state==5)count=0;}}
        else if(state==5){int wrap=timer%172;
            if(wrap<75&&position().distanceTo(target(ai))>600/ai.pixels())velocity=ai.unit(ai.turnTowards(velocity,target(ai).subtract(position()),.06)).scale(velocity.length()+(11/ai.pixels()-velocity.length())*.1);
            if(velocity.length()>11.5/ai.pixels())velocity=velocity.normalize().scale(11.49/ai.pixels());
            if(wrap==1){
                if(count>=3){state=1;timer=count=0;offset=angle=counter=0;return;}
                var bomb=MahouInfernumAttackEntity.spawn(ai.boss,SOUL_BOMB,position(),Vec3.ZERO,plane,360,16,0,ai.tailAttack());
                bomb.holdFor(getUUID());soulBomb=bomb.getUUID();count++;
            }
            if(wrap==75 && soulBomb!=null && ((ServerLevel)level()).getEntity(soulBomb) instanceof MahouInfernumAttackEntity bomb)
                bomb.launchSoul(ai.unit(target(ai).subtract(bomb.position())).scale(23/ai.pixels()));
        }
    }
    private void brother(MahouInfernumAI ai){
        boolean leader=kind==Kind.CATACLYSM;var first=leader?this:member(Kind.CATACLYSM);
        if(first==null){if(CalamitasBattleData.get((ServerLevel)level()).living(owner,"mahou_CATACLYSM")==0)retire();return;}
        if(!leader){timer=first.timer;state=first.state;setHealth(first.getHealth());}
        double side=leader?-1:1,ratio=first.getHealth()/first.getMaxHealth();
        if(state==0){steer(ai,target(ai).add(ai.plane.scale(side*720/ai.pixels())),30,.925);if(timer>=75&&leader){state=1;timer=0;}}
        else if(state==1){
            int charge=(ratio<.5?38:48),duration=ratio<.25?40:50;int wrap=timer%(charge+duration);double speed=ai.rage()?100:ratio<.25?61:55;
            Vec3 at=target(ai).add(ai.plane.scale(-side*600/ai.pixels()));
            if(wrap==1){setPos(at);velocity=Vec3.ZERO;ai.warning(position(),target(ai).subtract(position()),duration);}
            if(wrap==duration){velocity=ai.unit(target(ai).subtract(position())).scale(speed/ai.pixels());radial(ai,ai.rage()?60:11,10,LOST_SOUL);}
            if(wrap<charge){if(wrap<charge-8)fastHover(ai,at);else velocity=velocity.scale(.8);}
            if(timer>=(charge+duration)*3+1&&leader){state=2;timer=0;counter=0;}
        }else if(state==2){int wrap=timer%300;
            Vec3 at=target(ai).add(ai.plane.scale(-side*(timer/300%2==0?550:-550)/ai.pixels())).add(0,leader?255/ai.pixels():0,0);
            if(timer>=600)velocity=velocity.scale(.95);
            else if(wrap<30)fastHover(ai,at);
            else if(wrap<60){if(position().distanceTo(target(ai))>320/ai.pixels())velocity=velocity.scale(.9);else setPos(position().subtract(ai.unit(target(ai).subtract(position())).scale(10/ai.pixels())));}
            else if(leader)velocity=Vec3.ZERO;else fastHover(ai,at);
            counter+=leader?4.75:3;int threshold=ai.rage()?12:(leader?120:136)-(ratio<.5?13:0)-(ratio<.25?13:0);
            if(wrap>=60&&timer<600&&counter>=threshold){counter=0;Vec3 v=leader?ai.rotate(new Vec3(0,(ai.rage()?28.5:17.5)/ai.pixels(),0),(getRandom().nextDouble()*2-1)*Math.PI/9).scale(.9+getRandom().nextDouble()*.225):ai.plane.scale(Math.signum(target(ai).subtract(position()).dot(ai.plane))*11.75/ai.pixels());fire(ai,leader?REDIRECT_HELLFIRE:SLASH,leader?position().add(v.scale(5.4)):position().add(ai.plane.scale(Math.signum(target(ai).subtract(position()).dot(ai.plane))*125/ai.pixels())),v,leader?300:1500,14,0);}
            if(timer>=660&&leader){state=3;timer=0;counter=0;}
        }else{
            steer(ai,target(ai).add(ai.plane.scale(side*720/ai.pixels())).add(0,Math.sin(timer*Math.PI*2/420)*-side*400/ai.pixels(),0),30,.95);
            counter+=1.85+(3.1-1.85)*(1-ratio);
            if(counter>=(leader?105:85)){counter=0;fire(ai,leader?FIST:SLASH,position(),ai.plane.scale(Math.signum(target(ai).subtract(position()).dot(ai.plane))*(ai.rage()?24:9.5)/ai.pixels()),leader?1200:1500,14,0);}
            int rate=ai.rage()?12:55-(ratio<.5?5:0)-(ratio<.25?9:0);if(timer%rate==rate-1)radial(ai,9,8.5,LOST_SOUL);
            if(timer>=420&&leader){state=1;timer=0;counter=0;}
        }
    }
    private void fastHover(MahouInfernumAI ai,Vec3 at){
        Vec3 delta=at.subtract(position());double distance=delta.length()*ai.pixels();
        double blend=Math.max(0,Math.min(1,(distance-360)/720));
        velocity=ai.unit(delta).scale(Math.min(distance,24)/ai.pixels()).lerp(delta.scale(.0125),blend);
    }
    void retire(){retiring=true;if(level() instanceof ServerLevel server&&owner!=null){var data=CalamitasBattleData.get(server);data.died(owner,getUUID());if(kind==Kind.WORM)data.retireRole(owner,"mahou_SEGMENT");}discard();}
    @Override public void remove(RemovalReason reason){if(reason.shouldDestroy()&&!retiring&&level() instanceof ServerLevel server&&owner!=null)CalamitasBattleData.get(server).died(owner,getUUID());super.remove(reason);}
    private CompoundTag snapshot(){var t=new CompoundTag();t.putString("Kind",kind.name());var head=head();t.putInt("HeadId",head==null?headId:head.getId());t.putInt("Age",age);t.putInt("Timer",timer);t.putInt("State",state);t.putInt("Count",count);t.putInt("Index",index);t.putDouble("Counter",counter);t.putDouble("Angle",angle);t.putDouble("Offset",offset);t.putDouble("Pixels",pixels);t.putBoolean("Tail",tail);t.putDouble("ShadowScale",shadowScale);t.putDouble("ShadowWidth",shadowWidth);MahouInfernumAttackEntity.vector(t,"Plane",plane);if(owner!=null)t.putUUID("Owner",owner);if(parent!=null)t.putUUID("Parent",parent);if(previous!=null)t.putUUID("Previous",previous);if(soulBomb!=null)t.putUUID("SoulBomb",soulBomb);MahouInfernumAttackEntity.vector(t,"Velocity",velocity);for(int i=0;i<3;i++){MahouInfernumAttackEntity.vector(t,"Head"+i,heads[i]);MahouInfernumAttackEntity.vector(t,"HeadVelocity"+i,headVelocities[i]);t.putDouble("HeadOffset"+i,headOffsets[i]);t.putDouble("HeadAngle"+i,headAngles[i]);}return t;}
    private void vectorSample(CompoundTag t){MahouInfernumAttackEntity.vector(t,"Position",position());MahouInfernumAttackEntity.vector(t,"Velocity",velocity);}
    private void publish(){var t=snapshot();t.put("Samples",samples.copy());entityData.set(VIEW,t);}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("Infernum",snapshot());}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);var s=t.getCompound("Infernum");if(s.isEmpty())return;try{kind=Kind.valueOf(s.getString("Kind"));}catch(IllegalArgumentException e){return;}owner=s.hasUUID("Owner")?s.getUUID("Owner"):null;parent=s.hasUUID("Parent")?s.getUUID("Parent"):null;previous=s.hasUUID("Previous")?s.getUUID("Previous"):null;soulBomb=s.hasUUID("SoulBomb")?s.getUUID("SoulBomb"):null;age=s.getInt("Age");timer=s.getInt("Timer");state=s.getInt("State");count=s.getInt("Count");index=s.getInt("Index");counter=s.getDouble("Counter");angle=s.getDouble("Angle");offset=s.getDouble("Offset");pixels=s.getDouble("Pixels");if(!Double.isFinite(pixels)||pixels<=0)pixels=16;shadowScale=s.contains("ShadowScale")?s.getDouble("ShadowScale"):1;shadowWidth=s.contains("ShadowWidth")?s.getDouble("ShadowWidth"):50;tail=s.getBoolean("Tail");plane=MahouInfernumAttackEntity.vector(s,"Plane");plane=plane.lengthSqr()>1e-9?plane.normalize():new Vec3(1,0,0);refreshDimensions();velocity=MahouInfernumAttackEntity.vector(s,"Velocity");for(int i=0;i<3;i++){heads[i]=s.contains("Head"+i+"X")?MahouInfernumAttackEntity.vector(s,"Head"+i):position();headVelocities[i]=MahouInfernumAttackEntity.vector(s,"HeadVelocity"+i);headOffsets[i]=s.getDouble("HeadOffset"+i);headAngles[i]=s.getDouble("HeadAngle"+i);}publish();}
}

