package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Legacy ArkoftheCosmos/Galaxia2 and Terraria 1.3.5.3 Projectile state machines.
 * One source pixel is 1/16 block; velocity is applied once per source update.
 */
public final class OldOneEntity extends Entity {
    public enum Kind {
        SWORD(0),EON(0),EON_V2(0),EON_V3(0),EON_V4(0),ORB(0),ASTRAL_STAR(0),
        FLOWER_POW(206),ICE_BLADE(118),FLAIRON_BUBBLE(405),CURSED_FLAME(95),ICHOR(280),
        WATER_BOLT(27),HOLY_ARROW(661),MUSHROOM(131),FIREBALL(15),RAINBOW_CRYSTAL(644),
        NEBULA(634),STARDUST_CELL(614),SOLAR_EXPLOSION(612),VORTEX_ROCKET(616),TERRARIAN(604);
        public final int nativeId;
        Kind(int id){nativeId=id;}
    }
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(OldOneEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final Map<LivingEntity,Map<UUID,Long>> OWNER_IMMUNITY=new WeakHashMap<>();
    private final Map<Integer,Integer> hitUntil=new HashMap<>();
    private final AttackRenderSnapshot clientView=new AttackRenderSnapshot();
    private net.minecraft.world.item.ItemStack weapon=net.minecraft.world.item.ItemStack.EMPTY;
    public net.minecraft.world.item.ItemStack weapon(){return weapon;}
    private UUID ownerUuid;
    private long startTick;
    private long expiresAt=Long.MIN_VALUE;
    private int ownerId,age,updates,left,penetrate,alpha,animationFrame,tileDelay=120,targetId=-1,parentId=-1,solarWidth=8,sourceStep;
    private Kind kind=Kind.SWORD;
    private boolean ended,finishing,nebulaSpiral,tileCollide,friendly=true,pulse,waterWet,lavaWet,blockEnd;
    private long eventSequence;
    private float rotation,scale=1,ai0,ai1,local0,local1;
    private Vec3 velocity=Vec3.ZERO,spawn=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(new Vec3(0,0,1),new Vec3(-1,0,0));
    private ListTag samples=new ListTag(),trail=new ListTag();
    public OldOneEntity(EntityType<? extends OldOneEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public static OldOneEntity create(Player p,Kind k,Vec3 at,Vec3 speed,AttackFrame f){return spawn(p,k,at,speed,f,0,-1,-1);}
    public static OldOneEntity orb(Player p,Vec3 at,Vec3 speed,AttackFrame f,int parameter){return spawn(p,Kind.ORB,at,speed,f,parameter,-1,-1);}
    private static OldOneEntity spawn(Player p,Kind k,Vec3 at,Vec3 speed,AttackFrame f,float parameter,int target,int parent){
        if(k==Kind.SWORD)at=swordHilt(p);
        OldOneEntity e=new OldOneEntity(ArkOfCosmos.OLD_ONE_ATTACK.get(),p.level());e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=k;e.weapon=p.getMainHandItem().copy();
        e.startTick=p.level().getGameTime();
        e.frame=f;e.velocity=speed;e.setDeltaMovement(speed);e.setPos(at);e.spawn=at;e.ai1=parameter;e.targetId=target;e.parentId=parent;e.defaults();
        e.publish();p.level().addFreshEntity(e);return e;
    }
    private void defaults(){
        left=3600;penetrate=1;tileCollide=true;
        switch(kind){
            case SWORD->{rotation=(float)Math.toRadians(-60);left=15;penetrate=-1;tileCollide=false;}
            case EON,EON_V2,EON_V3,EON_V4->{left=160;penetrate=3;tileCollide=false;}
            case ORB->{left=80;penetrate=2;tileCollide=false;alpha=50;}
            case ASTRAL_STAR->{tileCollide=false;alpha=50;}
            case FLOWER_POW->{left=600;alpha=255;}
            case ICE_BLADE->alpha=255;
            case FLAIRON_BUBBLE->{left=90;alpha=255;}
            case CURSED_FLAME,FIREBALL->alpha=100;
            case ICHOR->alpha=255;
            case WATER_BOLT->{left=1800;alpha=255;}
            case HOLY_ARROW->{left=40;alpha=255;}
            case MUSHROOM->tileCollide=false;
            case RAINBOW_CRYSTAL->{left=900;tileCollide=false;alpha=255;}
            case NEBULA,STARDUST_CELL->alpha=255;
            case SOLAR_EXPLOSION->{left=60;penetrate=-1;tileCollide=false;alpha=255;}
            case VORTEX_ROCKET->{left=270;alpha=255;}
            case TERRARIAN->scale=1.2f;
        }
        int updatesPerTick=3*updatesPerFrame();
        if(left>=200*updatesPerTick){
            left=100*updatesPerTick;
            // The level sweep runs every 20 ticks: round down so even non-ticking attacks end within 5s.
            expiresAt=Math.floorDiv(startTick+100,20)*20;
        }else{
            // Keep short source lifetimes; allow two ticks for the sword's final network samples.
            expiresAt=startTick+(left+updatesPerTick-1)/updatesPerTick+2;
        }
    }
    @Override protected void defineSynchedData(){entityData.define(VIEW,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    public Kind kind(){return level().isClientSide?Kind.values()[entityData.get(VIEW).getInt("Kind")]:kind;}
    public int ownerId(){return level().isClientSide?entityData.get(VIEW).getInt("Owner"):ownerId;}
    public int sourceAge(){return age;}
    public static Vec3 swordHilt(Player p){return p.position().add(0,p.getBbHeight()*.6,0);}
    public static double swordAngle(double sourceAge){return Math.toRadians(-60+120*Math.max(0,Math.min(15,sourceAge))/15);}
    public int remaining(){return left;}
    public boolean lifetimeExpired(){return level().getGameTime()>=expiresAt;}
    public Player owner(){return ownerUuid==null?null:level().getPlayerByUUID(ownerUuid);}
    public int updatesPerFrame(){return switch(kind){case ICHOR,NEBULA,VORTEX_ROCKET->3;case HOLY_ARROW->2;default->1;};}
    public boolean beam(){return kind.ordinal()>=Kind.EON.ordinal()&&kind.ordinal()<=Kind.EON_V4.ordinal();}
    public double sizePixels(){return switch(kind){case SWORD->102;case EON,EON_V2,EON_V3,EON_V4->20;case ORB,ASTRAL_STAR->24;case FLOWER_POW,FLAIRON_BUBBLE,RAINBOW_CRYSTAL,VORTEX_ROCKET,TERRARIAN->14;case ICE_BLADE,HOLY_ARROW->10;case ICHOR->32;case MUSHROOM->22;case NEBULA->40;case SOLAR_EXPLOSION->solarWidth;default->16;};}
    public static boolean canHit(Player p,LivingEntity t){return !(t instanceof Player)&&ArkCombat.canHit(p,t)&&!(t instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isOwnedBy(p));}
    private LivingEntity target(){Entity e=level().getEntity(targetId);return e instanceof LivingEntity t&&t.isAlive()?t:null;}
    private LivingEntity nearest(Player p,double pixels,boolean sight,boolean includeSize){
        LivingEntity best=null;double distance=Double.MAX_VALUE;Vec3 at=position();
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(pixels/16+16),e->canHit(p,e)&&ArkCombat.enemy(p,e))){
            double d=at.distanceTo(t.getBoundingBox().getCenter()),extra=includeSize?(t.getBbWidth()+t.getBbHeight())/2:0;
            double range=kind==Kind.VORTEX_ROCKET?manhattan(at,t.getBoundingBox().getCenter()):d;
            if(range<pixels/16+extra&&d<distance&&(!sight||clear(at,t.getBoundingBox().getCenter()))){best=t;distance=d;}
        }return best;
    }
    private static double manhattan(Vec3 a,Vec3 b){return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.z-b.z);}
    private boolean clear(Vec3 a,Vec3 b){return level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    private void steer(LivingEntity t,double speed,double inertia){if(t!=null)velocity=velocity.scale(inertia).add(t.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed/16)).scale(1/(inertia+1));}
    private double vx(){return velocity.dot(frame.forward())*16;}
    private double vy(){return velocity.dot(frame.right())*16;}
    private void rotateWithVelocity(double offset){rotation=(float)(frame.angle(velocity)+offset);}
    private void capFall(){if(velocity.y< -1)velocity=new Vec3(velocity.x,-1,velocity.z);}
    @Override public void tick(){
        super.tick();if(level().isClientSide){clientView.tick(entityData.get(VIEW),"Updates");return;}
        if(lifetimeExpired()){discardAttack();return;}
        Player p=owner();if(p==null||!p.isAlive()||p.isSpectator()){discardAttack();return;}
        // Allow the final trajectory to arrive and interpolate before removal. No extra damage step.
        if(kind==Kind.SWORD&&left<=0){if(left==0){left=-1;publish();}else discardAttack();return;}
        samples=new ListTag();samples.add(snapshot());
        for(int step=0;step<3&&!ended;step++){
            sourceStep=step;age++;
            for(int sub=0;sub<updatesPerFrame()&&!ended;sub++){
                updates++;hitUntil.replaceAll((id,time)->time<0?time:Math.max(0,time-1));remember();ai(p);if(ended)break;if(kind!=Kind.CURSED_FLAME&&kind!=Kind.WATER_BOLT&&kind!=Kind.FIREBALL)emit(false);
                Vec3 from=position();
                if(kind==Kind.SWORD){setPos(swordHilt(p));swordHit(p);}
                else{blockEnd=false;travel();if(!ended)hit(p,from,position(),kind==Kind.SOLAR_EXPLOSION,solarWidth/32d);if(blockEnd&&!ended)finish(true);}
                left--;samples.add(snapshot());if(left<=0&&kind!=Kind.SWORD)finish(true);
            }
        }if(!ended)publish();
    }
    private void ai(Player p){
        if(kind==Kind.SWORD){rotation=(float)swordAngle(age);return;}
        if(beam()){
            if(local1<15)local1++;
            else if(!pulse){scale-=.02f;alpha+=30;if(alpha>=250){alpha=255;pulse=true;}}
            else{scale+=.02f;alpha-=30;if(alpha<=0){alpha=0;pulse=false;}}
            rotateWithVelocity(.785);capFall();return;
        }
        switch(kind){
            case ORB,ASTRAL_STAR->{
                alpha=Math.max(projectedY()>=ai1?0:150,alpha-15);rotation+=(float)((Math.abs(vx())+Math.abs(vy()))*.01*(vx()<0?-1:1));
                if(kind==Kind.ORB)steer(nearest(p,1600,false,true),35,20);else{tileDelay-=1+random.nextInt(2);if(tileDelay==0)tileCollide=true;}
            }
            case FLOWER_POW->{
                local0++;if(local0>3)alpha=Math.max(0,alpha-50);animationFrame=(updates/3)%5;
                velocity=velocity.add(frame.forward().scale(ai0/16)).add(frame.right().scale(ai1/16));
                if(++local1==50){local1=51;ai0=(random.nextInt(201)-100)*.00006f;ai1=(random.nextInt(201)-100)*.00006f;}
                double total=Math.abs(vx())+Math.abs(vy());if(total>16)velocity=velocity.scale(.95);if(Math.abs(vx())+Math.abs(vy())<12)velocity=velocity.scale(1.05);rotateWithVelocity(3.14);
            }
            case ICE_BLADE->{} // aiStyle 28 contains only sound and Dust; its velocity is constant.
            case CURSED_FLAME,WATER_BOLT,FIREBALL->{
                emit(false); // aiStyle 8 emits Dust before gravity changes velocity.
                if(kind!=Kind.WATER_BOLT&&++ai1>=20)velocity=velocity.add(0,-.2/16,0);
                rotation+=.3f*(vx()<0?-1:1);capFall();
            }
            case ICHOR->{scale-=.002f;if(scale<=0){finish(true);return;}if(ai0>3)velocity=velocity.add(0,-.075/16,0);else ai0++;}
            case MUSHROOM->{velocity=velocity.scale(.8);rotation+=.2f;alpha+=4;if(alpha>=255)finish(true);}
            case FLAIRON_BUBBLE->{
                if(ai0==0){LivingEntity t=nearest(p,500,true,false);targetId=t==null?-1:t.getId();ai0=t==null?-15:1;
                    if(t!=null){Vec3 jitter=frame.forward().scale((random.nextInt(201)-100)/16d).add(frame.right().scale((random.nextInt(201)-100)/16d));velocity=velocity.scale(20).add(t.getBoundingBox().getCenter().subtract(position()).add(jitter).normalize().scale((35+random.nextInt(40))/30d/16)).scale(1d/21);}}
                else if(ai0>0){Entity locked=level().getEntity(targetId);if(locked!=null)velocity=velocity.scale(40).add(locked.getBoundingBox().getCenter().subtract(position()).normalize().scale(12d/16)).scale(1d/41);}else{ai0++;alpha=Math.max(50,alpha-25);velocity=velocity.scale(.95);}
                if(ai1==0)ai1=(80+random.nextInt(41))/100f;scale=ai1;
            }
            case RAINBOW_CRYSTAL->{
                if(local0==0){local0=random.nextFloat()*.8f+.8f;ai0=random.nextBoolean()?1:-1;}
                rotation=local1/40*(float)(Math.PI*2)*ai0;alpha=Math.max(0,alpha-8);scale=(1-alpha/255f)/2*local0;velocity=Vec3.ZERO;if(++local1>=60){emit(false);finish(true);}
            }
            case STARDUST_CELL->{alpha=Math.max(0,alpha-63);LivingEntity t=target();if(t==null||!canHit(p,t)||!ArkCombat.enemy(p,t)){finish(true);return;}steer(t,14,4);}
            case TERRARIAN->{velocity=velocity.scale(.985);rotation+=(float)(vx()*.2+(vx()>0?.08:-.08));if(++ai1>30){alpha+=10;if(alpha>=255)finish(true);}}
            case SOLAR_EXPLOSION->{ai1+=.01f;scale=ai1;ai0++;animationFrame=updates/3;alpha=Math.max(0,alpha-63);if(ai0>=15){finish(true);return;}if(ai0==1){solarWidth=(int)(52*scale);hit(p,position(),position(),true,solarWidth/32d);}}
            case NEBULA->nebula(p);
            case HOLY_ARROW->{alpha=Math.max(0,alpha-55);scale=1.3f;rotateWithVelocity(Math.PI/2);}
            case VORTEX_ROCKET->{
                if(local0==0)local0=(float)velocity.length()*16;alpha=Math.max(0,alpha-25);
                if(++ai0>20){ai0=20;LivingEntity t=target();if(targetId<0){t=nearest(p,800,true,false);if(t!=null)targetId=t.getId();}
                    if(t!=null&&canHit(p,t)&&ArkCombat.enemy(p,t)&&manhattan(t.getBoundingBox().getCenter(),position())<1000d/16)steer(t,local0,7);}
                rotateWithVelocity(Math.PI/2);
            }
            default->throw new IllegalStateException("Unhandled legacy projectile "+kind);
        }
        if(kind==Kind.NEBULA||kind==Kind.HOLY_ARROW||kind==Kind.VORTEX_ROCKET)capFall();
    }
    private double projectedY(){return (level().getMaxBuildHeight()-position().y)*16;}
    private void nebula(Player p){
        if(ai1==0){ai1=1;local0=-random.nextInt(48);}
        else if(ai1==1){LivingEntity t=nearest(p,250,true,false);if(t!=null){if(t.getBoundingBox().getCenter().distanceTo(position())<20d/16){finish(true);return;}targetId=t.getId();ai1=6;}}
        else if(ai1>5){ai1++;LivingEntity t=target();if(t==null||!canHit(p,t)||!ArkCombat.enemy(p,t)){ai1=1;targetId=-1;}else if(t.getBoundingBox().getCenter().distanceTo(position())<20d/16){finish(true);return;}else steer(t,6,29);}
        if(ai1>=1&&ai1<5){ai1++;if(ai1==5)ai1=1;}alpha=Math.max(0,alpha-40);animationFrame=(updates/12)%4;rotateWithVelocity(0);if(++local0==48){local0=0;nebulaSpiral=false;}else nebulaSpiral=alpha==0;
    }
    private boolean ignoreWater(){return kind==Kind.ORB||kind==Kind.ICHOR||kind==Kind.RAINBOW_CRYSTAL||kind==Kind.NEBULA||kind==Kind.HOLY_ARROW||kind==Kind.SOLAR_EXPLOSION;}
    private boolean fluid(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag){
        AABB b=getBoundingBox();for(var pos:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(b.minX,b.minY,b.minZ),net.minecraft.core.BlockPos.containing(b.maxX,b.maxY,b.maxZ))){
            var state=level().getFluidState(pos);if(state.is(tag)&&pos.getY()+state.getHeight(level(),pos)>b.minY)return true;
        }return false;
    }
    private void travel(){
        if(!ignoreWater()){
            if(kind==Kind.FIREBALL&&waterWet&&!lavaWet){finish(true);return;}
            waterWet=fluid(net.minecraft.tags.FluidTags.WATER);lavaWet=fluid(net.minecraft.tags.FluidTags.LAVA);
        }
        boolean wet=!ignoreWater()&&(waterWet||lavaWet);Vec3 start=position(),motion=wet?velocity.scale(.5):velocity;if(tileCollide){
            refreshDimensions();if(kind==Kind.NEBULA||kind==Kind.ICHOR)setBoundingBox(new AABB(start,start).inflate((kind==Kind.NEBULA?8:10)/32d));noPhysics=false;setDeltaMovement(velocity);move(MoverType.SELF,motion);noPhysics=true;Vec3 center=getBoundingBox().getCenter();setPos(center);refreshDimensions();Vec3 actual=position().subtract(start);
            if(actual.distanceToSqr(motion)>1e-10){
                if(kind==Kind.WATER_BOLT||kind==Kind.CURSED_FLAME||kind==Kind.FIREBALL||kind==Kind.TERRARIAN){double bounce=kind==Kind.FIREBALL&&actual.y<-.25?.8:1;velocity=new Vec3(Math.abs(actual.x-motion.x)>1e-6?-velocity.x:velocity.x,Math.abs(actual.y-motion.y)>1e-6?-velocity.y*bounce:velocity.y,Math.abs(actual.z-motion.z)>1e-6?-velocity.z:velocity.z);if(kind!=Kind.TERRARIAN&&++ai0>=5)finish(true);else emit("bounce");}
                else blockEnd=true;
            }
        }else setPos(start.add(motion));setDeltaMovement(velocity);
    }
    @Override public EntityDimensions getDimensions(Pose pose){float side=kind==null?.25f:(float)sizePixels()/16;return EntityDimensions.scalable(side,side);}
    @Override protected AABB makeBoundingBox(){double half=getDimensions(Pose.STANDING).width/2d;return new AABB(getX()-half,getY()-half,getZ()-half,getX()+half,getY()+half,getZ()+half);}
    private int localCooldown(){if(beam())return 6-(kind.ordinal()-Kind.EON.ordinal());return switch(kind){case SWORD,RAINBOW_CRYSTAL,VORTEX_ROCKET->-1;case ORB->1;case SOLAR_EXPLOSION->6;case HOLY_ARROW->8;default->0;};}
    private void swordHit(Player p){
        Vec3 start=position();double reach=Math.hypot(OldOneBlade.WIDTH,OldOneBlade.HEIGHT)/16;
        double previous=swordAngle(age-1);
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(start,start).inflate(reach),e->canHit(p,e)))if(OldOneBlade.sweeps(t.getBoundingBox(),start,frame,previous,rotation))strike(p,t,false);
    }
    private void hit(Player p,Vec3 from,Vec3 to,boolean explosion,double radius){
        if(!friendly||ended)return;
        double half=explosion?radius:sizePixels()/32;
        List<LivingEntity> targets=level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(half),t->canHit(p,t));
        targets.sort(Comparator.comparingDouble(t->t.getBoundingBox().getCenter().distanceToSqr(from)));
        for(LivingEntity t:targets){if(ended&&!explosion)break;AABB box=t.getBoundingBox();if(kind==Kind.STARDUST_CELL&&t.getBbWidth()>.5&&t.getBbHeight()>.5)box=box.deflate(t.getBbWidth()/8,t.getBbHeight()/8,t.getBbWidth()/8);
            boolean collides=explosion?sphereIntersects(box,to,half):box.inflate(half).contains(from)||box.inflate(half).clip(from,to).isPresent();
            if(collides&&(kind!=Kind.HOLY_ARROW||t.getBoundingBox().getCenter().distanceTo(to)<=500d/16&&clear(to,t.getBoundingBox().getCenter())))strike(p,t,explosion);
        }
    }
    private static boolean sphereIntersects(AABB b,Vec3 p,double r){double x=Math.max(b.minX,Math.min(b.maxX,p.x)),y=Math.max(b.minY,Math.min(b.maxY,p.y)),z=Math.max(b.minZ,Math.min(b.maxZ,p.z));return p.distanceToSqr(new Vec3(x,y,z))<=r*r;}
    private void strike(Player p,LivingEntity t,boolean explosion){
        int cooldown=localCooldown();Integer remaining=hitUntil.get(t.getId());if(remaining!=null&&(remaining<0||remaining>0))return;
        long now=level().getGameTime()*3+Math.min(2,(age-1)%3);
        if((cooldown==0||kind==Kind.SOLAR_EXPLOSION)&&OWNER_IMMUNITY.getOrDefault(t,Map.of()).getOrDefault(ownerUuid,0L)>now)return;
        DamageSource source=kind==Kind.SWORD&&!ArkDamage.netherApollyon(t)?t.damageSources().playerAttack(p):new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK),this,p);
        float amount=(float)Math.rint((net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage("ark_of_the_old_one")+net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(weapon,t.getMobType()))*(1f+(random.nextInt(31)-15)*.01f));
        // Native melee base crit 4 plus the item's 15 percentage points; source critical multiplier is two.
        if(kind!=Kind.SOLAR_EXPLOSION&&random.nextInt(100)<19)amount*=2;
        if(!ArkDamage.hurtUnmitigated(t,source,amount))return;
        p.setLastHurtMob(t);
        int fire=weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT);if(fire>0)t.setSecondsOnFire(fire*4);
        Vec3 direction=kind==Kind.SWORD?frame.direction(rotation):velocity.normalize();
        double knockback=(9.5+weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK)*2)/16;
        t.knockback(knockback,-direction.x,-direction.z);
        net.minecraft.world.item.enchantment.EnchantmentHelper.doPostHurtEffects(t,p);
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.combatEnchantments(weapon).forEach((enchantment,level)->enchantment.doPostAttack(p,t,level));
        if(cooldown!=0)hitUntil.put(t.getId(),cooldown);if(kind==Kind.NEBULA||kind==Kind.SOLAR_EXPLOSION||cooldown==0&&(penetrate!=1||explosion))OWNER_IMMUNITY.computeIfAbsent(t,k->new HashMap<>()).put(ownerUuid,now+(kind==Kind.NEBULA?5:kind==Kind.SOLAR_EXPLOSION?4:10));
        if(kind==Kind.SWORD)OldOneEffects.grant(p,OldOneEnvironment.zone(p));
        else if(beam()){for(var s:new OldOneEffects.Status[]{OldOneEffects.Status.BRIMSTONE,OldOneEffects.Status.GLACIAL,OldOneEffects.Status.PLAGUE,OldOneEffects.Status.HOLY_FIRE})OldOneEffects.apply(t,s,40);}
        else if(kind==Kind.ORB){var z=OldOneEnvironment.zone(p);OldOneEffects.orb(t,z);OldOneEffects.grant(p,z);derive(p,t,z);}
        else switch(kind){case ASTRAL_STAR->OldOneEffects.apply(t,OldOneEffects.Status.ASTRAL,40);case CURSED_FLAME->OldOneEffects.apply(t,OldOneEffects.Status.CURSED,140);case ICHOR->OldOneEffects.apply(t,OldOneEffects.Status.ICHOR,200);case FIREBALL->{if(random.nextBoolean())t.setSecondsOnFire(5);}case SOLAR_EXPLOSION->OldOneEffects.apply(t,OldOneEffects.Status.DAYBREAK,100);case STARDUST_CELL->OldOneEffects.apply(t,OldOneEffects.Status.CELLED,300);case TERRARIAN->{friendly=false;ai1=1000;}default->{}}
        if(!explosion&&penetrate>0&&--penetrate==0)finish(true);
    }
    private void derive(Player p,LivingEntity t,OldOneEnvironment.Zone z){
        Kind next=switch(z){case ASTRAL->Kind.ASTRAL_STAR;case JUNGLE->Kind.FLOWER_POW;case SNOW->Kind.ICE_BLADE;case BEACH->Kind.FLAIRON_BUBBLE;case CORRUPT->Kind.CURSED_FLAME;case CRIMSON->Kind.ICHOR;case DUNGEON->Kind.WATER_BOLT;case DESERT->Kind.HOLY_ARROW;case MUSHROOM->Kind.MUSHROOM;case HELL->Kind.FIREBALL;case HOLY->Kind.RAINBOW_CRYSTAL;case NEBULA->Kind.NEBULA;case STARDUST->Kind.STARDUST_CELL;case SOLAR->Kind.SOLAR_EXPLOSION;case VORTEX->Kind.VORTEX_ROCKET;case DEFAULT->Kind.TERRARIAN;};
        OldOneEntity child=spawn(p,next,position(),velocity.scale(next==Kind.FLAIRON_BUBBLE?.25:1),frame,next==Kind.SOLAR_EXPLOSION?.85f+random.nextFloat()*1.15f:0,next==Kind.STARDUST_CELL?t.getId():-1,getId());child.weapon=weapon.copy();
    }
    public void discardAttack(){finish(false);}
    private void finish(boolean natural){
        if(ended||finishing)return;
        finishing=true;
        if(natural&&(kind==Kind.NEBULA||kind==Kind.VORTEX_ROCKET||kind==Kind.RAINBOW_CRYSTAL||kind==Kind.HOLY_ARROW)){Player p=owner();if(p!=null)hit(p,position(),position(),true,(kind==Kind.NEBULA?50:kind==Kind.RAINBOW_CRYSTAL?60:kind==Kind.HOLY_ARROW?160:80)/32d);}
        if(natural)emit(true);ended=true;super.discard();
    }
    private void emit(boolean death){
        emit(death?"death":"update");
    }
    private void emit(String eventType){
        if(kind.ordinal()<Kind.ASTRAL_STAR.ordinal())return;
        CompoundTag event=snapshot();event.putInt("SourceStep",sourceStep);event.putString("Event","old_one_"+eventType);
        event.putUUID("Attack",getUUID());event.putLong("Sequence",++eventSequence);
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,event);
    }
    @Override public void remove(RemovalReason reason){Player p=owner();if(!level().isClientSide&&reason==RemovalReason.DISCARDED&&!ended&&left>0&&p!=null&&p.isAlive())return;super.remove(reason);}
    private void remember(){if(kind!=Kind.ORB&&kind!=Kind.ASTRAL_STAR)return;trail.add(0,snapshotPosition());int max=kind==Kind.ORB?6:10;while(trail.size()>max)trail.remove(trail.size()-1);}
    private CompoundTag snapshotPosition(){CompoundTag s=new CompoundTag();ElementsEntity.vector(s,"Position",position());s.putFloat("Rotation",rotation);return s;}
    private CompoundTag snapshot(){
        CompoundTag s=snapshotPosition();s.putLong("StartTick",startTick);s.putInt("Kind",kind.ordinal());s.putInt("Owner",ownerId);s.putInt("Age",age);s.putInt("Updates",updates);s.putInt("Left",left);s.putInt("Alpha",alpha);s.putInt("Frame",animationFrame);s.putFloat("Scale",scale);s.putInt("Target",targetId);s.putInt("Parent",parentId);s.putFloat("AI0",ai0);s.putFloat("AI1",ai1);s.putFloat("Local0",local0);s.putFloat("Local1",local1);s.putBoolean("NebulaSpiral",nebulaSpiral);s.putInt("Size",(int)sizePixels());
        ElementsEntity.vector(s,"Velocity",velocity);ElementsEntity.vector(s,"F",frame.forward());ElementsEntity.vector(s,"R",frame.right());s.put("Trail",trail.copy());return s;
    }
    private void publish(){CompoundTag s=snapshot();s.put("Samples",samples.copy());entityData.set(VIEW,s);refreshDimensions();}
    public CompoundTag renderState(){clientView.initialize(entityData.get(VIEW));return clientView.state();}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        if(clientView!=null&&level().isClientSide&&key.equals(VIEW))clientView.initialize(entityData.get(VIEW));
        super.onSyncedDataUpdated(key);
    }
    public CompoundTag renderState(float partial){
        CompoundTag state=renderState();ListTag list=state.getList("Samples",10);if(list.size()<2)return state;
        double index=Math.max(0,Math.min(1,partial))*(list.size()-1);int i=(int)index;CompoundTag a=list.getCompound(i),b=list.getCompound(Math.min(i+1,list.size()-1)),s=a.copy();double t=index-i;
        for(String key:new String[]{"Position","Velocity"})ElementsEntity.vector(s,key,ElementsEntity.vector(a,key).lerp(ElementsEntity.vector(b,key),t));
        for(String key:new String[]{"Rotation","Scale","Alpha","Age"})s.putDouble(key,a.getDouble(key)+(b.getDouble(key)-a.getDouble(key))*t);
        return s;
    }
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<256*256;}
}
