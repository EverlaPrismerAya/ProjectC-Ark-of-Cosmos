package net.everla.projectc.arkofcosmos.entity;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Terraria 1.4.5.8 item 4722 / projectile 857: 60 Hz, two motion updates per frame. */
public final class FirstFractalEntity extends Entity implements OwnableEntity {
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(FirstFractalEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot render=new AttackRenderSnapshot();
    private final Map<UUID,Integer> hitUntil=new HashMap<>();
    private UUID owner;
    private int ownerId,age,variant,delay;
    private float hue;
    private double curve;
    private Vec3 velocity=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(new Vec3(0,0,1),new Vec3(1,0,0));
    private ItemStack weapon=ItemStack.EMPTY,sourceItem=ItemStack.EMPTY;
    private boolean ending;
    private ListTag samples=new ListTag();
    public FirstFractalEntity(EntityType<? extends FirstFractalEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override public UUID getOwnerUUID(){return owner;}
    @Override public Player getOwner(){return owner==null?null:level().getPlayerByUUID(owner);}
    public int ownerId(){return level().isClientSide?view().getInt("Owner"):ownerId;}
    public ItemStack weapon(){return weapon;}
    public static boolean inSearch(Vec3 offset){return (offset.x*offset.x+offset.z*offset.z)/(31.25*31.25)+offset.y*offset.y/625<=1;}
    public static double opacity(double age){return clamp(age/12)*clamp((60-age)/12);}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    public static Vec3 rotate(Vec3 velocity,AttackFrame plane,double angle){
        return plane.direction(plane.angle(velocity)+angle).scale(velocity.length());
    }
    public static Vec3 start(Vec3 aim,Vec3 velocity,AttackFrame plane,double curve){
        Vec3 offset=Vec3.ZERO;
        for(int i=0;i<30;i++){offset=offset.add(velocity);velocity=rotate(velocity,plane,curve);}
        return aim.subtract(offset);
    }
    private static final double LOCK_RANGE=64,LOCK_COS=Math.cos(Math.toRadians(15));
    /** Visible enemy nearest the crosshair (smallest angle, then distance) within a 15° cone. */
    private static LivingEntity crosshairTarget(Player p,Vec3 look){
        Vec3 eye=p.getEyePosition();LivingEntity best=null;double bestCos=LOCK_COS,bestDistance=Double.MAX_VALUE;
        for(var t:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().expandTowards(look.scale(LOCK_RANGE)).inflate(LOCK_RANGE*Math.tan(Math.toRadians(15))),t->WeaponUser.enemy(p,t))){
            Vec3 offset=t.getBoundingBox().getCenter().subtract(eye);double distance=offset.length();
            if(distance<.001||distance>LOCK_RANGE)continue;
            double cos=offset.dot(look)/distance;
            if((cos>bestCos+1e-6||Math.abs(cos-bestCos)<=1e-6&&distance<bestDistance)&&visible(p,t)){best=t;bestCos=cos;bestDistance=distance;}
        }
        return best;
    }
    /** Tall or partly buried targets (emerging obsidian monoliths) may have their eye point inside blocks: accept any of centre, top or eye. */
    private static boolean visible(Player p,LivingEntity t){
        var box=t.getBoundingBox();Vec3 eye=p.getEyePosition();
        for(Vec3 point:new Vec3[]{box.getCenter(),new Vec3(box.getCenter().x,box.maxY-.1,box.getCenter().z),t.getEyePosition()})
            if(p.level().clip(new net.minecraft.world.level.ClipContext(eye,point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS)return true;
        return false;
    }
    public static FirstFractalEntity launch(Player p,double hue,int delay){
        var e=new FirstFractalEntity(ArkOfCosmos.FIRST_FRACTAL_ATTACK.get(),p.level());var random=p.getRandom();
        e.owner=p.getUUID();e.ownerId=p.getId();e.sourceItem=p.getMainHandItem();e.weapon=e.sourceItem.copy();
        e.hue=(float)hue;e.variant=random.nextInt(15);e.delay=delay;
        Vec3 center=p.getBoundingBox().getCenter();
        var targets=p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(31.25,25,31.25),t->WeaponUser.enemy(p,t)&&inSearch(t.getBoundingBox().getCenter().subtract(center)));
        Vec3 aim;
        // Random planes containing the aim axis preserve the source curve while filling 3D space.
        Vec3 axis=p.getLookAngle().normalize();AttackFrame view=AttackFrame.capture(p);
        double roll=random.nextDouble()*Math.PI*2;
        Vec3 side=view.right().scale(Math.cos(roll)).add(view.normal().scale(Math.sin(roll)));
        e.frame=new AttackFrame(axis,side);
        Vec3 direction=e.frame.direction(random.nextDouble()*Math.PI*2);
        // Crosshair lock first; nearby enemies are only a self-defence fallback when nothing is under the crosshair.
        LivingEntity locked=crosshairTarget(p,axis);
        if(locked!=null||!targets.isEmpty()){
            var target=locked!=null?locked:targets.get(random.nextInt(targets.size()));
            // Minecraft velocity is blocks/tick; Terraria predicts 20 of its 60 Hz frames.
            aim=target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(20d/3));
        }else{
            Vec3 end=p.getEyePosition().add(axis.scale(43.75));
            aim=p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getLocation();
            Vec3 offset=aim.subtract(center);double length=offset.length();
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*1.5;
            aim=aim.add(e.frame.direction(angle).scale(radius));
            if(length>43.75)aim=center.add(offset.scale(43.75/length));
            Vec3 movement=p.getDeltaMovement().scale(16d/3);
            double weight=clamp(movement.length()/6)*.8;
            Vec3 blend=direction.scale(1-weight).add(movement.scale(weight));
            if(blend.lengthSqr()>1e-12){
                direction=blend.normalize();
                // Keep velocity and its rotation axis coplanar after incorporating player movement.
                e.frame=GalaxiaEntity.aimFrame(direction,e.frame.right());
            }
        }
        e.curve=(random.nextDouble()*2-1)*Math.PI/120;
        e.velocity=direction.scale((12+random.nextDouble()*2)/16);
        e.setPos(start(aim,e.velocity,e.frame,e.curve));
        e.publish();p.level().addFreshEntity(e);ArkOfCosmos.sound(p,"biome_swing",1);return e;
    }
    public void discardAttack(){ending=true;try{discard();}finally{ending=false;}}
    @Override public void remove(RemovalReason reason){
        if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ending&&age<59&&WeaponUser.active(getOwner()))return;
        super.remove(reason);
    }
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256*256;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        if(render!=null&&level().isClientSide&&key.equals(VIEW)&&!view().isEmpty()){setPos(ElementsEntity.vector(view(),"Position"));render.initialize(view());}
        super.onSyncedDataUpdated(key);
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide){render.tick(view(),"Age");return;}
        Player p=getOwner();
        if(!WeaponUser.active(p)||p.getMainHandItem()!=sourceItem){discardAttack();return;}
        // Publish the terminal source sample for one network tick without extending collision life.
        if(age>=59){discardAttack();return;}
        samples=new ListTag();samples.add(snapshot(false));
        for(int i=0;i<6&&!isRemoved();i++){
            if(delay>0){delay--;samples.add(snapshot(false));continue;}
            if(++age>=59){samples.add(snapshot(false));break;}
            velocity=rotate(velocity,frame,curve);
            Vec3 previous=position();setPos(previous.add(velocity));hit(p,previous,position());
            samples.add(snapshot(age>7&&random.nextInt(15)==0));
        }
        publish();
    }
    private void hit(Player p,Vec3 from,Vec3 to){
        for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1),t->WeaponUser.canHit(p,t))){
            if(hitUntil.getOrDefault(target.getUUID(),-1)>age)continue;
            AABB box=target.getBoundingBox().inflate(1);
            if(!box.contains(from)&&box.clip(from,to).isEmpty())continue;
            float amount=PlayerWeaponDamage.scale(p,weapon,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage("first_fractal"))
                    +EnchantmentHelper.getDamageBonus(weapon,target.getMobType());
            if(!ArkDamage.hurt(target,WeaponUser.source(this,p),amount))continue;
            hitUntil.put(target.getUUID(),age+10);p.setLastHurtMob(target);
            int fire=weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT);if(fire>0)target.setSecondsOnFire(fire*4);
            EnchantmentHelper.doPostHurtEffects(target,p);
            InnateEnchantment.combatEnchantments(weapon).forEach((enchantment,level)->enchantment.doPostAttack(p,target,level));
            target.knockback((6.5+weapon.getEnchantmentLevel(Enchantments.KNOCKBACK))*.1,-velocity.x,-velocity.z);
        }
    }
    private CompoundTag snapshot(boolean dust){
        var s=new CompoundTag();s.putInt("Age",age);s.putInt("Owner",ownerId);s.putInt("Variant",variant);s.putFloat("Hue",hue);s.putBoolean("Dust",dust);
        s.putDouble("Curve",curve);ElementsEntity.vector(s,"Position",position());ElementsEntity.vector(s,"Velocity",velocity);
        ElementsEntity.vector(s,"F",frame.forward());ElementsEntity.vector(s,"R",frame.right());return s;
    }
    private void publish(){var s=snapshot(false);s.put("Samples",samples.copy());entityData.set(VIEW,s);}
    public CompoundTag view(){return entityData.get(VIEW);}
    public CompoundTag renderState(float partial){
        CompoundTag state=render.state().isEmpty()?view():render.state();var list=state.getList("Samples",10);
        if(list.size()<2)return state;
        double index=Math.max(0,Math.min(1,partial))*(list.size()-1);int i=(int)index;double t=index-i;
        var a=list.getCompound(i);var b=list.getCompound(Math.min(i+1,list.size()-1));var s=state.copy();
        ElementsEntity.vector(s,"Position",ElementsEntity.vector(a,"Position").lerp(ElementsEntity.vector(b,"Position"),t));
        ElementsEntity.vector(s,"Velocity",ElementsEntity.vector(a,"Velocity").lerp(ElementsEntity.vector(b,"Velocity"),t));
        s.putDouble("Age",a.getDouble("Age")+(b.getDouble("Age")-a.getDouble("Age"))*t);return s;
    }
}
