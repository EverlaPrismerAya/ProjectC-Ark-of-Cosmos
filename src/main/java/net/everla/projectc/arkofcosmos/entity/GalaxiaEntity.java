package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;
import static net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.*;

/** Server-authoritative Galaxia state machines; all counters below use original Terraria updates. */
public final class GalaxiaEntity extends Entity {
    public static final int BLADE=0,BOLT=1,STAR=2,CHAIN=3,RING=4,SLASH=5,SPROUT=6,SURFACE_WAVE=7;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(GalaxiaEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AttackRenderSnapshot clientSnapshot = new AttackRenderSnapshot();
    private UUID ownerUuid;
    private int ownerId,kind,mode,state,age,left=2,charge,overCharge,indicator,hitCooldown,blastCooldown;
    private float damage,scale=1,rotation,hue,homing;
    private Vec3 velocity=Vec3.ZERO,direction=new Vec3(0,0,1),start=Vec3.ZERO,end=Vec3.ZERO,displacement=Vec3.ZERO;
    private AttackFrame frame=new AttackFrame(new Vec3(0,0,1),new Vec3(1,0,0));
    private ItemStack weapon=ItemStack.EMPTY;
    private GalaxiaEntity wheel;
    private LivingEntity target;
    private final Map<UUID,Integer> immunity=new HashMap<>();
    private boolean directFire=true,dashing,hasMadeSound;
    private boolean madeChargeSound;
    private int chargeSoundCooldown;
    private int ariesChainSwapTimer;
    private int duration;
    private boolean movedPlayer;
    private double sproutSize=1,wait;
    private final net.minecraft.nbt.ListTag chainPoints=new net.minecraft.nbt.ListTag();
    private Vec3 previousEnd=Vec3.ZERO;
    private int chainTimer;
    private int chainGenerationAge;
    private net.minecraft.nbt.ListTag samples=new net.minecraft.nbt.ListTag();

    public GalaxiaEntity(EntityType<? extends GalaxiaEntity> type, Level level) {
        super(type,level); noPhysics=true; setNoGravity(true);
    }
    private static GalaxiaEntity create(Player p,int kind,int mode,Vec3 at,AttackFrame f,float damage,int life) {
        GalaxiaEntity e=new GalaxiaEntity(ArkOfCosmos.GALAXIA_ATTACK.get(),p.level());
        e.ownerUuid=p.getUUID();e.ownerId=p.getId();e.kind=kind;e.mode=mode;e.frame=f;
        e.direction=f.forward();e.damage=damage;e.left=life;e.weapon=p.getMainHandItem().copy();e.setPos(at);
        p.level().addFreshEntity(e);return e;
    }
    public static void blade(Player p,int mode) {
        GalaxiaEntity e=create(p,BLADE,mode,p.getBoundingBox().getCenter(),AttackFrame.capture(p),base(mode),2);
        if(mode==POLARIS) for(GalaxiaEntity star:p.level().getEntitiesOfClass(GalaxiaEntity.class,p.getBoundingBox().inflate(128),
                s->s.ownerId==p.getId() && s.kind==STAR)) {
            Vec3 towards=star.position().subtract(p.getBoundingBox().getCenter()).normalize();
            if(towards.dot(ArkCombat.aim(p).subtract(p.getBoundingBox().getCenter()).normalize())<Math.cos(Math.PI/4)) star.discard();
            else {e.wheel=star;e.dashing=true;e.start=p.getBoundingBox().getCenter();star.left=60;e.invulnerable(p,20);}
            break;
        }
        e.publish();
    }
    public static void ring(Player p,boolean capricorn) {
        GalaxiaEntity e=create(p,RING,capricorn?1:0,capricorn?ArkCombat.aim(p):p.getBoundingBox().getCenter(),
                AttackFrame.capture(p),capricorn?0:GalaxiaRules.damage(3000),300);e.publish();
    }
    public int ownerId() { return level().isClientSide?view().getInt("Owner"):ownerId; }
    public int kind() { return level().isClientSide?view().getInt("Kind"):kind; }
    public CompoundTag view() { return entityData.get(VIEW); }
    public ItemStack weapon() { return weapon; }
    private Player owner() { return ownerUuid==null?null:((ServerLevel)level()).getPlayerByUUID(ownerUuid); }
    @Override protected void defineSynchedData() { entityData.define(VIEW,new CompoundTag()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance<256*256; }
    @Override public void tick() {
        super.tick(); if(level().isClientSide) {clientSnapshot.tick(view(),"Age");return;}
        Player p=owner(); if(p==null || !p.isAlive() || p.isSpectator()) {discard();return;}
        samples=new net.minecraft.nbt.ListTag();samples.add(snapshot());
        int steps=kind==STAR?12:kind==BLADE && mode!=ARIES?6:3;
        for(int step=0;step<steps && !isRemoved();step++) {
            immunity.replaceAll((id,t)->t-1);immunity.values().removeIf(t->t<=0);
            hitCooldown--;blastCooldown--;
            switch(kind) {
                case BLADE -> bladeStep(p);
                case BOLT -> boltStep(p);
                case STAR -> starStep();
                case CHAIN -> chainStep(p);
                case RING -> ringStep(p);
                case SLASH -> hitLine(p,start,end,0,60,damage);
                case SPROUT -> sproutStep(p);
                case SURFACE_WAVE -> waveStep(p);
            }
            if(isRemoved()) break;
            age++;if(--left<=0) finish(p);
            samples.add(snapshot());
        }
        if(movedPlayer && p instanceof ServerPlayer sp) {
            sp.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());movedPlayer=false;
        }
        if(!isRemoved()) publish();
    }
    private Vec3 aim(Player p) { return ArkCombat.aim(p); }
    private Vec3 center(Player p) { return p.getBoundingBox().getCenter(); }
    private void sound(String name,float volume,boolean ownerOnly,boolean relative) {
        GalaxiaCombat.sound(this,ownerId,name,volume,ownerOnly,relative);
    }
    private void sound(String name) {sound(name,1,false,false);}
    private void invulnerable(Player p,int updates) {
        ArkCombat.controls(p).dashInvulnerableUntil=Math.max(ArkCombat.controls(p).dashInvulnerableUntil,level().getGameTime()+(updates+2)/3);
    }
    private Vec3 rotate(Vec3 v,double angle) {
        Vec3 n=frame.normal();return v.scale(Math.cos(angle)).add(n.cross(v).scale(Math.sin(angle)))
                .add(n.scale(n.dot(v)*(1-Math.cos(angle))));
    }
    private void bladeStep(Player p) {
        boolean held=GalaxiaCombat.channeling(p);Vec3 c=center(p);
        if(age==0) {
            if(mode==PHOENIX || mode==POLARIS)sound("item90");
            else if(mode==ANDROMEDA)sound("item101");
        }
        if(mode==PHOENIX) {
            if(state==0) {
                double phase=frame.angle(direction);
                frame=AttackFrame.capture(p);
                direction=frame.direction(phase);
            }
            if(!held && state==0) {
                if(charge<300) {sound("item77");discard();return;}
                sound("item80");
                state=1;left=90;direction=aim(p).subtract(c).normalize();
                for(int i=0;i<6;i++) bolt(p,c,rotate(direction,-Math.PI/4+Math.PI/2*i/5),30,damage,1,.1f,(float)Math.PI/50,80);
            }
            if(state==0) {
                if(charge>=300 && !madeChargeSound){sound("item76",1,true,true);madeChargeSound=true;}
                double phase=frame.angle(direction);
                if(phase>-3*Math.PI/4 && phase<-Math.PI/4 && hasMadeSound) hasMadeSound=false;
                else if(phase>Math.PI/4 && phase<3*Math.PI/4 && !hasMadeSound) {directFire=true;hasMadeSound=true;sound("item71",1,true,true);}
                if(charge>=300) {
                    charge++;
                    if((charge+overCharge)%30==29) bolt(p,c,frame.direction(random.nextDouble()*Math.PI*2),15,damage*.5f,1,.1f,(float)Math.PI/50,80);
                }
                if(charge>=450 && directFire && direction.dot(aim(p).subtract(c).normalize())>Math.cos(.1)) {
                    bolt(p,c,aim(p).subtract(c).normalize(),15,damage*.5f,1,.1f,(float)Math.PI/50,80);directFire=false;
                }
                scale=1+charge/600f*1.5f;
                direction=rotate(direction,Math.max(.4,Math.min(1,charge/600d))*Math.PI/20).normalize();
                setPos(c.add(direction.scale(scale*10/16d)));left=91;
                if(++charge>600){charge=600;overCharge++;}
            } else {
                double t=(90-left)/90d;
                setPos(c.add(direction.scale(scale*10/16d+440/16d*throwCurve(t))));
                scale=(float)((1+charge/600d*1.5)*Math.max(.3,1-Math.max(0,(t-.6)/.4)));
            }
            rotation=(float)frame.angle(direction);
            hitLine(p,position(),position().add(direction.scale(145/16d*scale)),25/16d*scale,30,
                    damage*(state==1?1+2.2f*charge/600f:.5f+2.1f*charge/600f));
        } else if(mode==ARIES) {
            if(!held) {
                Vec3 offset=c.subtract(position());
                if(offset.length()<30/16d || offset.length()>2000/16d || velocity.length()>100/16d) {discard();return;}
                if(left<=2) velocity=velocity.scale(10);
                double dot=velocity.normalize().dot(offset.normalize());
                Vec3 turned=ArkGeometry.turn(velocity,offset,dot<Math.cos(Math.PI/4)?Math.PI/20:Math.PI,frame.normal());
                velocity=turned.scale((dot<Math.cos(Math.PI/4)?.98:1.05)/.995);
                setPos(position().add(velocity));scale=(float)Math.max(.3,Math.min(2,offset.length()/(650/32d)));left=4;
            } else {
                // Source checks CantUseHoldout before advancing ChainSwapTimer.
                if(ariesChainSwapTimer==0) setPos(c);
                if(ariesChainSwapTimer%27==0) {
                    // The in-game Aries blade replays Item120 once per second while thrown.
                    sound("item120",.5f,false,false);
                }
                double dis=ariesDisplacement(ariesChainSwapTimer);scale=(float)ariesScale(ariesChainSwapTimer);
                Vec3 point=ArkGeometry.towards(position().lerp(aim(p),.05*dis),aim(p),40/16d*dis);
                Vec3 off=point.subtract(c);setPos(c.add(off.length()>650/16d?off.normalize().scale(650/16d):off));
                velocity=position().subtract(c).normalize().scale(1/16d);left=2;
                if(ariesChainSwapTimer==21) {GalaxiaEntity chain=create(p,CHAIN,0,c,frame,damage*.2f,20);chain.end=aim(p);chain.publish();}
                ariesChainSwapTimer++;
            }
            state=held?0:1;
            rotation=(float)((level().getGameTime()/20d+age%3/60d)*25);
            hitBox(p,position(),50/16d*scale,10,damage);
        } else if(mode==POLARIS) {
            if(!held) {finish(p);return;}
            direction=aim(p).subtract(c).normalize();
            if(!dashing)frame=aimFrame(direction,AttackFrame.capture(p).right());
            rotation=(float)frame.angle(direction);
            double ratio=clamp(charge/325d);scale=(float)(1+ratio);setPos(c.add(direction.scale(60/16d)));left=2;
            if(dashing) {
                if(wheel==null || wheel.isRemoved()) endDash(p);
                else {
                    Vec3 offset=wheel.position().subtract(c);
                    if(offset.length()<60/16d) {wheel.discard();endDash(p);}
                    else if(!movePlayer(p,offset.normalize().scale(60/16d))) {wheel.discard();endDash(p);}
                }
            }
            if(charge>=30) {int cooldown=30-(int)(14*ratio);
                hitLine(p,c,c.add(direction.scale(145/16d*scale)),86/16d*scale,cooldown,(float)(damage*(1+ratio)*cooldown/30));}
            if(ratio>.85) {
                if(chargeSoundCooldown<=0){sound("book",2.5f,true,true);chargeSoundCooldown=20;}
            } else chargeSoundCooldown--;
            charge=Math.min(650,charge+1);
        } else {
            if(!held && state==0) {
                if(charge<90) {sound("item109");discard();return;}
                sound("item120",.5f,false,false);
                state=1;duration=left=lungeDuration(charge);displacement=position().subtract(c);
            }
            if(state==0) {
                direction=aim(p).subtract(c).normalize();
                frame=aimFrame(direction,AttackFrame.capture(p).right());
                setPos(c.add(direction.scale(70/16d*chargeDisplacement(charge/360d))));
                overCharge--;
                charge=Math.min(360,charge+1);left=2;
                if(indicator<4 && charge>=90*(indicator+1)) {
                    int count=indicator==3?9:5;
                    for(int i=0;i<count;i++) bolt(p,c,frame.direction(Math.PI*2*i/count),10,damage*.2f,1,.75f,(float)Math.PI/50,indicator==3?50:30);
                    indicator++;
                    sound(indicator==4?"beacon":"item79");
                    overCharge=20;
                }
            } else {
                setPos(c.add(displacement.lerp(direction.scale(40/16d),clamp((duration-left)/(double)duration*2))));
                Vec3 tip=c.add(direction.scale(120/16d*scale));
                BlockHitResult impact=level().clip(new ClipContext(c,tip,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if(impact.getType()!=HitResult.Type.MISS) {slam(p,impact.getLocation(),Vec3.atLowerCornerOf(impact.getDirection().getNormal()));finish(p);return;}
                if(!movePlayer(p,direction.scale(30/16d))) {finish(p);return;}
                hitLine(p,center(p),center(p).add(direction.scale(145/16d*scale)),25/16d*scale,16,(float)(damage*(1+6*Math.pow(charge/360d,2))));
            }
            scale=1+charge/360f*.3f;rotation=(float)frame.angle(direction);
        }
    }
    private void bolt(Player p,Vec3 at,Vec3 dir,double speed,float damage,float size,float hue,float homing,int life) {
        GalaxiaEntity b=create(p,BOLT,0,at,frame,damage,life);b.velocity=dir.scale(speed/16);b.scale=size;b.hue=hue;b.homing=homing;b.publish();
    }
    private void boltStep(Player p) {
        if(target==null) target=ArkCombat.nearest(p,position(),812/16d);
        else if(!target.isAlive()) target=null;
        else if(velocity.dot(target.getBoundingBox().getCenter().subtract(position()))>0)
            velocity=ArkGeometry.turn(velocity,target.getBoundingBox().getCenter().subtract(position()),homing,frame.normal());
        Vec3 old=position();setPos(old.add(velocity));rotation=(float)frame.angle(velocity);
        hitLine(p,old,position(),30/16d*scale,80,damage);
    }
    private void starStep() {
        if(age==0)sound("item90");
        velocity=velocity.scale(.96);
        // Original AI advances once explicitly; Terraria then advances again through tile collision.
        setPos(position().add(velocity));
        AABB box=new AABB(position().subtract(45/32d,45/32d,45/32d),position().add(45/32d,45/32d,45/32d));
        Vec3 allowed=Entity.collideBoundingBox(this,velocity,box,level(),List.of());
        setPos(position().add(allowed));
        if(allowed.distanceToSqr(velocity)>1e-12) velocity=new Vec3(
                Math.abs(allowed.x-velocity.x)>1e-8?0:velocity.x,
                Math.abs(allowed.y-velocity.y)>1e-8?0:velocity.y,
                Math.abs(allowed.z-velocity.z)>1e-8?0:velocity.z).scale(.95);
        Player p=owner();hitLine(p,position().subtract(direction.scale(42/16d*scale)),position().add(direction.scale(42/16d*scale)),76/16d*scale,60,damage);
    }
    private void chainStep(Player p) {
        setPos(center(p));if(GalaxiaCombat.channeling(p)) left=20;
        Vec3 off=aim(p).subtract(position());end=position().add(off.length()>650/16d?off.normalize().scale(650/16d):off);
        if(chainTimer%15==0 && left>=20) {
            chainPoints.clear();previousEnd=off;chainTimer=1;chainGenerationAge=age;
            addChainPoint(Vec3.ZERO,1+random.nextDouble()*.5,false);
            Vec3 line=end.subtract(position()),across=frame.normal().cross(line).normalize();
            for(double fraction=.2+random.nextDouble()*.3;fraction<1;fraction+=.2+random.nextDouble()*.3) {
                Vec3 point=line.scale(fraction).add(across.scale((random.nextDouble()*100-50)/16));
                addChainPoint(point,1+random.nextDouble()*.5,false);
                if(random.nextInt(3)==0) {
                    Vec3 branch=line.scale(fraction).add(across.scale((random.nextDouble()*100-50)/16));
                    addChainPoint(branch,1+random.nextDouble()*.5,true);
                }
            }
            addChainPoint(line,1+random.nextDouble()*.5,false);
        }
        chainTimer++;
        if(off.subtract(previousEnd).length()>120/16d)chainTimer=15;
        hitLine(p,position(),end,30/16d,30,damage);
    }
    private void addChainPoint(Vec3 point,double size,boolean branch) {
        CompoundTag tag=new CompoundTag();vector(tag,"At",point);tag.putDouble("Scale",size);tag.putBoolean("Branch",branch);
        // Separate visual RNG: adding sparkle rotation must not advance gameplay randomness.
        tag.putDouble("Rotation",new java.util.Random(getUUID().getLeastSignificantBits()^age^chainPoints.size()).nextDouble()*Math.PI*2);chainPoints.add(tag);
    }
    private void ringStep(Player p) {
        velocity=velocity.scale(.95);
        double ratio=clamp((left-150)/150d);
        if(mode==0) setPos(position().lerp(center(p),.4*ratio));
        else {Vec3 off=position().subtract(center(p));velocity=off.normalize().scale(ratio*3/16*(5-Math.min(4,off.length()*16/150)));}
        setPos(position().add(velocity));hitBox(p,position(),95/16d,10,damage);
    }
    private void hitBox(Player p,Vec3 at,double radius,int cooldown,float amount) {
        AABB box=new AABB(at,at).inflate(radius);
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,box,e->ArkCombat.canHit(p,e))) hit(p,t,cooldown,amount);
    }
    private void hitLine(Player p,Vec3 a,Vec3 b,double width,int cooldown,float amount) {
        for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(width/2+1),e->ArkCombat.canHit(p,e)))
            if(ArkGeometry.line(t.getBoundingBox(),a,b,width,frame)) hit(p,t,cooldown,amount);
    }
    private void hit(Player p,LivingEntity t,int cooldown,float amount) {
        if(isRemoved() || immunity.containsKey(t.getUUID())) return;
        immunity.put(t.getUUID(),cooldown);
        int fire=weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT);
        boolean startedFire=amount>0 && fire>0 && !t.isOnFire();
        if(startedFire)t.setSecondsOnFire(1);
        float enchanted=amount>0?amount+net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(weapon,t.getMobType()):0;
        boolean success=amount==0 || WeaponDamage.hurt(t,new net.minecraft.world.damagesource.DamageSource(damageSources().playerAttack(p).typeHolder(),this,p),enchanted);
        if(!success && startedFire)t.clearFire();
        if(!success) return;
        visualEvent("galaxia_hit",t.getBoundingBox().getCenter(),t.getDeltaMovement());
        if(amount>0) {
            if(fire>0)t.setSecondsOnFire(4*fire);
            p.setLastHurtMob(t);
            net.minecraft.world.item.enchantment.EnchantmentHelper.doPostHurtEffects(t,p);
            weapon.getAllEnchantments().forEach((enchantment,level)->enchantment.doPostAttack(p,t,level));
        }
        if(kind==BOLT) {discard();return;}
        if(kind==RING) {
            if(mode==0 && random.nextFloat()<.4) p.heal(GalaxiaRules.damage(3));
            else t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.ARMOR_CRUNCH.get(),67));
        }
        if(kind==BLADE && mode==ARIES && blastCooldown<=0) {
            Set<UUID> excluded=new HashSet<>();excluded.add(t.getUUID());
            for(int i=0;i<3;i++) {
                LivingEntity next=null;double distance=500/16d;
                for(LivingEntity candidate:level().getEntitiesOfClass(LivingEntity.class,t.getBoundingBox().inflate(distance),e->ArkCombat.enemy(p,e))) {
                    double d=candidate.getBoundingBox().getCenter().distanceTo(t.getBoundingBox().getCenter());
                    if(!excluded.contains(candidate.getUUID()) && d<distance) {distance=d;next=candidate;}
                }
                if(next==null) break;excluded.add(next.getUUID());
                bolt(p,t.getBoundingBox().getCenter(),next.getBoundingBox().getCenter().subtract(t.getBoundingBox().getCenter()).normalize(),25,amount*.5f,2,.9f,(float)Math.PI/10,80);
            }
            blastCooldown=30;
        }
        if(kind==BLADE && mode==POLARIS && hitCooldown<=0) {sound("hit30",1,true,false);charge+=80;hitCooldown=20;invulnerable(p,10);}
        if(kind==BLADE && mode==ANDROMEDA) invulnerable(p,20);
        if(kind==SLASH) for(int i=0;i<3;i++) bolt(p,center(p),rotate(t.getBoundingBox().getCenter().subtract(center(p)).normalize(),(random.nextDouble()-.5)*Math.PI/2),30,GalaxiaRules.damage(1300),1,.55f,(float)Math.PI/100,100);
    }
    private boolean movePlayer(Player p,Vec3 delta) {
        // Split the original substep to keep the complete player volume out of intermediate blocks.
        int slices=Math.max(1,(int)Math.ceil(delta.length()/.2));Vec3 step=delta.scale(1d/slices);
        for(int i=0;i<slices;i++) {
            if(level().getBlockCollisions(p,p.getBoundingBox().move(step)).iterator().hasNext()) return false;
            p.setPos(p.position().add(step));movedPlayer=true;
        }
        p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);
        return true;
    }
    private void endDash(Player p) {
        if(!dashing)return;dashing=false;
        sound("meaty");
        for(int i=0;i<5;i++) bolt(p,center(p),frame.direction(random.nextDouble()*Math.PI*2),15,GalaxiaRules.damage(1300),1,.55f,(float)Math.PI/50,100);
        GalaxiaEntity slash=create(p,SLASH,0,start.lerp(center(p),.5),frame,damage*6*2,20);slash.start=start;slash.end=center(p);slash.publish();
    }
    private void slam(Player p,Vec3 at,Vec3 normal) {
        sound("impact",1.5f,false,false);
        bolt(p,at,direction.scale(-1),16,damage,3,.75f,(float)Math.PI/25,50);
        Vec3 tangent=frame.right().subtract(normal.scale(frame.right().dot(normal))).normalize();
        if(tangent.lengthSqr()<.5) tangent=normal.cross(new Vec3(0,0,1)).normalize();
        if(tangent.lengthSqr()<.5) tangent=normal.cross(new Vec3(0,1,0)).normalize();
        Vec3 other=normal.cross(tangent).normalize();
        GalaxiaEntity wave=create(p,SURFACE_WAVE,0,at,frame,damage*1.2f,2);
        wave.direction=normal;wave.start=at;wave.velocity=tangent;wave.end=other;wave.sproutSize=charge/360d;wave.indicator=3;
        wave.wait=(1-wave.sproutSize)*34;wave.publish();
    }
    private void waveStep(Player p) {
        left=2;if(wait-->0)return;
        double radius=(mode+1)*150/16d;
        // Source: two independent SideSprouts chains, one eruption each per generation.
        // Rotate successive opposite pairs around the impact normal without adding projectiles.
        int generations=1;
        for(double size=sproutSize/Math.pow(.8,mode);size*.8>.4;size*=.8) generations++;
        int surviving=0;
        for(int i=0;i<2;i++) {
            if((indicator & (1<<i))==0)continue;
            double angle=Math.PI*i+Math.PI*mode/generations;
            Vec3 along=velocity.scale(Math.cos(angle)).add(end.scale(Math.sin(angle)));
            Vec3 above=start.add(along.scale(radius)).add(direction.scale(150/16d));
            BlockHitResult hit=level().clip(new ClipContext(above,above.subtract(direction.scale(300/16d)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()==HitResult.Type.MISS)continue;
            surviving|=1<<i;
            GalaxiaEntity sprout=create(p,SPROUT,mode,hit.getLocation(),frame,damage,30);
            sprout.direction=Vec3.atLowerCornerOf(hit.getDirection().getNormal());sprout.sproutSize=sproutSize;sprout.publish();
        }
        if(surviving!=0 && sproutSize*.8>.4) {
            GalaxiaEntity next=create(p,SURFACE_WAVE,mode+1,start,frame,damage,2);
            next.direction=direction;next.start=start;next.velocity=velocity;next.end=end;next.sproutSize=sproutSize*.8;next.indicator=surviving;
            next.wait=1+Math.sqrt(1-Math.pow(next.sproutSize-1,2))*3;next.publish();
        }
        discard();
    }
    private void sproutStep(Player p) {
        if(wait>0) {wait--;left=30;return;}
        if(left==2) {sound("eruption");bolt(p,position(),direction,18,damage,(float)(sproutSize*3),.75f,(float)Math.PI/25,50);}
    }
    private void finish(Player p) {
        if(kind==BLADE && mode==POLARIS) {
            if(charge/325d>.85) {
                GalaxiaEntity star=create(p,STAR,0,position(),frame,damage*.8f,120);
                star.charge=charge;star.direction=direction;star.velocity=direction.scale(1);star.scale=1+(float)clamp(charge/325d);star.publish();
            }
            if(dashing && wheel!=null) wheel.discard();
        }
        discard();
    }
    private CompoundTag snapshot() {
        CompoundTag s=new CompoundTag();s.putInt("Owner",ownerId);s.putInt("Kind",kind);s.putInt("Mode",mode);s.putInt("State",state);
        s.putInt("Age",age);s.putInt("Left",left);s.putInt("Charge",charge);s.putFloat("Scale",scale);s.putFloat("Rotation",rotation);s.putFloat("Hue",hue);
        vector(s,"F",frame.forward());vector(s,"R",frame.right());vector(s,"End",end);vector(s,"Start",start);vector(s,"Direction",direction);
        s.putDouble("Size",sproutSize);vector(s,"Position",position());
        s.putInt("OverCharge",overCharge);s.putBoolean("Dashing",dashing);s.putDouble("Wait",wait);
        vector(s,"Velocity",velocity);
        Entity owner=level().getEntity(ownerId);
        vector(s,"Center",owner==null?position():owner.getBoundingBox().getCenter());
        vector(s,"OwnerVelocity",owner==null?Vec3.ZERO:owner.getDeltaMovement());
        s.putDouble("Time",level().getGameTime()/20d+age%3/60d);
        if(kind==CHAIN){s.put("Points",chainPoints.copy());s.putInt("ChainTimer",chainTimer);s.putInt("ChainAge",age-chainGenerationAge);}
        return s;
    }
    private void visualEvent(String event,Vec3 at,Vec3 targetVelocity) {
        CompoundTag data=snapshot();data.putString("Event",event);data.putInt("Id",getId());
        var finalSamples=samples.copy();
        if(event.equals("galaxia_death") && (finalSamples.isEmpty() || finalSamples.getCompound(finalSamples.size()-1).getInt("Age")<age))
            finalSamples.add(snapshot());
        data.put("Samples",finalSamples);vector(data,"At",at);vector(data,"TargetVelocity",targetVelocity);
        net.everla.projectc.arkofcosmos.network.ArkNetwork.effect(this,data);
    }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide && !isRemoved() && reason==RemovalReason.DISCARDED) {
            if(kind==STAR)sound("item60");
            else if(kind==BLADE && mode==POLARIS)sound("hit43");
            visualEvent("galaxia_death",position(),Vec3.ZERO);
        }
        super.remove(reason);
    }
    private void publish() {
        CompoundTag s=snapshot();s.put("Samples",samples.copy());
        if(kind==CHAIN) {s.put("Points",chainPoints.copy());s.putInt("ChainTimer",chainTimer);}
        entityData.set(VIEW,s);
    }
    public static AttackFrame aimFrame(Vec3 direction,Vec3 right) {
        Vec3 forward=direction.normalize();
        Vec3 across=right.subtract(forward.scale(right.dot(forward))).normalize();
        if(across.lengthSqr()<1e-8)across=forward.cross(new Vec3(0,1,0)).normalize();
        if(across.lengthSqr()<1e-8)across=new Vec3(1,0,0);
        return new AttackFrame(forward,across);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (level().isClientSide && key.equals(VIEW) && !view().isEmpty())
            clientSnapshot.initialize(view());
        super.onSyncedDataUpdated(key);
    }
    public CompoundTag effectSnapshot() {
        return level().isClientSide ? clientSnapshot.state() : view();
    }
    public CompoundTag renderState(float partial) {
        CompoundTag state=effectSnapshot().copy();var path=state.getList("Samples",10);if(path.size()<2)return state;
        double time=Math.max(0,Math.min(1,partial))*(path.size()-1);int index=Math.min(path.size()-2,(int)time);
        double ratio=time-index;CompoundTag a=path.getCompound(index),b=path.getCompound(index+1);
        state.putDouble("RenderAge",a.getInt("Age")+(b.getInt("Age")-a.getInt("Age"))*ratio);
        for(String key:java.util.List.of("Age","Left","Charge","State","OverCharge","ChainTimer","ChainAge"))
            if(a.contains(key))state.putInt(key,a.getInt(key));
        if(a.contains("Points"))state.put("Points",a.get("Points").copy());
        double delta=b.getFloat("Rotation")-a.getFloat("Rotation");
        state.putFloat("Rotation",(float)(a.getFloat("Rotation")+Math.atan2(Math.sin(delta),Math.cos(delta))*ratio));
        state.putFloat("Scale",(float)(a.getFloat("Scale")+(b.getFloat("Scale")-a.getFloat("Scale"))*ratio));
        vector(state,"Position",vector(a,"Position").lerp(vector(b,"Position"),ratio));
        vector(state,"Center",vector(a,"Center").lerp(vector(b,"Center"),ratio));
        vector(state,"Direction",vector(a,"Direction").lerp(vector(b,"Direction"),ratio).normalize());
        if(state.getInt("Kind")==BLADE && state.getInt("Mode")==PHOENIX) {
            AttackFrame interpolated=AttackFrame.interpolateView(new AttackFrame(vector(a,"F"),vector(a,"R")),
                    new AttackFrame(vector(b,"F"),vector(b,"R")),ratio);
            vector(state,"F",interpolated.forward());vector(state,"R",interpolated.right());
            if(state.getInt("State")==0) vector(state,"Direction",interpolated.direction(state.getFloat("Rotation")));
        }
        if(state.getInt("Kind")==BLADE && (state.getInt("Mode")==POLARIS || state.getInt("Mode")==ANDROMEDA)) {
            AttackFrame interpolated=AttackFrame.interpolateView(new AttackFrame(vector(a,"F"),vector(a,"R")),
                    new AttackFrame(vector(b,"F"),vector(b,"R")),ratio);
            AttackFrame aimed=aimFrame(vector(state,"Direction"),interpolated.right());
            vector(state,"F",aimed.forward());vector(state,"R",aimed.right());
            state.putFloat("Rotation",0);
        }
        return state;
    }
    public static Vec3 vector(CompoundTag s,String key) {return new Vec3(s.getDouble(key+"X"),s.getDouble(key+"Y"),s.getDouble(key+"Z"));}
    private static void vector(CompoundTag s,String key,Vec3 v) {s.putDouble(key+"X",v.x);s.putDouble(key+"Y",v.y);s.putDouble(key+"Z",v.z);}
}
