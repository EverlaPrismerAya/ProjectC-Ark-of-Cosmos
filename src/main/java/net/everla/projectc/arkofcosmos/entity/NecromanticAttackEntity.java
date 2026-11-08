package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.enchant.WitchSouls;
import net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Calamity 60 Hz logic: three source frames per server tick, in blocks (16 source px). */
public final class NecromanticAttackEntity extends Entity implements OwnableEntity {
    public enum Kind { RANCOR, CONGREGATION, ARM, SMALL_CINDER, LARGE_CINDER, SMALL_SPIRIT, SPIRIT_DUST }
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(NecromanticAttackEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private Kind kind=Kind.RANCOR;
    private UUID owner,parent;
    private InteractionHand hand=InteractionHand.MAIN_HAND;
    private ItemStack weapon=ItemStack.EMPTY;
    private int age,lifetime=90000,death,variant;
    private float damage,scale=1,opacity=1,rotation;
    private double laserLength;
    private boolean ending,strong,returning;
    private Vec3 velocity=Vec3.ZERO,direction=new Vec3(0,0,1),hover=Vec3.ZERO,anchor=Vec3.ZERO;
    private final Deque<Vec3> trail=new ArrayDeque<>();
    private final Map<UUID,Long> hitUntil=new HashMap<>();
    private static final Map<LivingEntity,EnumMap<Kind,Long>> SHARED_HITS=new WeakHashMap<>();
    private ListTag samples=new ListTag();
    public NecromanticAttackEntity(EntityType<? extends NecromanticAttackEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public Kind kind(){return level().isClientSide?Kind.values()[Math.min(Kind.values().length-1,Math.max(0,view().getInt("Kind")))]:kind;}
    public CompoundTag view(){return entityData.get(VIEW);}
    public int age(){return level().isClientSide?view().getInt("Age"):age;}
    public float power(){return (float)Math.pow(lerpValue(15,840,age()),4);}
    public static double lerpValue(double a,double b,double value){return Math.max(0,Math.min(1,(value-a)/(b-a)));}
    public static double smooth(double a,double b,double value){double t=Math.max(0,Math.min(1,value));return a+(b-a)*t*t*(3-2*t);}
    public double diameter(){return smooth(54,185,power())/16;}
    @Override public UUID getOwnerUUID(){return owner;}
    @Override public Player getOwner(){return owner==null?null:level().getPlayerByUUID(owner);}
    public ItemStack weapon(){return weapon;}
    public boolean root(){return kind==Kind.RANCOR||kind==Kind.CONGREGATION;}
    public static boolean casting(Player player){return !player.level().getEntitiesOfClass(NecromanticAttackEntity.class,player.getBoundingBox().inflate(128),e->e.root()&&player.getUUID().equals(e.owner)&&!e.isRemoved()).isEmpty();}
    public static NecromanticAttackEntity cast(Player player,NecromanticWeaponItem item,InteractionHand hand){
        var e=spawn(player,item.rancor?Kind.RANCOR:Kind.CONGREGATION,player.getBoundingBox().getCenter().add(0,.75,0),item.rancor?Vec3.ZERO:new Vec3(0,10/16d,0),item.damage(player.getItemInHand(hand),player),null);
        e.hand=hand;e.weapon=player.getItemInHand(hand).copy();e.direction=player.getLookAngle();e.publish();
        if(!item.rancor)e.sound("spirit_open");return e;
    }
    private static NecromanticAttackEntity spawn(Player p,Kind kind,Vec3 at,Vec3 velocity,float damage,UUID parent){
        var e=new NecromanticAttackEntity(CalamitasContent.NECROMANTIC_ATTACK.get(),p.level());e.kind=kind;e.weapon=p.getUseItem().copy();e.owner=p.getUUID();e.parent=parent;e.damage=damage;e.velocity=velocity;e.anchor=at;e.setPos(at);
        e.variant=p.getRandom().nextInt(kind==Kind.ARM?6:3);
        e.lifetime=switch(kind){case ARM->240;case SMALL_CINDER->60+p.getRandom().nextInt(150);case LARGE_CINDER->300;case SMALL_SPIRIT->360;case SPIRIT_DUST->60;default->90000;};
        if(kind==Kind.LARGE_CINDER)e.variant=45+p.getRandom().nextInt(105); // Source Lifetime controls fading, not the 300-frame removal.
        if(kind==Kind.ARM){e.rotation=p.getRandom().nextInt(3)==0?(p.getRandom().nextFloat()*2-1)*.3f:0;e.anchor=at.add(0,-36/16d,0);}
        e.getPersistentData().putBoolean("ArkWitchCaptured",true);e.getPersistentData().putUUID("ArkWitchOwner",p.getUUID());
        e.publish();p.level().addFreshEntity(e);if(kind==Kind.ARM)e.sound("rancor_arm");return e;
    }
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        Player p=getOwner();if(p==null||!p.isAlive()||p.isSpectator()){finish();return;}
        samples=new ListTag();
        for(int sub=0;sub<3&&!isRemoved();sub++){
            if(age>=lifetime){finish();break;}
            long now=level().getGameTime()*3+sub;
            if(root()&&!using(p)){
                if(kind==Kind.RANCOR){finish();break;}
                if(death==0)death=1;
            }
            switch(kind){
                case RANCOR->rancor(p,now);
                case CONGREGATION->congregation(p,now);
                case ARM->arm(p,now);
                case SMALL_CINDER,LARGE_CINDER->cinder(p,now);
                case SMALL_SPIRIT->smallSpirit(p);
                case SPIRIT_DUST->setPos(position().add(velocity));
            }
            if(!isRemoved()){samples.add(sample());age++;}
        }
        hitUntil.entrySet().removeIf(e->e.getValue()<=level().getGameTime()*3);
        if(!isRemoved())publish();
    }
    private boolean using(Player p){return p.isUsingItem()&&p.getUsedItemHand()==hand&&p.getItemInHand(hand)==p.getUseItem()&&p.getUseItem().is(weapon.getItem())&&p.getUseItem().getItem() instanceof NecromanticWeaponItem;}
    private void stop(Player p){if(using(p))p.stopUsingItem();if(kind==Kind.CONGREGATION)death=1;else finish();}
    private void rancor(Player p,long now){
        if(age%12==11&&!WitchSouls.pay(p,((NecromanticWeaponItem)weapon.getItem()).soulCost(weapon))){stop(p);return;}
        if(age==15)sound("rancor_cast");if(age==30)sound("rancor_charge");if(age==179)sound("rancor_fire");
        scale=(float)(lerpValue(0,35,age)*1.4);opacity=(float)Math.pow(scale/1.4,2);rotation-=Math.toRadians(scale*4);
        Vec3 ideal=ArkCombat.aim(p).subtract(p.getBoundingBox().getCenter()).normalize();direction=moveTowards(direction,ideal,.05);
        setPos(p.getBoundingBox().getCenter().add(direction.normalize().scale(scale*56/16d)));
        if(age<179)return;
        Vec3 forward=direction.normalize();double beamScale=Math.min(2,1+.15*(age-178));
        laserLength=scan(forward,beamScale*8/16d);
        Vec3 end=position().add(forward.scale(laserLength));
        for(var target:targets(new AABB(position(),end).inflate(.1),p))if(target.getBoundingBox().contains(position())||target.getBoundingBox().clip(position(),end).isPresent())hit(p,target,damage,5,now,false);
        if(random.nextInt(8)==0){
            Vec3 endOffset=randomPlane(forward,80/16d,8/16d);BlockPos ground=findGround(end.add(endOffset),5);
            if(ground!=null)spawn(p,Kind.ARM,Vec3.atBottomCenterOf(ground).add(0,1,0),Vec3.ZERO,damage*2/3,null);
        }
        if(random.nextBoolean()){
            boolean large=random.nextInt(11)==0;double speed=(2+random.nextDouble()*4)/16*(large?1.2:1);
            Vec3 motion=rotateInPlane(forward.scale(-1).lerp(new Vec3(0,1,0),.45),(random.nextDouble()*2-1)*.72).scale(speed);
            Vec3 at=position().add(forward.scale(laserLength-(12+random.nextDouble()*60)/16));
            spawn(p,large?Kind.LARGE_CINDER:Kind.SMALL_CINDER,at,motion,large?damage/3:0,null);
        }
    }
    private double scan(Vec3 forward,double width){
        Vec3 a=side(forward),b=forward.cross(a).normalize();double total=0;
        for(int i=0;i<24;i++){
            double angle=i*Math.PI*2/24;Vec3 start=position().add(a.scale(Math.cos(angle)*width*.5)).add(b.scale(Math.sin(angle)*width*.5));
            Vec3 end=start.add(forward.scale(3330/16d));var hit=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            total+=start.distanceTo(hit.getLocation());
        }return total/24;
    }
    private BlockPos findGround(Vec3 at,int depth){
        BlockPos start=BlockPos.containing(at);
        for(int dy=0;dy<depth;dy++){
            BlockPos pos=start.below(dy);if(!level().hasChunkAt(pos))return null;
            var state=level().getBlockState(pos);var shape=state.getCollisionShape(level(),pos);
            if(!shape.isEmpty()&&net.minecraft.world.level.block.Block.isFaceFull(shape,net.minecraft.core.Direction.UP))return pos;
        }return null;
    }
    private void congregation(Player p,long now){
        if(death>0){death++;scale=1-death/35f;velocity=velocity.scale(.98);setPos(position().add(velocity));if(death>=35)finish();return;}
        Vec3 aim=ArkCombat.aim(p);float power=power();
        if(position().distanceToSqr(aim)>25){
            double speed=(10+smooth(0,2.2,lerpValue(.18,.3,power))+smooth(0,4,lerpValue(.4,.52,power))+smooth(0,5,lerpValue(.6,.72,power))+smooth(0,6,lerpValue(.8,1.2,power)))/16;
            double inertia=18+22*power;
            Vec3 ideal=aim.add(hover).subtract(position()).normalize().lerp(p.getBoundingBox().getCenter().subtract(position()).normalize(),.25).scale(speed);
            velocity=moveTowards(velocity,ideal,speed*.04).scale(inertia-1).add(ideal).scale(1/inertia);
        }else if(velocity.length()>4/16d)velocity=velocity.scale(.95);
        // Source damage uses the pre-increment power, while growth/side effects use the incremented timer.
        float hitDamage=(float)(damage*smooth(1,1.85,power));
        int nextAge=age+1;
        if(nextAge%12==11&&!WitchSouls.pay(p,((NecromanticWeaponItem)weapon.getItem()).soulCost(weapon))){stop(p);return;}
        double nextPower=Math.pow(lerpValue(15,840,nextAge),4);
        if(nextPower>.97)hover=Vec3.ZERO;
        if(!strong&&nextPower>.62){
            strong=true;sound("spirit_grow");
            for(int i=0;i<25;i++)spawn(p,Kind.SPIRIT_DUST,position(),randomPlane(velocity,4/16d,4/16d),0,getUUID());
        }else if(nextPower<=.97&&nextAge%55==54)hover=randomUnit().scale((.6+random.nextDouble()*.4)*smooth(460,0,nextPower)/16);
        if(nextPower<=.97&&nextAge%45==44&&position().distanceToSqr(p.getBoundingBox().getCenter())>Math.pow(230/16d,2)){
            spawn(p,Kind.SMALL_SPIRIT,position(),randomUnit().scale((7+random.nextDouble()*3)/16),0,getUUID());sound("spirit_release");
        }
        if(random.nextInt(16)==0)spawn(p,Kind.SPIRIT_DUST,position(),rotateInPlane(velocity.normalize().scale(-1),(random.nextDouble()*2-1)*1.04).scale(1.5/16),0,getUUID());
        setPos(position().add(velocity));trail.addFirst(position());while(trail.size()>8)trail.removeLast();
        double broad=diameter()*.63;
        for(Vec3 point:trail)for(var target:targets(new AABB(point,point).inflate(broad),p)){
            int index=0;for(Vec3 previous:trail){double radius=diameter()*(.45+(.17-.45)*index/8)*1.4;if(sphere(target.getBoundingBox(),previous,radius)){hit(p,target,hitDamage,11,now,false);break;}index++;}
        }
    }
    private void arm(Player p,long now){
        int[] heights={48,120,82,90,110,50},widths={16,28,30,22,36,28};
        double descend=lerpValue(0,24,lifetime-age),rise=lerpValue(0,30,age);
        double offset=(1-rise*descend)*64-58+(heights[variant]>90?22:0);
        Vec3 up=new Vec3(Math.sin(rotation),Math.cos(rotation),0);
        Vec3 bottom=anchor.subtract(up.scale(offset/16));Vec3 top=bottom.add(up.scale(heights[variant]/16d));
        scale=(float)descend;opacity=(float)(Math.pow(rise,2.4)*descend);setPos(bottom.lerp(top,.5));
        for(var target:targets(new AABB(bottom,top).inflate(widths[variant]*scale/32d),p))if(target.getBoundingBox().inflate(widths[variant]*scale/32d).contains(bottom)||target.getBoundingBox().inflate(widths[variant]*scale/32d).clip(bottom,top).isPresent())hit(p,target,damage,12,now,true);
    }
    private void cinder(Player p,long now){
        int fadeLifetime=kind==Kind.LARGE_CINDER?variant:lifetime;
        scale=(float)(lerpValue(0,20,age)*lerpValue(fadeLifetime,fadeLifetime-20,age)*(.5+.5*(getId()%6)/6d));
        Vec3 old=position();if(kind==Kind.LARGE_CINDER)velocity=velocity.add(0,-.04/16,0);setPos(old.add(velocity));
        if(kind==Kind.LARGE_CINDER)for(var target:targets(new AABB(old,position()).inflate(18/16d),p))hit(p,target,damage,10,now,true);
    }
    private void smallSpirit(Player p){
        var congregation=parent!=null&&level() instanceof ServerLevel server&&server.getEntity(parent) instanceof NecromanticAttackEntity e?e:null;
        if(congregation==null||congregation.isRemoved()){finish();return;}
        returning=congregation.power()>.97||lifetime-age<95;
        Vec3 target=returning?congregation.position():p.getBoundingBox().getCenter();double speed=(returning?29:8)/16d,inertia=returning?5:54;
        if(returning){setPos(moveTowards(position(),target,8.5/16d));if(position().distanceToSqr(target)<25){finish();return;}}
        if(position().distanceToSqr(target)>Math.pow(260/16d,2))velocity=velocity.scale(inertia-1).add(target.subtract(position()).normalize().scale(speed)).scale(1/inertia);else velocity=velocity.scale(1.01);
        opacity=Math.min(returning?.4f:1,opacity+.075f);setPos(position().add(velocity));
    }
    private List<LivingEntity> targets(AABB box,Player p){return level().getEntitiesOfClass(LivingEntity.class,box,e->WeaponUser.canHit(p,e));}
    private void hit(Player p,LivingEntity target,float amount,int cooldown,long now,boolean shared){
        var sharedHits=shared?SHARED_HITS.computeIfAbsent(target,k->new EnumMap<>(Kind.class)):null;long until=shared?sharedHits.getOrDefault(kind,0L):hitUntil.getOrDefault(target.getUUID(),0L);
        if(now<until)return;
        if(shared)sharedHits.put(kind,now+cooldown);else hitUntil.put(target.getUUID(),now+cooldown);
        DamageSource source=new DamageSource(p.damageSources().indirectMagic(this,p).typeHolder(),this,p);
        boolean success;
        if(ArkDamage.netherApollyon(target)){
            int previous=target.invulnerableTime;
            try{target.invulnerableTime=0;success=target.hurt(source,amount);}
            finally{target.invulnerableTime=Math.max(previous,target.invulnerableTime);}
        }else success=ArkDamage.hurt(target,source,amount);
        if(success){
            p.setLastHurtMob(target);
            net.minecraft.world.item.enchantment.EnchantmentHelper.doPostHurtEffects(target,p);
            InnateEnchantment.combatEnchantments(weapon).forEach((enchantment,level)->enchantment.doPostAttack(p,target,level));
            if(kind==Kind.RANCOR){net.everla.projectc.arkofcosmos.enchant.NecromanticAsh.hit(target,now);net.everla.projectc.arkofcosmos.enchant.WitchEnchanting.necromanticVisual(target,0);}
        }
    }
    private static boolean sphere(AABB box,Vec3 at,double radius){double x=Math.max(box.minX,Math.min(box.maxX,at.x)),y=Math.max(box.minY,Math.min(box.maxY,at.y)),z=Math.max(box.minZ,Math.min(box.maxZ,at.z));return at.distanceToSqr(new Vec3(x,y,z))<=radius*radius;}
    public static Vec3 moveTowards(Vec3 from,Vec3 to,double distance){Vec3 delta=to.subtract(from);return delta.length()<=distance?to:from.add(delta.normalize().scale(distance));}
    private Vec3 randomUnit(){double z=random.nextDouble()*2-1,angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(1-z*z);return new Vec3(r*Math.cos(angle),z,r*Math.sin(angle));}
    private static Vec3 side(Vec3 forward){Vec3 result=forward.cross(new Vec3(0,1,0));return result.lengthSqr()<.001?new Vec3(1,0,0):result.normalize();}
    private Vec3 randomPlane(Vec3 forward,double x,double y){Vec3 a=side(forward.normalize()),b=forward.normalize().cross(a).normalize();double angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble());return a.scale(Math.cos(angle)*r*x).add(b.scale(Math.sin(angle)*r*y));}
    private Vec3 rotateInPlane(Vec3 vector,double angle){Vec3 perpendicular=side(vector).cross(vector).normalize();return vector.scale(Math.cos(angle)).add(perpendicular.scale(vector.length()*Math.sin(angle)));}
    public void finish(){if(isRemoved())return;if(kind==Kind.SMALL_SPIRIT&&!level().isClientSide)net.everla.projectc.arkofcosmos.enchant.WitchEnchanting.necromanticVisual(this,1);ending=true;discard();}
    public boolean protectRemoval(RemovalReason reason){var p=getOwner();return reason==RemovalReason.DISCARDED&&!level().isClientSide&&!ending&&p!=null&&p.isAlive();}
    @Override public void remove(RemovalReason reason){if(!protectRemoval(reason))super.remove(reason);}
    private void sound(String sound){if(!level().isClientSide)level().playSound(null,getX(),getY(),getZ(),CalamitasContent.necromanticSound(sound),net.minecraft.sounds.SoundSource.PLAYERS,1,1);}
    private CompoundTag sample(){var tag=new CompoundTag();tag.putBoolean("Returning",returning);tag.putInt("Age",age);put(tag,"Position",position());put(tag,"Velocity",velocity);put(tag,"Direction",direction);tag.putFloat("Scale",scale);tag.putFloat("Opacity",opacity);tag.putFloat("Rotation",rotation);tag.putInt("Death",death);tag.putDouble("Length",laserLength);return tag;}
    private void publish(){var tag=sample();tag.putInt("Kind",kind.ordinal());tag.putInt("Owner",getOwner()==null?-1:getOwner().getId());tag.putInt("Variant",variant);tag.putInt("Lifetime",lifetime);tag.putBoolean("Strong",strong);tag.put("Samples",samples.copy());entityData.set(VIEW,tag);}
    public static void put(CompoundTag tag,String key,Vec3 value){var list=new ListTag();list.add(DoubleTag.valueOf(value.x));list.add(DoubleTag.valueOf(value.y));list.add(DoubleTag.valueOf(value.z));tag.put(key,list);}
    public static Vec3 vector(CompoundTag tag,String key){var list=tag.getList(key,6);return list.size()==3?new Vec3(list.getDouble(0),list.getDouble(1),list.getDouble(2)):Vec3.ZERO;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.put("State",sample());tag.putString("Kind",kind.name());if(owner!=null)tag.putUUID("Owner",owner);if(parent!=null)tag.putUUID("Parent",parent);tag.putInt("Hand",hand.ordinal());tag.put("Weapon",weapon.save(new CompoundTag()));tag.putFloat("Damage",damage);tag.putInt("Lifetime",lifetime);tag.putInt("Variant",variant);tag.putBoolean("Strong",strong);put(tag,"Hover",hover);put(tag,"Anchor",anchor);var hits=new ListTag();hitUntil.forEach((uuid,until)->{var hit=new CompoundTag();hit.putUUID("Target",uuid);hit.putLong("Until",until);hits.add(hit);});tag.put("Hits",hits);var history=new ListTag();for(Vec3 point:trail){var t=new CompoundTag();put(t,"P",point);history.add(t);}tag.put("Trail",history);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        try{kind=Kind.valueOf(tag.getString("Kind"));}catch(IllegalArgumentException invalid){ending=true;return;}
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;parent=tag.hasUUID("Parent")?tag.getUUID("Parent"):null;hand=tag.getInt("Hand")==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        weapon=ItemStack.of(tag.getCompound("Weapon"));damage=tag.getFloat("Damage");lifetime=Math.max(1,tag.getInt("Lifetime"));variant=Math.max(0,tag.getInt("Variant"));if(kind==Kind.ARM)variant%=6;strong=tag.getBoolean("Strong");hover=vector(tag,"Hover");anchor=vector(tag,"Anchor");
        var state=tag.getCompound("State");age=Math.max(0,state.getInt("Age"));death=state.getInt("Death");scale=state.getFloat("Scale");opacity=state.getFloat("Opacity");rotation=state.getFloat("Rotation");laserLength=state.getDouble("Length");velocity=vector(state,"Velocity");direction=vector(state,"Direction");
        hitUntil.clear();for(Tag t:tag.getList("Hits",10)){var hit=(CompoundTag)t;if(hit.hasUUID("Target"))hitUntil.put(hit.getUUID("Target"),hit.getLong("Until"));}trail.clear();for(Tag t:tag.getList("Trail",10))if(trail.size()<8)trail.addLast(vector((CompoundTag)t,"P"));publish();
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
