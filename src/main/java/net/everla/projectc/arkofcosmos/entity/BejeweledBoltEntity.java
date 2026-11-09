package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.*;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.item.ItemStack;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import java.util.*;

/** Terraria 1.4.5 Item6171, AllGems (not the unused AllGems2 branch). Source pixels / 16, 60 Hz. */
public final class BejeweledBoltEntity extends Entity implements OwnableEntity {
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(BejeweledBoltEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID owner;
    private Vec3 velocity=Vec3.ZERO;
    private int age,penetration=3,variant;
    private float damage,scale=1;
    private ItemStack weapon=ItemStack.EMPTY;
    private ListTag samples=new ListTag();
    private boolean ending;
    private final Deque<Vec3> trail=new ArrayDeque<>();
    private final Map<UUID,Integer> hitUntil=new HashMap<>();
    public BejeweledBoltEntity(EntityType<? extends BejeweledBoltEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected AABB makeBoundingBox(){return new AABB(position(),position()).inflate(5/16d);}
    @Override public UUID getOwnerUUID(){return owner;}
    @Override public Player getOwner(){return owner==null?null:level().getPlayerByUUID(owner);}
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    public CompoundTag view(){return entityData.get(VIEW);}
    public ItemStack weapon(){return weapon;}
    public static void cast(Player p,ItemStack stack,float damage){
        AttackFrame frame=AttackFrame.capture(p);int color=p.getRandom().nextInt(7);
        for(int i=-1;i<=1;i++){
            var e=new BejeweledBoltEntity(BejeweledContent.BOLT.get(),p.level());
            e.owner=p.getUUID();e.variant=color;e.damage=damage;e.weapon=stack.copy();
            e.getPersistentData().putBoolean("ArkWitchCaptured",true);
            e.getPersistentData().putUUID("ArkWitchOwner",p.getUUID());
            e.setPos(p.getEyePosition());e.velocity=frame.direction(i*Math.PI/40).scale(6*5*1.25/16*(i==0?1.1:1));
            e.publish(new ListTag());p.level().addFreshEntity(e);
            e.sound(BejeweledContent.FLIGHT.get());
        }
        p.level().playSound(null,p.blockPosition(),BejeweledContent.CAST.get(),SoundSource.PLAYERS,1,1);
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        Player p=getOwner();if(ending||p==null||!p.isAlive()||p.isSpectator()||!level().hasChunkAt(blockPosition())){finish(false);return;}
        samples=new ListTag();
        for(int sub=0;sub<3&&!isRemoved();sub++){
            if(age>=90){finish(true);break;}
            // Dust.NewDust runs before homing/deceleration in the original AI.
            CompoundTag sample=pointTag(position(),velocity);sample.putInt("Age",age);sample.putFloat("Scale",scale);samples.add(sample);
            home(p);
            if(velocity.length()>.1/16)velocity=velocity.scale(.97);
            else {scale-=.005f;if(scale<.1f){finish(true);break;}}
            Vec3 from=position();
            // Sweep the actual 10 px projectile volume against block VoxelShapes, reflecting all
            // clipped axes once. A corner is one collision, not three penetration losses.
            Vec3 movement=Entity.collideBoundingBox(this,velocity,getBoundingBox(),level(),List.of());
            boolean x=Math.abs(movement.x-velocity.x)>1e-7,y=Math.abs(movement.y-velocity.y)>1e-7,z=Math.abs(movement.z-velocity.z)>1e-7;
            boolean bounced=x||y||z;
            setPos(from.add(movement));
            if(bounced){
                Vec3 incoming=velocity;
                setPos(position().add(x?-Math.signum(incoming.x)*.0001:0,y?-Math.signum(incoming.y)*.0001:0,z?-Math.signum(incoming.z)*.0001:0));
                velocity=new Vec3(x?-incoming.x*.8:incoming.x,y?-incoming.y*.8:incoming.y,z?-incoming.z*.8:incoming.z);
                LivingEntity target=nearest(p,150);
                if(target!=null)velocity=target.getBoundingBox().getCenter().subtract(position()).normalize().scale(incoming.length()*.8);
                trail.clear();
                if(--penetration<=0){finish(true);break;}
                sound(BejeweledContent.HIT.get());
            }
            // Source BiggerHitbox is always set by shoot=126, regardless of the selected color.
            List<Vec3> hitPoints=new ArrayList<>(trail);hitPoints.add(position());
            for(Vec3 point:hitPoints){
                for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(35/16d),t->WeaponUser.canHit(p,t))){
                    if(hitUntil.getOrDefault(target.getUUID(),-1)>age||!visible(point,target))continue;
                    if(hit(p,target,damage,6)){
                        hitUntil.put(target.getUUID(),age+60);
                        if(--penetration<=0){finish(true);break;}
                    }
                }
                if(isRemoved())break;
            }
            if(!bounced){trail.addFirst(from);while(trail.size()>5)trail.removeLast();}
            age++;
            if(!isRemoved()&&age>=90)finish(true,age-1);
        }
        if(!isRemoved()){publish(samples);visual(false);}
    }
    private boolean visible(Vec3 from,LivingEntity target){return level().clip(new ClipContext(from,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    private LivingEntity nearest(Player p,double radius){
        return level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),t->WeaponUser.enemy(p,t)&&visible(position(),t)&&t.getBoundingBox().getCenter().distanceToSqr(position())<radius*radius).stream().min(Comparator.comparingDouble(t->t.getBoundingBox().getCenter().distanceToSqr(position()))).orElse(null);
    }
    private void home(Player p){
        var target=nearest(p,1000/16d);if(target==null)return;
        Vec3 delta=target.getBoundingBox().getCenter().subtract(position());double distance=delta.length()*16,speed=velocity.length()*16;
        if(speed<16)velocity=velocity.add(velocity.normalize().scale(.1/16)).scale(1.1);
        double turn=(Math.max(0,Math.min(1,1-distance/1000))*.1+.01);
        if(speed<16)turn=(turn+.05)*remap(speed,1,3,10,1);
        turn*=remap(speed,30,40,1,0);
        Vec3 unit=velocity.normalize(),desired=delta.normalize();double angle=Math.acos(Math.max(-1,Math.min(1,unit.dot(desired))));
        if(angle>1e-7){Vec3 tangent=desired.subtract(unit.scale(unit.dot(desired))).normalize();if(tangent.lengthSqr()<1e-8)tangent=unit.cross(Math.abs(unit.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();double step=Math.min(angle,turn);velocity=unit.scale(Math.cos(step)).add(tangent.scale(Math.sin(step))).scale(velocity.length());}
        velocity=velocity.lerp(delta.normalize().scale(Math.min(delta.length(),1)),remap(distance,150,0,0,.2));
    }
    public static double remap(double v,double a,double b,double c,double d){return c+(d-c)*Math.max(0,Math.min(1,(v-a)/(b-a)));}
    private boolean hit(Player p,LivingEntity target,float amount,double knockback){
        // Terraria's 4 defense penetration becomes 0.2 armor under the existing 1:20 damage scale.
        var armor=target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        var modifier=new net.minecraft.world.entity.ai.attributes.AttributeModifier("Bejeweled penetration",-.2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if(armor!=null)armor.addTransientModifier(modifier);
        try{
            var source=new net.minecraft.world.damagesource.DamageSource(p.damageSources().indirectMagic(this,p).typeHolder(),this,p);
            boolean hit;
            if(ArkDamage.netherApollyon(target)){
                int previous=target.invulnerableTime;
                try{target.invulnerableTime=0;hit=target.hurt(source,amount);}
                finally{target.invulnerableTime=Math.max(previous,target.invulnerableTime);}
            }else hit=ArkDamage.hurt(target,source,amount);
            if(hit){
                p.setLastHurtMob(target);
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostHurtEffects(target,p);
                InnateEnchantment.combatEnchantments(weapon).forEach((enchantment,level)->enchantment.doPostAttack(p,target,level));
            }
            if(hit)target.knockback(knockback/10,getX()-target.getX(),getZ()-target.getZ());
            return hit;
        }finally{if(armor!=null)armor.removeModifier(modifier);}
    }
    public void finish(boolean explode){finish(explode,age);}
    private void finish(boolean explode,int frame){
        if(ending)return;ending=true;
        Player p=getOwner();
        if(explode&&p!=null){
            for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(5),t->WeaponUser.canHit(p,t)))
                if(hitUntil.getOrDefault(target.getUUID(),-1)!=frame+60&&visible(position(),target))hit(p,target,(float)(Math.floor(damage*20d*.35f)/20d),6*.35);
            visual(true);sound(BejeweledContent.HIT.get());
        }
        discard();
    }
    private void visual(boolean burst){
        CompoundTag data=pointTag(position(),velocity);data.putString("Event","bejeweled_fx");
        data.putUUID("Attack",getUUID());data.putInt("Owner",getOwner()==null?-1:getOwner().getId());
        data.putInt("Age",age);data.putInt("Variant",variant);data.putBoolean("Burst",burst);data.put("Samples",samples.copy());
        ArkNetwork.nearbyEffect(this,data);
    }
    private void sound(SoundEvent sound){level().playSound(null,getX(),getY(),getZ(),sound,SoundSource.PLAYERS,1,1);}
    private static CompoundTag pointTag(Vec3 at,Vec3 velocity){CompoundTag t=new CompoundTag();t.putDouble("X",at.x);t.putDouble("Y",at.y);t.putDouble("Z",at.z);t.putDouble("VX",velocity.x);t.putDouble("VY",velocity.y);t.putDouble("VZ",velocity.z);return t;}
    private void publish(ListTag samples){CompoundTag t=pointTag(position(),velocity);t.putInt("Age",age);t.putFloat("Scale",scale);t.putInt("Variant",variant);t.put("Samples",samples);entityData.set(VIEW,t);}
    public boolean protectRemoval(RemovalReason reason){return reason==RemovalReason.DISCARDED&&!level().isClientSide&&!ending&&getOwner()!=null&&getOwner().isAlive();}
    @Override public void remove(RemovalReason reason){if(!protectRemoval(reason))super.remove(reason);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){owner=null;ending=false;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
