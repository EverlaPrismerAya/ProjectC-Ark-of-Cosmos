/*
 * Server-side adaptation of CrystalAura and crystal forecasting from Meteor Client.
 * Copyright (c) Meteor Development. GPL-3.0 (see LICENSE.txt and THIRD_PARTY_NOTICES.md).
 * Source: sample/meteor-client-26.2, commit 2385f7b4bb9de1a698b4503254bd32f30fd7a87d.
 */
package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Meteor's break/place pipeline, operating on the authoritative server world rather than packets. */
public final class FourthCalamityCrystalAura {
    public enum Support { DISABLED, ACCURATE, FAST }
    /** Boss profile: aggressive placement, real self damage, real line of sight and native action budgets. */
    public static final class Settings {
        public double targetRange=10, minDamage=1.5, maxSelfDamage=Double.POSITIVE_INFINITY;
        public boolean predictMovement=true, antiSuicide=false, ignoreNakeds=false, rotate=true;
        public double yawSteps=180, placeRange=4.5, breakRange=4.5;
        public boolean place=true, breakCrystals=true, onlyOwn=false, fastBreak=true, smartDelay=false, placement112=false;
        public int placeDelay=0, breakDelay=0, supportDelay=1, switchDelay=0, minimumAge=0, breakAttempts=2, attackFrequency=20;
        public Support support=Support.ACCURATE;
        public boolean antiWeakness=true, facePlace=true, facePlaceMissingArmor=false, forceFacePlace=false;
        public double facePlaceHealth=8, facePlaceDurability=2, pauseHealth=0;
        public boolean pauseOnUse=true;
    }
    public final Settings settings=new Settings();
    private final FourthCalamityEntity boss;
    private final List<LivingEntity> targets=new ArrayList<>();
    private final Map<UUID,Integer> attempts=new HashMap<>(),waiting=new HashMap<>();
    private final Deque<Integer> attacks=new ArrayDeque<>();
    private BlockPos supportBase;
    private int placeAt,breakAt,switchAt,supportUntil;
    public FourthCalamityCrystalAura(FourthCalamityEntity boss){this.boss=boss;}
    public void clear(){targets.clear();attempts.clear();waiting.clear();attacks.clear();supportBase=null;placeAt=breakAt=switchAt=0;}
    public boolean tick(LivingEntity primary) {
        int tick=boss.tickCount;
        while(!attacks.isEmpty()&&tick-attacks.peekFirst()>=20)attacks.removeFirst();
        var nearby=boss.level().getEntitiesOfClass(EndCrystal.class,boss.getBoundingBox().inflate(FourthCalamityActions.REACH));
        Set<UUID> loaded=new HashSet<>();for(var crystal:nearby)loaded.add(crystal.getUUID());
        attempts.keySet().retainAll(loaded);waiting.keySet().retainAll(loaded);
        findTargets(primary);
        if(targets.isEmpty()||boss.getHealth()+boss.getAbsorptionAmount()<=settings.pauseHealth
            ||settings.pauseOnUse&&boss.isUsingItem()||!boss.actions.terrainAllowed())return false;
        EndCrystal best=null;double damage=0;
        for(var crystal:nearby){double value=breakDamage(crystal);if(value>damage){damage=value;best=crystal;}}
        boolean opportunity=best!=null;
        if(best!=null&&settings.breakCrystals&&tick>=breakAt&&tick>=switchAt)attack(best);
        // Multiplace suppression: a surviving useful crystal must be resolved before another is placed.
        if(best!=null&&best.isAlive())return true;
        if(!settings.place||tick<placeAt)return opportunity;
        BlockPos selected=chooseBase();
        if(selected==null)return opportunity;
        if(!rotate(Vec3.atCenterOf(selected).add(0,.5,0)))return true;
        var state=boss.level().getBlockState(selected);
        if(!foundation(state)) {
            if(boss.actions.place(Items.OBSIDIAN,selected)) {
                supportBase=selected;supportUntil=tick+20;placeAt=tick+Math.max(1,settings.supportDelay);
            }
            return true;
        }
        if(boss.actions.crystal(selected)) {
            supportBase=null;placeAt=tick+settings.placeDelay;
            if(settings.fastBreak)for(var crystal:boss.level().getEntitiesOfClass(EndCrystal.class,new AABB(selected.above()).inflate(1))) {
                if(CalamityAttackGuard.owned(crystal,boss)&&crystal.blockPosition().equals(selected.above())&&breakDamage(crystal)>0){attack(crystal);break;}
            }
        }
        return true;
    }
    private boolean foundation(BlockState state){return state.is(Blocks.OBSIDIAN)||state.is(Blocks.BEDROCK);}
    private boolean validTarget(LivingEntity target) {
        return target!=boss&&!(target instanceof FourthCalamityEntity)&&target.isAlive()&&!target.isSpectator()
            &&!boss.isAlliedTo(target)&&boss.distanceToSqr(target)<=settings.targetRange*settings.targetRange
            &&(!(target instanceof Player p)||!p.isCreative());
    }
    private void findTargets(LivingEntity primary) {
        targets.clear();
        for(var target:boss.level().getEntitiesOfClass(LivingEntity.class,boss.getBoundingBox().inflate(settings.targetRange))) {
            if(!validTarget(target)||!(target instanceof Player||target==primary||target.getType()==EntityType.WARDEN||target.getType()==EntityType.WITHER))continue;
            if(settings.ignoreNakeds&&target instanceof Player&&target.getMainHandItem().isEmpty()&&target.getOffhandItem().isEmpty()) {
                boolean naked=true;for(var armor:target.getArmorSlots())if(!armor.isEmpty()){naked=false;break;}
                if(naked)continue;
            }
            targets.add(target);
        }
    }
    private boolean facePlace() {
        if(!settings.facePlace)return false;
        if(settings.forceFacePlace)return true;
        for(var target:targets) {
            if(target.getHealth()+target.getAbsorptionAmount()<=settings.facePlaceHealth)return true;
            for(var armor:target.getArmorSlots()) {
                if(armor.isEmpty()){if(settings.facePlaceMissingArmor)return true;}
                else if(armor.isDamageableItem()&&(armor.getMaxDamage()-armor.getDamageValue())*100.0/armor.getMaxDamage()<=settings.facePlaceDurability)return true;
            }
        }
        return false;
    }
    private double damage(Vec3 at,BlockPos base,boolean breaking,boolean fast) {
        float self=forecast(boss,at,base);
        if(self>settings.maxSelfDamage||settings.antiSuicide&&self>=boss.getHealth()+boss.getAbsorptionAmount())return 0;
        double total=0;
        LivingEntity nearest=fast?targets.stream().min(Comparator.comparingDouble(boss::distanceToSqr)).orElse(null):null;
        for(var target:targets) {
            if(fast&&target!=nearest||breaking&&settings.smartDelay&&target.hurtTime>0)continue;
            total+=forecast(target,at,base);
        }
        return total>=Math.min(settings.minDamage,facePlace()?1.5:settings.minDamage)?total:0;
    }
    private double breakDamage(EndCrystal crystal) {
        if(!crystal.isAlive()||settings.onlyOwn&&!CalamityAttackGuard.owned(crystal,boss)||crystal.tickCount<settings.minimumAge
            ||attempts.getOrDefault(crystal.getUUID(),0)>settings.breakAttempts||boss.tickCount<waiting.getOrDefault(crystal.getUUID(),0)
            ||boss.getEyePosition().distanceToSqr(crystal.getBoundingBox().getCenter())>settings.breakRange*settings.breakRange
            ||!boss.actions.canAttack(crystal))return 0;
        return damage(crystal.position(),crystal.blockPosition().below(),true,false);
    }
    private void attack(EndCrystal crystal) {
        if(attacks.size()>=settings.attackFrequency||!attacks.isEmpty()&&attacks.peekLast()==boss.tickCount)return;
        var weakness=boss.getEffect(MobEffects.WEAKNESS);var strength=boss.getEffect(MobEffects.DAMAGE_BOOST);
        if(settings.antiWeakness&&weakness!=null&&(strength==null||strength.getAmplifier()<=weakness.getAmplifier())&&!boss.getMainHandItem().is(Items.NETHERITE_SWORD)) {
            boss.actions.hold(Items.NETHERITE_SWORD);switchAt=boss.tickCount+Math.max(1,settings.switchDelay);return;
        }
        if(boss.tickCount<switchAt||!rotate(crystal.getBoundingBox().getCenter()))return;
        boolean hit=boss.actions.breakCrystal(crystal);
        attacks.addLast(boss.tickCount);attempts.merge(crystal.getUUID(),1,Integer::sum);
        waiting.put(crystal.getUUID(),boss.tickCount+10);breakAt=boss.tickCount+settings.breakDelay;
        if(hit&&!crystal.isAlive()){attempts.remove(crystal.getUUID());waiting.remove(crystal.getUUID());}
    }
    private boolean validBase(BlockPos base) {
        if(!boss.level().hasChunkAt(base)||!boss.actions.crystalSpace(base)
            ||boss.getEyePosition().distanceToSqr(Vec3.atBottomCenterOf(base.above()))>settings.placeRange*settings.placeRange
            ||settings.placement112&&!boss.level().isEmptyBlock(base.above(2)))return false;
        var state=boss.level().getBlockState(base);
        return foundation(state)?boss.actions.canUse(base):settings.support!=Support.DISABLED&&state.canBeReplaced()&&boss.actions.canPlace(base);
    }
    private BlockPos chooseBase() {
        if(supportBase!=null&&boss.tickCount<=supportUntil&&validBase(supportBase)
            &&damage(Vec3.atBottomCenterOf(supportBase.above()),supportBase,false,false)>0)return supportBase;
        supportBase=null;
        int radius=(int)Math.ceil(Math.min(FourthCalamityActions.REACH,settings.placeRange));
        BlockPos center=boss.blockPosition(),best=null;double bestDamage=0;boolean bestExisting=false;
        for(var pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))) {
            if(!validBase(pos))continue;
            boolean existing=foundation(boss.level().getBlockState(pos));
            double value=damage(Vec3.atBottomCenterOf(pos.above()),pos,false,!existing&&settings.support==Support.FAST);
            if(value>0&&((existing&&!bestExisting)||(existing==bestExisting&&value>bestDamage))) {
                best=pos.immutable();bestDamage=value;bestExisting=existing;
            }
        }
        return best;
    }
    private boolean rotate(Vec3 point) {
        if(!settings.rotate)return true;
        Vec3 delta=point.subtract(boss.getEyePosition());
        float yaw=(float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90;
        float pitch=(float)(-Mth.atan2(delta.y,delta.horizontalDistance())*180/Math.PI);
        boss.setYRot(Mth.approachDegrees(boss.getYRot(),yaw,(float)settings.yawSteps));boss.setXRot(pitch);
        boss.setYHeadRot(boss.getYRot());boss.setYBodyRot(boss.getYRot());
        return Math.abs(Mth.wrapDegrees(yaw-boss.getYRot()))<.01;
    }
    private float forecast(LivingEntity target,Vec3 explosion,BlockPos support) {
        Vec3 motion=settings.predictMovement?target.getDeltaMovement():Vec3.ZERO;
        if(!boss.level().noCollision(target,target.getBoundingBox().move(motion)))motion=Vec3.ZERO;
        Vec3 position=target.position().add(motion);AABB bounds=target.getBoundingBox().move(motion);
        double distance=position.distanceTo(explosion)/12;if(distance>1)return 0;
        // Meteor predicts with the prospective obsidian present and weak terrain removed. Never writes the world.
        BlockGetter view=new BlockGetter() {
            public BlockEntity getBlockEntity(BlockPos pos){return null;}
            public BlockState getBlockState(BlockPos pos) {
                if(pos.equals(support))return Blocks.OBSIDIAN.defaultBlockState();
                if(!boss.level().hasChunkAt(pos))return Blocks.BEDROCK.defaultBlockState();
                var state=boss.level().getBlockState(pos);
                return state.getBlock().getExplosionResistance()<600?Blocks.AIR.defaultBlockState():state;
            }
            public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
            public int getHeight(){return boss.level().getHeight();}
            public int getMinBuildHeight(){return boss.level().getMinBuildHeight();}
        };
        double dx=1/(bounds.getXsize()*2+1),dy=1/(bounds.getYsize()*2+1),dz=1/(bounds.getZsize()*2+1);
        double ox=(1-Math.floor(1/dx)*dx)/2,oz=(1-Math.floor(1/dz)*dz)/2;int clear=0,total=0;
        for(double x=0;x<=1;x+=dx)for(double y=0;y<=1;y+=dy)for(double z=0;z<=1;z+=dz) {
            Vec3 from=new Vec3(Mth.lerp(x,bounds.minX,bounds.maxX)+ox,Mth.lerp(y,bounds.minY,bounds.maxY),Mth.lerp(z,bounds.minZ,bounds.maxZ)+oz);
            if(view.clip(new ClipContext(from,explosion,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.MISS)clear++;
            total++;
        }
        double impact=(1-distance)*clear/total;float amount=(int)((impact*impact+impact)*42+1);
        if(target instanceof Player)amount=switch(boss.level().getDifficulty()){case PEACEFUL->0;case EASY->Math.min(amount/2+1,amount);case HARD->amount*1.5f;default->amount;};
        amount=CombatRules.getDamageAfterAbsorb(amount,target.getArmorValue(),(float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance=target.getEffect(MobEffects.DAMAGE_RESISTANCE);if(resistance!=null)amount*=Math.max(0,1-.2f*(resistance.getAmplifier()+1));
        return CombatRules.getDamageAfterMagicAbsorb(amount,EnchantmentHelper.getDamageProtection(target.getArmorSlots(),boss.damageSources().explosion(null,boss)));
    }
}
