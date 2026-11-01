package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

public final class OldOneEntity extends Entity {
 public enum Kind { SWORD,EON,ORB,DERIVED }
 private static final EntityDataAccessor<Integer> K=SynchedEntityData.defineId(OldOneEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(OldOneEntity.class,EntityDataSerializers.INT);
 private UUID owner; private AttackFrame frame; public int age,parameter; private int life;
 public OldOneEntity(EntityType<? extends OldOneEntity> t,Level l){super(t,l);noPhysics=true;setNoGravity(true);}
 public static OldOneEntity create(Player p,Kind k,Vec3 pos,Vec3 vel,AttackFrame f){OldOneEntity e=new OldOneEntity(ArkOfCosmos.OLD_ONE_ATTACK.get(),p.level());e.owner=p.getUUID();e.entityData.set(K,k.ordinal());e.entityData.set(OWNER,p.getId());e.frame=f;e.setPos(pos);e.setDeltaMovement(vel);e.life=k==Kind.SWORD?15:k==Kind.EON?160:k==Kind.ORB?80:100;p.level().addFreshEntity(e);return e;}
 @Override protected void defineSynchedData(){entityData.define(K,0);entityData.define(OWNER,-1);}
 public Kind kind(){return Kind.values()[Math.max(0,Math.min(Kind.values().length-1,entityData.get(K)))];} public int ownerId(){return entityData.get(OWNER);} public AttackFrame frame(){return frame==null?new AttackFrame(Vec3.ZERO,Vec3.ZERO):frame;}
 public int parameter(){return parameter;}
 @Override public void tick(){super.tick();if(level().isClientSide)return;Player p=owner==null?null:level().getPlayerByUUID(owner);if(p==null||!p.isAlive()){discard();return;}age++; if(kind()==Kind.SWORD){setPos(p.getBoundingBox().getCenter());} else {move(MoverType.SELF,getDeltaMovement());if(kind()==Kind.ORB){if(age>8){LivingEntity t=ArkCombat.nearest(p,position(),50);if(t!=null)setDeltaMovement(getDeltaMovement().scale(19d/20).add(t.getBoundingBox().getCenter().subtract(position()).normalize().scale(.4)));}if(age>45)setDeltaMovement(new Vec3(getDeltaMovement().x*.98,getDeltaMovement().y+.018,getDeltaMovement().z*.98));}else if(kind()==Kind.DERIVED)setDeltaMovement(getDeltaMovement().scale(.95));}
  hit(p);if(age>=life)discard();}
 private void hit(Player p){double r=kind()==Kind.SWORD?10:kind()==Kind.EON?1.25:kind()==Kind.ORB?1:2;for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(r),x->ArkCombat.canHit(p,x))){float damage=20; if(kind()==Kind.ORB)damage*=.5; if(!WeaponDamage.hurt(t,t.damageSources().playerAttack(p),damage))continue; if(kind()==Kind.ORB){OldOneEffects.orb(t,OldOneEnvironment.zone(p));if(OldOneEnvironment.zone(p)==OldOneEnvironment.Zone.ASTRAL){OldOneEntity star=create(p,Kind.DERIVED,position(),getDeltaMovement(),frame());star.life=60;}}OldOneEffects.grant(p,OldOneEnvironment.zone(p));if(kind()!=Kind.SWORD)discard();}}
 @Override protected void readAdditionalSaveData(CompoundTag t){} @Override protected void addAdditionalSaveData(CompoundTag t){}
 @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
 @Override public void remove(RemovalReason r){super.remove(r);}
}
