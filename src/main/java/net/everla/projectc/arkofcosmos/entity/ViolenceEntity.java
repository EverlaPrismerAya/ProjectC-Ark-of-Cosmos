package net.everla.projectc.arkofcosmos.entity;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.everla.projectc.arkofcosmos.item.ViolenceItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Pinned ViolenceThrownProjectile: 60 Hz, with 2/5/4 updates per source frame. */
public final class ViolenceEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(ViolenceEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot render=new AttackRenderSnapshot();
    private final Map<UUID,Integer> hitUntil=new HashMap<>();
    private UUID owner;
    private int ownerId,age,left,hitstop;
    private boolean javelin,returning;
    private boolean grinding,finalUpdate;
    private LivingEntity grindTarget;
    private float damage,rotation,damageScale=1;
    private Vec3 velocity=Vec3.ZERO,normalVelocity=Vec3.ZERO;
    private ItemStack weapon=ItemStack.EMPTY;
    private ItemStack sourceItem=ItemStack.EMPTY;
    private AttackFrame frame=new AttackFrame(new Vec3(0,0,1),new Vec3(-1,0,0));
    private ListTag trail=new ListTag(),samples=new ListTag();

    public ViolenceEntity(EntityType<? extends ViolenceEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public static ViolenceEntity launch(Player player,boolean javelin){
        ViolenceEntity e=new ViolenceEntity(ArkOfCosmos.VIOLENCE_ATTACK.get(),player.level());
        e.owner=player.getUUID();e.ownerId=player.getId();e.javelin=javelin;e.left=javelin?60:90000;
        e.sourceItem=player.getMainHandItem();e.weapon=e.sourceItem.copy();
        e.damage=(float)player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*(javelin?15:1);
        e.frame=GalaxiaEntity.aimFrame(ArkCombat.aim(player,256).subtract(player.getBoundingBox().getCenter()),AttackFrame.capture(player).right());e.rotation=javelin?(float)(Math.PI/4):0;
        e.normalVelocity=e.velocity=e.frame.forward().scale(15*(javelin?1.6666:1)/16);
        e.setPos(player.getBoundingBox().getCenter());e.publish();player.level().addFreshEntity(e);
        ArkOfCosmos.sound(e,"violence_swing",1);return e;
    }
    public Player owner(){return owner==null?null:level().getPlayerByUUID(owner);}
    public int ownerId(){return level().isClientSide?view().getInt("Owner"):ownerId;}
    public ItemStack weapon(){return weapon;}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256*256;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        if(render!=null&&level().isClientSide&&key.equals(VIEW)&&!view().isEmpty()){
            setPos(ElementsEntity.vector(view(),"Position"));render.initialize(view());
        }
        super.onSyncedDataUpdated(key);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){render.tick(view(),"Age");return;}
        Player p=owner();
        if(!WeaponUser.active(p)){
            discard();return;
        }
        if(!javelin&&!sameWeapon(p))returning=true;
        samples=new ListTag();samples.add(snapshot());
        for(int sourceFrame=0;sourceFrame<3&&!isRemoved();sourceFrame++){
            // Recompute only at source-frame boundaries, as Terraria's extraUpdates changes do.
            int updates=javelin?(returning?4:5):2;
            for(int extra=0;extra<updates&&!isRemoved();extra++)step(p,updates,extra==updates-1);
        }
        if(!isRemoved())publish();
    }
    private boolean sameWeapon(Player p){
        // Charge is live item state; the attack's immutable snapshot must not terminate on discharge.
        return p.getMainHandItem()==sourceItem&&Curse.of(p.getMainHandItem())==Curse.of(weapon);
    }
    private void step(Player p,int updates,boolean finalUpdate){
        grinding=false;this.finalUpdate=finalUpdate;
        var oldPose=new CompoundTag();oldPose.putFloat("Rotation",rotation);ElementsEntity.vector(oldPose,"Position",position());
        trail.add(0,oldPose);while(trail.size()>60)trail.remove(trail.size()-1);
        Vec3 old=position();boolean stopped=false;
        if(javelin){
            if(hitstop>0){hitstop--;left++;velocity=normalVelocity.scale(.01);stopped=true;}
            else velocity=normalVelocity;
            if(left==1){left++;returning=true;rotation+=.45f;returnToOwner(p);}
        }else{
            if(!ViolenceCombat.channeling(p))returning=true;
            if(returning){
                returnToOwner(p);
                double ideal=frame.angle(p.getBoundingBox().getCenter().subtract(position()))-Math.PI/4;
                double delta=Math.atan2(Math.sin(ideal-rotation),Math.cos(ideal-rotation));
                rotation+=(float)(delta*.1+net.minecraft.util.Mth.clamp(delta*.9,-.25,.25));
            }else{
                Vec3 aim=aim(p);
                if(position().distanceTo(aim)<velocity.length()*.7)setPos(aim);
                else velocity=velocity.scale(.75).add(aim.subtract(position()).normalize().scale(19/16d/4));
                rotation+=.45f/updates;
            }
        }
        if(isRemoved())return;
        setPos(position().add(velocity));
        if(!level().hasChunkAt(blockPosition())){discard();return;}
        if(!javelin||!returning)collide(p,old,position());
        grinding=stopped;
        age++;left--;
        if(left<=0)discard();
        samples.add(snapshot());
    }
    /** Same crosshair cone as Galaxia, with a weapon-specific 256-block lock range. */
    private Vec3 aim(Player p){
        return ArkCombat.aim(p,256);
    }
    private void returnToOwner(Player p){
        Vec3 center=p.getBoundingBox().getCenter();setPos(position().lerp(center,.02));
        velocity=center.subtract(position()).normalize().scale(22/16d);
        if(new AABB(position(),position()).inflate(142/32d).intersects(p.getBoundingBox())){
            ArkNetwork.nearbyEffect(this,effect("violence_end",center,null,true));discard();
        }
    }
    private void collide(Player p,Vec3 old,Vec3 at){
        double radius=(javelin?45:142)/32d;
        Vec3 tip=javelin?normalVelocity.normalize().scale(70/16d):Vec3.ZERO;
        Vec3 from=old.add(tip),to=at.add(tip);
        var targets=level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(radius+2),t->WeaponUser.canHit(p,t));
        targets.sort(Comparator.comparingDouble(t->t.getBoundingBox().getCenter().distanceToSqr(from)));
        for(var t:targets){
            if(hitUntil.getOrDefault(t.getUUID(),-1)>age)continue;
            AABB bounds=t.getBoundingBox().inflate(radius);
            if(!bounds.contains(from)&&bounds.clip(from,to).isEmpty())continue;
            float before=t.getHealth();
            float amount=damage+EnchantmentHelper.getDamageBonus(weapon,t.getMobType())*(javelin?15:1)*damageScale;
            if(!ArkDamage.hurt(t,WeaponUser.source(this,p),amount))continue;
            hitUntil.put(t.getUUID(),javelin?Integer.MAX_VALUE:age+8);
            float dealt=Math.max(0,before-t.getHealth());
            p.setLastHurtMob(t);
            if(javelin){
                grindTarget=t;
                if(dealt*20>5){hitstop=(int)(20*net.minecraft.util.Mth.clamp((dealt*20-1000)/5000,0,1)*5);damage*=.9f;damageScale*=.9f;}
                WitchFlames.hex(t,80,Curse.of(weapon)==Curse.AFLAME?2:1);
            }
            int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);if(fire>0)t.setSecondsOnFire(fire*4);
            EnchantmentHelper.doPostHurtEffects(t,p);
            InnateEnchantment.combatEnchantments(weapon).forEach((enchantment,level)->enchantment.doPostAttack(p,t,level));
            Vec3 away=t.position().subtract(p.position()).normalize();
            t.knockback((9+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK))*.1,-away.x,-away.z);
            CompoundTag effect=effect("violence_hit",position().lerp(t.getBoundingBox().getCenter(),.65),t,true);
            effect.putFloat("Dealt",dealt);ArkNetwork.nearbyEffect(this,effect);
        }
    }
    private CompoundTag effect(String event,Vec3 at,LivingEntity target,boolean sound){
        CompoundTag s=snapshot();s.putString("Event",event);s.putBoolean("Sound",sound);ElementsEntity.vector(s,"At",at);
        if(target!=null){ElementsEntity.vector(s,"Target",target.getBoundingBox().getCenter());s.putFloat("TargetWidth",target.getBbWidth());s.putFloat("TargetHeight",target.getBbHeight());s.putBoolean("Organic",organic(target));}
        return s;
    }
    private CompoundTag snapshot(){
        CompoundTag s=new CompoundTag();s.putUUID("Attack",getUUID());s.putInt("Age",age);s.putInt("Owner",ownerId);s.putBoolean("Javelin",javelin);s.putBoolean("Returning",returning);s.putFloat("Rotation",rotation);
        s.putBoolean("Grinding",grinding);s.putBoolean("FinalUpdate",finalUpdate);
        if(grinding&&grindTarget!=null){s.putBoolean("Organic",organic(grindTarget));ElementsEntity.vector(s,"Target",grindTarget.getBoundingBox().getCenter());s.putFloat("TargetWidth",grindTarget.getBbWidth());s.putFloat("TargetHeight",grindTarget.getBbHeight());}
        ElementsEntity.vector(s,"Position",position());ElementsEntity.vector(s,"Velocity",velocity);
        ElementsEntity.vector(s,"F",frame.forward());ElementsEntity.vector(s,"R",frame.right());return s;
    }
    private static boolean organic(LivingEntity target){return !(target instanceof net.minecraft.world.entity.monster.AbstractSkeleton||target instanceof net.minecraft.world.entity.animal.IronGolem||target instanceof net.minecraft.world.entity.monster.Blaze);}
    private void publish(){var s=snapshot();s.put("Samples",samples.copy());s.put("Trail",trail.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    public CompoundTag renderState(float partial){
        CompoundTag state=render.state().isEmpty()?view():render.state();var list=state.getList("Samples",10);
        if(list.size()<2)return state;
        double index=net.minecraft.util.Mth.clamp(partial,0,1)*(list.size()-1);int i=(int)index;
        var a=list.getCompound(i);var b=list.getCompound(Math.min(i+1,list.size()-1));var s=state.copy();double t=index-i;
        ElementsEntity.vector(s,"Position",ElementsEntity.vector(a,"Position").lerp(ElementsEntity.vector(b,"Position"),t));
        s.putDouble("Rotation",a.getDouble("Rotation")+(b.getDouble("Rotation")-a.getDouble("Rotation"))*t);
        s.putDouble("Age",a.getDouble("Age")+(b.getDouble("Age")-a.getDouble("Age"))*t);return s;
    }
}
