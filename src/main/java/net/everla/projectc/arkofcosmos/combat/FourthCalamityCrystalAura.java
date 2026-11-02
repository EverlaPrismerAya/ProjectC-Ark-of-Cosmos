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
    public enum TickResult { PROGRESSED, WAITING, REPOSITION, NO_OPPORTUNITY }
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
    private BlockPos coverColumn;
    private Vec3 planTarget,planBoss;
    private int planTargetId=-1,stalled;
    private final Map<UUID,Integer> deferred=new HashMap<>();
    private int placeAt,breakAt,switchAt,supportUntil;
    public FourthCalamityCrystalAura(FourthCalamityEntity boss){this.boss=boss;}
    public void clear(){targets.clear();attempts.clear();waiting.clear();attacks.clear();deferred.clear();supportBase=coverColumn=null;planTarget=planBoss=null;planTargetId=-1;stalled=0;placeAt=breakAt=switchAt=supportUntil=0;}
    boolean followTarget(LivingEntity primary) {
        boolean changed=planTarget!=null&&(primary.getId()!=planTargetId||primary.position().distanceToSqr(planTarget)>16
            ||boss.position().distanceToSqr(planBoss)>36||boss.distanceToSqr(primary)>settings.targetRange*settings.targetRange);
        if(changed)abandonPlan("TARGET_OR_BOSS_MOVED");
        if(planTarget==null){planTarget=primary.position();planBoss=boss.position();planTargetId=primary.getId();}
        return changed;
    }
    void abandonPlan(String reason) {
        EndCrystal old=boss.actions.pendingCrystal();
        if(old!=null)deferred.put(old.getUUID(),boss.tickCount+40);
        boss.actions.forgetPendingCrystal();supportBase=null;planTarget=planBoss=null;stalled=0;
        boss.actions.diagnostic("AURA_PLAN",null,FourthCalamityActions.CrystalResult.BLOCKED,reason);
    }
    public TickResult tick(LivingEntity primary) {
        followTarget(primary);
        int tick=boss.tickCount;
        deferred.entrySet().removeIf(entry->tick>=entry.getValue());
        while(!attacks.isEmpty()&&tick-attacks.peekFirst()>=20)attacks.removeFirst();
        var nearby=boss.level().getEntitiesOfClass(EndCrystal.class,boss.getBoundingBox().inflate(FourthCalamityActions.REACH));
        Set<UUID> loaded=new HashSet<>();for(var crystal:nearby)loaded.add(crystal.getUUID());
        attempts.keySet().retainAll(loaded);waiting.keySet().retainAll(loaded);
        findTargets(primary);
        if(targets.isEmpty()||boss.getHealth()+boss.getAbsorptionAmount()<=settings.pauseHealth)return TickResult.NO_OPPORTUNITY;
        EndCrystal pending=boss.actions.pendingCrystal(),best=null;double damage=0;
        if(pending!=null&&(!boss.actions.canAttack(pending)||damage(pending.position(),pending.blockPosition().below(),true,false)<=0)) {
            abandonPlan("CRYSTAL_NO_LONGER_USEFUL");pending=null;
        }
        if(pending!=null) {
            best=pending;
        } else for(var crystal:nearby){double value=breakDamage(crystal);if(value>damage){damage=value;best=crystal;}}
        boolean progressed=false;
        if(best!=null&&settings.breakCrystals) {
            boss.stopUsingItem();
            var result=attack(best);
            progressed=result==FourthCalamityActions.CrystalResult.DETONATED;
            if(best.isAlive())return result.progressed()?TickResult.PROGRESSED:result==FourthCalamityActions.CrystalResult.WAITING?TickResult.WAITING:TickResult.REPOSITION;
        }
        if(!boss.isAlive())return progressed?TickResult.PROGRESSED:TickResult.NO_OPPORTUNITY;
        if(settings.pauseOnUse&&boss.isUsingItem())return progressed?TickResult.PROGRESSED:TickResult.NO_OPPORTUNITY;
        if(!settings.place)return progressed?TickResult.PROGRESSED:TickResult.NO_OPPORTUNITY;
        if(tick<placeAt)return progressed?TickResult.PROGRESSED:TickResult.WAITING;
        if(boss.position().subtract(primary.position()).horizontalDistance()<3)return progressed?TickResult.PROGRESSED:TickResult.REPOSITION;
        BlockPos selected=chooseBase(primary);
        if(selected==null) {
            return progressed?TickResult.PROGRESSED:TickResult.NO_OPPORTUNITY;
        }
        if(!rotate(Vec3.atCenterOf(selected).add(0,.5,0)))return TickResult.WAITING;
        // Validate and build cover before committing a real crystal; never leave it behind just to retreat.
        if(foundation(boss.level().getBlockState(selected))) {
            var cover=prepareCover(Vec3.atBottomCenterOf(selected.above()));
            if(cover!=null)return cover.progressed()?TickResult.PROGRESSED:cover==FourthCalamityActions.CrystalResult.WAITING?TickResult.WAITING:TickResult.REPOSITION;
        }
        var result=boss.actions.crystal(selected);
        if(result==FourthCalamityActions.CrystalResult.BASE_PLACED) {
            supportBase=selected;supportUntil=tick+20;placeAt=tick+Math.max(1,settings.supportDelay);
        } else if(result==FourthCalamityActions.CrystalResult.CRYSTAL_PLACED) {
            supportBase=null;placeAt=tick+settings.placeDelay;
            EndCrystal crystal=boss.actions.pendingCrystal();
            if(settings.fastBreak&&crystal!=null)attack(crystal);
        } else if(result==FourthCalamityActions.CrystalResult.BLOCKED)supportBase=null;
        if(progressed||result.progressed())return TickResult.PROGRESSED;
        return result==FourthCalamityActions.CrystalResult.WAITING?TickResult.WAITING:TickResult.REPOSITION;
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
        float self=forecast(boss,at,base,false);
        if(self>settings.maxSelfDamage||settings.antiSuicide&&self>=boss.getHealth()+boss.getAbsorptionAmount())return 0;
        double total=0;
        LivingEntity nearest=fast?targets.stream().min(Comparator.comparingDouble(boss::distanceToSqr)).orElse(null):null;
        for(var target:targets) {
            if(fast&&target!=nearest||breaking&&settings.smartDelay&&target.hurtTime>0)continue;
            total+=forecast(target,at,base,true);
        }
        return total>=Math.min(settings.minDamage,facePlace()?1.5:settings.minDamage)?total:0;
    }
    private double breakDamage(EndCrystal crystal) {
        if(deferred.containsKey(crystal.getUUID())||!crystal.isAlive()||settings.onlyOwn&&!CalamityAttackGuard.owned(crystal,boss)||crystal.tickCount<settings.minimumAge
            ||boss.getEyePosition().distanceToSqr(crystal.getBoundingBox().getCenter())>settings.breakRange*settings.breakRange
            ||!boss.actions.canAttack(crystal))return 0;
        return damage(crystal.position(),crystal.blockPosition().below(),true,false);
    }
    FourthCalamityActions.CrystalResult attack(EndCrystal crystal) {
        if(deferred.containsKey(crystal.getUUID()))return FourthCalamityActions.CrystalResult.BLOCKED;
        var result=attackStep(crystal);
        if(result.progressed())stalled=0;
        else if(result==FourthCalamityActions.CrystalResult.BLOCKED&&++stalled>=FourthCalamityAI.CRYSTAL_STALL_TICKS)abandonPlan("CRYSTAL_STALLED");
        return result;
    }
    private FourthCalamityActions.CrystalResult attackStep(EndCrystal crystal) {
        var wait=FourthCalamityActions.CrystalResult.WAITING;
        int tick=boss.tickCount;
        if(!boss.actions.canAttack(crystal)||boss.getEyePosition().distanceToSqr(crystal.getBoundingBox().getCenter())>settings.breakRange*settings.breakRange)
            return FourthCalamityActions.CrystalResult.BLOCKED;
        if(tick<breakAt||tick<switchAt||crystal.tickCount<settings.minimumAge
            ||tick<waiting.getOrDefault(crystal.getUUID(),0)||attacks.size()>=settings.attackFrequency
            ||!attacks.isEmpty()&&attacks.peekLast()==tick)return wait;
        if(attempts.getOrDefault(crystal.getUUID(),0)>=Math.max(1,settings.breakAttempts)) {
            attempts.remove(crystal.getUUID());waiting.put(crystal.getUUID(),tick+20);
            return FourthCalamityActions.CrystalResult.BLOCKED;
        }
        var weakness=boss.getEffect(MobEffects.WEAKNESS);var strength=boss.getEffect(MobEffects.DAMAGE_BOOST);
        if(settings.antiWeakness&&weakness!=null&&(strength==null||strength.getAmplifier()<=weakness.getAmplifier())&&!boss.getMainHandItem().is(Items.NETHERITE_SWORD)) {
            boss.actions.hold(Items.NETHERITE_SWORD);switchAt=tick+Math.max(1,settings.switchDelay);return wait;
        }
        if(!rotate(crystal.getBoundingBox().getCenter()))return wait;
        var cover=prepareCover(crystal.position());
        if(cover!=null)return cover;
        var result=boss.actions.detonateCrystal(crystal);
        if(result==wait)return result;
        attacks.addLast(tick);attempts.merge(crystal.getUUID(),1,Integer::sum);
        waiting.put(crystal.getUUID(),tick+10);breakAt=tick+settings.breakDelay;
        if(result==FourthCalamityActions.CrystalResult.DETONATED){attempts.remove(crystal.getUUID());waiting.remove(crystal.getUUID());}
        return result;
    }
    private FourthCalamityActions.CrystalResult prepareCover(Vec3 explosion) {
        refreshCover();
        double current=exposure(boss,explosion,boss.getBoundingBox(),null,null,false);
        if(protectedFromBlast(explosion,current))return null;
        BlockPos best=null;double bestExposure=current;boolean bestDirect=false;
        BlockPos feet=boss.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(feet.offset(-2,0,-2),feet.offset(2,1,2))) {
            if(new AABB(pos).intersects(new AABB(explosion.x-1,explosion.y,explosion.z-1,explosion.x+1,explosion.y+2,explosion.z+1)))continue;
            if(coverColumn!=null&&(pos.getX()!=coverColumn.getX()||pos.getZ()!=coverColumn.getZ()
                ||pos.getY()<coverColumn.getY()||pos.getY()>coverColumn.getY()+1))continue;
            if(!boss.actions.canPlaceCrystalCover(pos)||!leavesExits(pos))continue;
            double value=exposure(boss,explosion,boss.getBoundingBox(),null,pos,false);
            boolean direct=value<current;
            // A raised crystal can require a two-block wall: the lower block is its real support.
            if(boss.level().getBlockState(pos.above()).canBeReplaced()
                &&boss.level().isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(),pos.above(),net.minecraft.world.phys.shapes.CollisionContext.empty()))
                value=Math.min(value,exposure(boss,explosion,boss.getBoundingBox(),null,pos,false,true));
            if(value<bestExposure||best!=null&&value==bestExposure&&direct&&!bestDirect){bestExposure=value;best=pos.immutable();bestDirect=direct;}
        }
        if(best!=null) {
            var result=boss.actions.crystalCover(best);
            if(result==FourthCalamityActions.CrystalResult.BASE_PLACED&&coverColumn==null)coverColumn=best;
            return result;
        }
        boss.actions.diagnostic("AURA_COVER",null,FourthCalamityActions.CrystalResult.BLOCKED,"NO_LEGAL_BLAST_COVER");
        return FourthCalamityActions.CrystalResult.BLOCKED;
    }
    private void refreshCover() {
        if(coverColumn!=null&&(boss.position().distanceToSqr(Vec3.atBottomCenterOf(coverColumn))>36
            ||!boss.level().getBlockState(coverColumn).is(Blocks.OBSIDIAN)))coverColumn=null;
    }
    private boolean protectedFromBlast(Vec3 explosion,double exposure) {
        // Require real body cover, not perfect occlusion of every edge sample: that can deadlock at a wall corner.
        return exposure==0||exposure<1&&boss.level().clip(new ClipContext(boss.getBoundingBox().getCenter(),explosion,
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.BLOCK;
    }
    private boolean leavesExits(BlockPos cover) {
        AABB wall=new AABB(cover).expandTowards(0,1,0);int exits=0;
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            AABB corridor=boss.getBoundingBox().expandTowards(direction.getStepX()*1.2,0,direction.getStepZ()*1.2);
            if(!corridor.intersects(wall)&&boss.level().noCollision(boss,corridor))exits++;
        }
        return exits>=2;
    }
    double coverExposure(Vec3 explosion,Vec3 feet) {
        return exposure(boss,explosion,boss.getBoundingBox().move(feet.subtract(boss.position())),null,null,false);
    }
    private boolean validBase(BlockPos base) {
        return baseRejection(base)==null;
    }
    private String baseRejection(BlockPos base) {
        String reason=boss.actions.crystalBaseRejection(base);
        if(reason!=null)return reason;
        if(boss.getEyePosition().distanceToSqr(Vec3.atBottomCenterOf(base.above()))>settings.placeRange*settings.placeRange)return "OUT_OF_AURA_PLACE_RANGE";
        if(settings.placement112&&!boss.level().isEmptyBlock(base.above(2)))return "LEGACY_SPACE_OCCUPIED";
        return foundation(boss.level().getBlockState(base))||settings.support!=Support.DISABLED?null:"SUPPORT_DISABLED";
    }
    private BlockPos chooseBase(LivingEntity primary) {
        refreshCover();
        if(supportBase!=null&&boss.tickCount<=supportUntil&&validBase(supportBase)
            &&nearTarget(supportBase,primary)
            &&damage(Vec3.atBottomCenterOf(supportBase.above()),supportBase,false,false)>0)return supportBase;
        supportBase=null;
        int radius=(int)Math.ceil(Math.min(FourthCalamityActions.REACH,settings.placeRange));
        BlockPos center=boss.blockPosition(),best=null;double bestDamage=0;boolean bestExisting=false;
        Map<String,Integer> rejected=new TreeMap<>();
        for(var pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))) {
            String reason=baseRejection(pos);
            if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
            if(!nearTarget(pos,primary)){rejected.merge("TOO_FAR_FROM_TARGET",1,Integer::sum);continue;}
            boolean existing=foundation(boss.level().getBlockState(pos));
            // Once a wall exists, place on its protected side instead of creating crystals we cannot break.
            Vec3 explosion=Vec3.atBottomCenterOf(pos.above());
            if(coverColumn!=null&&!protectedFromBlast(explosion,exposure(boss,explosion,boss.getBoundingBox(),null,null,false))) {
                rejected.merge("OUTSIDE_EXISTING_COVER",1,Integer::sum);continue;
            }
            double value=damage(Vec3.atBottomCenterOf(pos.above()),pos,false,!existing&&settings.support==Support.FAST);
            if(value<=0)rejected.merge("DAMAGE_BELOW_THRESHOLD",1,Integer::sum);
            if(value>0&&(value>bestDamage||value==bestDamage&&existing&&!bestExisting)) {
                best=pos.immutable();bestDamage=value;bestExisting=existing;
            }
        }
        if(best==null)boss.actions.diagnostic("AURA_SELECT",null,FourthCalamityActions.CrystalResult.BLOCKED,"NO_LEGAL_CANDIDATE "+rejected);
        return best;
    }
    private boolean nearTarget(BlockPos base,LivingEntity target) {
        Vec3 predicted=target.position().add(settings.predictMovement?target.getDeltaMovement():Vec3.ZERO);
        return Vec3.atBottomCenterOf(base.above()).subtract(predicted).horizontalDistance()<=3;
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
    private float forecast(LivingEntity target,Vec3 explosion,BlockPos support,boolean targetRanking) {
        Vec3 motion=settings.predictMovement?target.getDeltaMovement():Vec3.ZERO;
        if(!boss.level().noCollision(target,target.getBoundingBox().move(motion)))motion=Vec3.ZERO;
        Vec3 position=target.position().add(motion);AABB bounds=target.getBoundingBox().move(motion);
        double distance=position.distanceTo(explosion)/12;if(distance>1)return 0;
        double impact=(1-distance)*exposure(target,explosion,bounds,support,null,true);float amount=(int)((impact*impact+impact)*42+1);
        if(target instanceof Player)amount=switch(boss.level().getDifficulty()){case PEACEFUL->0;case EASY->Math.min(amount/2+1,amount);case HARD->amount*1.5f;default->amount;};
        // Rank enemies by raw blast potential; native explosions still apply all mitigation.
        if(targetRanking)return amount;
        amount=CombatRules.getDamageAfterAbsorb(amount,target.getArmorValue(),(float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance=target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if(resistance!=null)amount*=Math.max(0,1-.2f*(resistance.getAmplifier()+1));
        return CombatRules.getDamageAfterMagicAbsorb(amount,EnchantmentHelper.getDamageProtection(target.getArmorSlots(),boss.damageSources().explosion(null,boss)));
    }
    private double exposure(LivingEntity target,Vec3 explosion,AABB bounds,BlockPos support,BlockPos cover,boolean predictTerrain) {
        return exposure(target,explosion,bounds,support,cover,predictTerrain,false);
    }
    private double exposure(LivingEntity target,Vec3 explosion,AABB bounds,BlockPos support,BlockPos cover,boolean predictTerrain,boolean coverStack) {
        // Scoring predicts terrain; cover validation uses real blocks and the boss's current body.
        BlockGetter view=new BlockGetter() {
            public BlockEntity getBlockEntity(BlockPos pos){return null;}
            public BlockState getBlockState(BlockPos pos) {
                if(pos.equals(cover)||coverStack&&cover!=null&&pos.equals(cover.above())||predictTerrain&&pos.equals(support))return Blocks.OBSIDIAN.defaultBlockState();
                if(!boss.level().hasChunkAt(pos))return Blocks.BEDROCK.defaultBlockState();
                var state=boss.level().getBlockState(pos);
                return predictTerrain&&state.getBlock().getExplosionResistance()<600?Blocks.AIR.defaultBlockState():state;
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
        return (double)clear/total;
    }
}
