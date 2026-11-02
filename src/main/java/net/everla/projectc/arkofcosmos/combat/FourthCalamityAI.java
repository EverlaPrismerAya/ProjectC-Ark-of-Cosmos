package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;

/** One decision tick per server tick; all attacks are delegated to real items and entities. */
public final class FourthCalamityAI {
    public static final int RECOVER_TICKS=20,TAIL_INTERVAL=2;
    public static final double RETREAT_TRIGGER=4.5,RETREAT_DISTANCE=7;
    public static final int WINDUP_TICKS=20;
    public static final int CRYSTAL_STALL_TICKS=8,CRYSTAL_RETRY_TICKS=20;
    public static final double AIR_CLEARANCE=1.5;
    public static final double MACE_HEIGHT=12,INTERRUPT_DAMAGE=20;
    private enum Ground { ARROW,PEARL,HIT,CHECK,CRYSTALS,RETREAT,PREPARE,SHIELD,PLACE,CHARGE,DETONATE,RECOVER }
    private enum Air { GROUND,APPROACH,READY,CHARGE,STRAFE,CLIMB,WINDUP,DROP,FALL,LAND,WIND_LAND }
    private final FourthCalamityEntity boss;
    private final FourthCalamityCrystalAura crystalAura;
    private Ground ground=Ground.ARROW;
    private Air air=Air.GROUND;
    public static final double MACE_RANGE=6,GROUND_CHARGE_RANGE=24,SETUP_DISTANCE=21,SETUP_HEIGHT=5;
    private Vec3 chargePoint,chargeDirection,landing;
    private int shotsLeft,spearSequence,chargeTarget=-1,windWait;
    private boolean chargeHit,groundCharge,windFired;
    private int age,airAge,airborne,grounded,crystals,remaining,meleeDelay;
    private int crystalStall,crystalRetryAt;
    private boolean aerial,hadTarget,pearlOpening,swordAttempted;
    private float interrupt;
    private BlockPos base,explosive,shield;
    private Vec3 lockedLanding;
    private Vec3 placementPosition;
    private Vec3 previousTargetPosition;
    private int previousTargetId=-1;
    private boolean spacing;
    private boolean escaping;
    private boolean anchor;
    private int crystalLaunchTarget=-1,crystalLaunchTick;
    private double crystalLaunchY,crystalLaunchVelocity;
    private boolean crystalLifted,shortCrystalCharge;
    private boolean tailGround;
    private int fluidTicks,riptideCharge,riptideCooldown;
    private boolean fluidCombat;
    public void crystalBlast(LivingEntity target) {
        if(target!=boss.getTarget()||air!=Air.GROUND)return;
        if(crystalLaunchTarget==target.getId()&&boss.tickCount-crystalLaunchTick<=20)return;
        crystalLaunchTarget=target.getId();crystalLaunchTick=boss.tickCount;
        crystalLaunchY=target.getY();crystalLaunchVelocity=target.getDeltaMovement().y;crystalLifted=false;
    }
    private void followCrystalLaunch(LivingEntity target) {
        if(crystalLaunchTarget!=target.getId()||boss.tickCount-crystalLaunchTick>20) {crystalLaunchTarget=-1;return;}
        if(boss.tickCount-crystalLaunchTick<=2&&target.getDeltaMovement().y>.15
            &&target.getDeltaMovement().y>crystalLaunchVelocity+.05)crystalLifted=true;
        if(crystalLifted&&!target.onGround()&&target.getY()-crystalLaunchY>=3&&target.getDeltaMovement().y>0
            &&air==Air.GROUND&&!explosivePending()) {
            crystalLaunchTarget=-1;shortCrystalCharge=true;groundCharge=true;pearlOpening=false;base=null;
            crystalAura.abandonPlan("CRYSTAL_LAUNCH_CHARGE");enterAir(Air.READY);
            boss.setGliding(true);boss.setDeltaMovement(boss.getDeltaMovement().add(0,.5,0));
        }
    }
    public FourthCalamityAI(FourthCalamityEntity boss) { this.boss=boss;this.crystalAura=new FourthCalamityCrystalAura(boss); }
    private boolean valid(LivingEntity target) {
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==boss.level()&&!boss.isAlliedTo(target)
            &&boss.distanceToSqr(target)<=128*128&&!(target instanceof FourthCalamityEntity)
            &&(!(target instanceof Player p)||!p.isCreative()&&!p.isSpectator());
    }
    public void tick() {
        LivingEntity target=boss.getTarget();
        if(!valid(target)) {
            target=boss.level().getNearestPlayer(boss.getX(),boss.getY(),boss.getZ(),128,p->p instanceof LivingEntity living&&valid(living));
            boss.setTarget(target);
        }
        if(!valid(target)) { if(hadTarget){stop();boss.actions.cleanup();}hadTarget=false;return; }
        hadTarget=true;if(meleeDelay>0)meleeDelay--;
        if(fluidAttack(target))return;
        boolean approaching=target instanceof Player&&previousTargetId==target.getId()&&previousTargetPosition!=null
            &&target.position().subtract(previousTargetPosition).multiply(1,0,1)
                .dot(boss.position().subtract(target.position()).multiply(1,0,1).normalize())>.02;
        escaping=previousTargetId==target.getId()&&previousTargetPosition!=null
            &&target.position().subtract(previousTargetPosition).multiply(1,0,1)
                .dot(target.position().subtract(boss.position()).multiply(1,0,1).normalize())>.02;
        if(previousTargetId!=target.getId())spacing=false;
        previousTargetId=target.getId();previousTargetPosition=target.position();
        if(boss.phase()==2&&crystalAura.followTarget(target)) {
            base=null;
            if(ground==Ground.CRYSTALS)recover();
        }
        if(boss.actions.consumePearlArrival()&&boss.phase()==2&&air==Air.GROUND&&!explosivePending()) {
            crystalAura.abandonPlan("PEARL_ARRIVAL");base=null;
            pearlOpening=true;transition(Ground.ARROW);boss.getNavigation().stop();boss.stopUsingItem();
        }
        if(boss.phase()!=3)followCrystalLaunch(target);
        if((boss.phase()==3||!explosivePending())&&!shortCrystalCharge)updateMode(target);
        if(boss.phase()!=3)tailGround=false;
        if(boss.phase()==3&&air==Air.GROUND) {
            if(!tailGround) {
                crystalAura.clear();boss.actions.cleanup();boss.stopUsingItem();
                base=explosive=shield=null;placementPosition=null;pearlOpening=spacing=false;
                crystalLaunchTarget=-1;transition(Ground.HIT);meleeDelay=0;tailGround=true;
            }
            if(boss.tickCount%TAIL_INTERVAL==0)teleport(target);
            boss.setGliding(false);boss.setNoGravity(false);aim(target.getEyePosition(),30);
            boss.actions.hold(Items.NETHERITE_SWORD);chase(target);
            if(meleeDelay==0&&boss.actions.melee(target))meleeDelay=13;
            return;
        }
        if(air==Air.GROUND&&!explosivePending()&&boss.actions.chasePearlFlying()) {
            crystalAura.abandonPlan("PEARL_CHASE");base=null;chase(target);return;
        }
        if(air==Air.GROUND&&!explosivePending()&&boss.actions.pendingCrystal()==null) {
            if(boss.actions.retreatPearlFlying()){boss.getNavigation().stop();return;}
            if(approaching&&boss.distanceToSqr(target)<RETREAT_TRIGGER*RETREAT_TRIGGER)spacing=true;
            if(spacing&&boss.distanceToSqr(target)<6*6) {
                if(!retreat(target)) {
                    Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
                    moveToPoint(target.position().add(away.scale(RETREAT_DISTANCE)));boss.actions.arrow(target,false);
                }
                return;
            }
            spacing=false;
        }
        if(air!=Air.GROUND)flight(target);
        else ground(target);
    }
    private boolean explosivePending() {
        return ground==Ground.SHIELD||ground==Ground.CHARGE||ground==Ground.DETONATE;
    }
    private boolean fluidAttack(LivingEntity target) {
        boolean immersed=boss.isInWaterOrBubble()||boss.isInLava()
            ||!boss.level().getFluidState(boss.blockPosition()).isEmpty();
        fluidTicks=immersed?fluidTicks+1:0;
        if(!fluidCombat&&fluidTicks<20)return false;
        if(!fluidCombat) {
            // Fluid recovery takes ownership even during a crystal combo or a failed landing.
            stop();boss.actions.cleanup();fluidCombat=true;
        }
        if(boss.isAutoSpinAttack())return true;
        if(!immersed) {
            fluidCombat=false;riptideCharge=riptideCooldown=0;boss.stopUsingItem();
            recover();return false;
        }
        boss.getNavigation().stop();boss.setGliding(false);boss.setNoGravity(false);
        boss.setSprinting(false);boss.setJumping(false);
        boss.actions.hold(Items.TRIDENT);
        if(net.minecraft.world.item.enchantment.EnchantmentHelper.getRiptide(boss.getMainHandItem())==0)
            boss.getMainHandItem().enchant(net.minecraft.world.item.enchantment.Enchantments.RIPTIDE,3);
        if(riptideCooldown>0){riptideCooldown--;return true;}
        Vec3 velocity=target instanceof net.minecraft.server.level.ServerPlayer?SpearCombat.motion(target):target.getDeltaMovement();
        Vec3 offset=target.getBoundingBox().getCenter().subtract(boss.getEyePosition());
        double intercept=interceptTime(offset,velocity,3);
        double lead=intercept>=0?Math.min(20,intercept):Math.min(20,offset.length()/3);
        Vec3 point=target.getBoundingBox().getCenter().add(velocity.scale(lead));
        aim(point,30);
        if(!boss.isUsingItem())boss.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        if(++riptideCharge<10)return true;
        Vec3 direction=point.subtract(boss.getEyePosition()).normalize();
        if(direction.lengthSqr()<.01)direction=new Vec3(0,1,0);
        aim(boss.getEyePosition().add(direction),180);boss.stopUsingItem();
        // Vanilla Riptide III adds a 3-block/tick impulse and spins for 20 ticks.
        boss.push(direction.x*3,direction.y*3,direction.z*3);
        boss.setRiptideTicks(20);boss.resetFallDistance();
        if(boss.onGround())boss.move(MoverType.SELF,new Vec3(0,1.1999999,0));
        boss.playSound(net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_3,1,1);
        VanillaCombat.syncMotion(boss);riptideCharge=0;riptideCooldown=20;
        return true;
    }
    private boolean retreat(LivingEntity target) {
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}) {
            Vec3 direction=new Vec3(away.x*Math.cos(angle)-away.z*Math.sin(angle),0,away.x*Math.sin(angle)+away.z*Math.cos(angle));
            Vec3 destination=target.position().add(direction.scale(RETREAT_DISTANCE));
            destination=new Vec3(destination.x,boss.getY(),destination.z);
            if(!safeLanding(destination)||!boss.actions.retreatPearl(destination))continue;
            recover();return true;
        }
        return false;
    }
    private void updateMode(LivingEntity target) {
        Vec3 feet=target.position();
        boolean nearGround=target.onGround()||boss.level().clip(new net.minecraft.world.level.ClipContext(
            feet,feet.add(0,-AIR_CLEARANCE,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.BLOCK;
        boolean gliding=!nearGround&&target.isFallFlying();
        boolean flying=!nearGround&&target instanceof Player p&&p.getAbilities().flying;
        boolean hovering=!nearGround&&!target.isInWater()&&Math.abs(target.getDeltaMovement().y)<.12;
        airborne=gliding?20:flying||hovering?airborne+1:0;grounded=nearGround?grounded+1:0;
        boolean mace=air==Air.CLIMB||air==Air.WINDUP||air==Air.DROP||air==Air.FALL;
        if(!aerial&&airborne>=20&&!mace) {
            aerial=true;groundCharge=false;pearlOpening=false;landing=null;enterAir(Air.APPROACH);ground=Ground.ARROW;age=0;
        } else if(aerial&&grounded>=10) {
            aerial=false;
            double distance=target.position().subtract(boss.position()).horizontalDistance();
            groundCharge=distance>GROUND_CHARGE_RANGE;
            enterAir(distance<=MACE_RANGE?Air.CLIMB:groundCharge?Air.READY:Air.LAND);
        }
    }
    private void aim(Vec3 point,float turn) {
        Vec3 delta=point.subtract(boss.getEyePosition());
        float yaw=(float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90;
        float pitch=(float)(-Mth.atan2(delta.y,delta.horizontalDistance())*180/Math.PI);
        boss.setYRot(Mth.approachDegrees(boss.getYRot(),yaw,turn));boss.setXRot(Mth.approachDegrees(boss.getXRot(),pitch,turn));
        boss.setYHeadRot(boss.getYRot());boss.setYBodyRot(boss.getYRot());
    }
    private void chase(LivingEntity target) {
        if(boss.phase()==2&&!pearlOpening) {
            if(boss.position().subtract(target.position()).horizontalDistance()>4.5)positionForCrystals(target);
            else {boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);}
            return;
        }
        boss.getNavigation().moveTo(target,1.2);boss.setSprinting(true);
        if(boss.horizontalCollision&&boss.onGround())boss.setJumping(true);else boss.setJumping(false);
    }
    private void transition(Ground next) { ground=next;age=0;if(next==Ground.CRYSTALS)crystalStall=0; }
    private void recover() { swordAttempted=false;pearlOpening=false;transition(Ground.RECOVER);boss.getNavigation().stop(); }
    private void ground(LivingEntity target) {
        boss.setGliding(false);boss.setNoGravity(false);aim(target.getEyePosition(),30);age++;
        if(boss.actions.pendingCrystal()==null&&(boss.distanceToSqr(target)>16*16||escaping&&boss.distanceToSqr(target)>6*6)
            &&boss.actions.pearl(target)) {
            crystalAura.abandonPlan("PEARL_CHASE");base=null;chase(target);return;
        }
        // A target already above the floor needs no second launch. Never restart an active combo.
        if(boss.phase()==1&&boss.tickCount>=crystalRetryAt&&!target.onGround()&&!target.isInWater()
            &&boss.distanceToSqr(target)<=FourthCalamityActions.REACH*FourthCalamityActions.REACH
            &&target.getY()>boss.getY()+.35&&target.getY()<=boss.getY()+3
            &&(ground==Ground.ARROW||ground==Ground.PEARL||ground==Ground.HIT||ground==Ground.CHECK)) {
            boss.stopUsingItem();base=boss.actions.bestBase(target,true);
            crystals=0;remaining=1+boss.getRandom().nextInt(3);transition(Ground.CRYSTALS);
        }
        if(boss.phase()==2&&!pearlOpening&&ground!=Ground.RETREAT&&ground!=Ground.PREPARE&&ground!=Ground.SHIELD&&ground!=Ground.PLACE&&ground!=Ground.CHARGE&&ground!=Ground.DETONATE) {
            var result=crystalAura.tick(target);
            if(result==FourthCalamityCrystalAura.TickResult.PROGRESSED||result==FourthCalamityCrystalAura.TickResult.WAITING) {
                boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);
            } else positionForCrystals(target);
            return;
        }
        switch(ground) {
            case ARROW -> { if(boss.actions.arrow(target,false))transition(pearlOpening?Ground.HIT:Ground.PEARL);else if(age>40)recover(); }
            case PEARL -> { boss.actions.pearl(target);chase(target);if(boss.distanceToSqr(target)<9)transition(Ground.HIT);else if(age>60)recover(); }
            case HIT -> {
                chase(target);boss.actions.hold(Items.NETHERITE_SWORD);
                if(meleeDelay==0&&boss.actions.melee(target)){swordAttempted=true;meleeDelay=13;transition(Ground.CHECK);}
                else if(age>30)recover();
            }
            case CHECK -> {
                if(swordAttempted&&boss.tickCount>=crystalRetryAt&&(!target.onGround()||target.getDeltaMovement().y>.05)) {
                    base=boss.actions.bestBase(target,boss.phase()==1);crystals=0;remaining=1+boss.getRandom().nextInt(3);
                    transition(Ground.CRYSTALS);
                    if(boss.phase()==2)pearlOpening=false; // Hand back to the same spacing/cover-before-placement pipeline.
                } else if(age>=8)transition(Ground.RETREAT);
            }
            case CRYSTALS -> {
                var result=crystalStep(target);
                if(result==FourthCalamityActions.CrystalResult.DETONATED)crystals++;
                if(result.progressed())crystalStall=0;
                else if(result==FourthCalamityActions.CrystalResult.WAITING)age--;
                else crystalStall++;
                if(crystals>=remaining)recover();
                else if(crystalStall>=CRYSTAL_STALL_TICKS) {
                    crystalRetryAt=boss.tickCount+CRYSTAL_RETRY_TICKS;base=null;
                    recover();
                } else if(age>30)recover();
            }
            case RETREAT -> {
                Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
                Vec3 destination=boss.position().add(away.scale(3));boss.getNavigation().moveTo(destination.x,destination.y,destination.z,1.25);
                if(age>=10) { boss.getNavigation().stop();transition(Ground.PREPARE); }
            }
            case PREPARE -> {
                Vec3 toward=target.position().subtract(boss.position()).multiply(1,0,1).normalize();
                shield=BlockPos.containing(boss.position().add(toward.scale(1.5)));
                anchor=!boss.level().dimensionType().respawnAnchorWorks();
                if(!anchor&&boss.level().dimensionType().bedWorks()){recover();break;}
                placementPosition=boss.position().add(toward).add(-toward.z*3.5,0,toward.x*3.5);
                explosive=shield.relative(net.minecraft.core.Direction.getNearest(toward.x,0,toward.z));transition(Ground.PLACE);
            }
            case PLACE -> {
                if(boss.actions.place(anchor?Items.RESPAWN_ANCHOR:Items.RED_BED,explosive)){boss.getNavigation().stop();transition(Ground.SHIELD);}
                else { moveToPoint(placementPosition);if(age>20)recover(); }
            }
            case SHIELD -> {
                // Cover is best effort; detonation needs reach, not an unobstructed interaction ray.
                if(!boss.level().getBlockState(shield).is(Blocks.OBSIDIAN)&&boss.actions.canPlace(shield))boss.actions.place(Items.OBSIDIAN,shield);
                transition(anchor?Ground.CHARGE:Ground.DETONATE);
            }
            case CHARGE -> {
                if(!boss.level().getBlockState(explosive).is(Blocks.RESPAWN_ANCHOR)){recover();break;}
                if(boss.level().getBlockState(explosive).getValue(RespawnAnchorBlock.CHARGE)>0)transition(Ground.DETONATE);
                else if(boss.actions.chargeAnchor(explosive)&&boss.level().getBlockState(explosive).is(Blocks.RESPAWN_ANCHOR)
                    &&boss.level().getBlockState(explosive).getValue(RespawnAnchorBlock.CHARGE)>0)transition(Ground.DETONATE);else if(age>12)recover();
            }
            case DETONATE -> {
                var state=boss.level().getBlockState(explosive);
                boolean usable=anchor?state.is(Blocks.RESPAWN_ANCHOR)&&state.getValue(RespawnAnchorBlock.CHARGE)>0&&!boss.level().dimensionType().respawnAnchorWorks():state.getBlock() instanceof BedBlock&&!boss.level().dimensionType().bedWorks();
                if(!usable||boss.actions.detonate(explosive)||age>12)recover();
            }
            case RECOVER -> { if(age>=RECOVER_TICKS)transition(Ground.ARROW); }
        }
    }
    private FourthCalamityActions.CrystalResult crystalStep(LivingEntity target) {
        var crystal=boss.actions.pendingCrystal();
        if(crystal==null&&boss.phase()!=1)crystal=boss.actions.bestCrystal(target);
        FourthCalamityActions.CrystalResult result;
        if(crystal!=null)result=boss.phase()==2?crystalAura.attack(crystal):boss.actions.detonateCrystal(crystal);
        else {
            if(!boss.actions.validCrystalBase(base)||boss.phase()==1&&!boss.actions.comboBase(base))base=boss.actions.bestBase(target,boss.phase()==1);
            result=base==null?FourthCalamityActions.CrystalResult.BLOCKED:boss.actions.crystal(base);
        }
        if(result==FourthCalamityActions.CrystalResult.BLOCKED) { base=null;positionForCrystals(target); }
        else { boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false); }
        return result;
    }
    private void positionForCrystals(LivingEntity target) {
        boss.actions.hold(Items.END_CRYSTAL);boss.setSprinting(false);boss.setJumping(false);
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        var pending=boss.actions.pendingCrystal();
        if(pending!=null) {
            if(boss.phase()==2) {
                Vec3 best=null;double exposure=crystalAura.coverExposure(pending.position(),boss.position()),distance=Double.POSITIVE_INFINITY;
                BlockPos center=pending.blockPosition();
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) {
                    Vec3 point=new Vec3(center.getX()+x+.5,boss.getY(),center.getZ()+z+.5);
                    if(point.add(0,boss.getEyeHeight(),0).distanceToSqr(pending.getBoundingBox().getCenter())>FourthCalamityActions.REACH*FourthCalamityActions.REACH||!safeLanding(point))continue;
                    double value=crystalAura.coverExposure(pending.position(),point),travel=boss.position().distanceToSqr(point);
                    if(value<exposure||best!=null&&value==exposure&&travel<distance){exposure=value;distance=travel;best=point;}
                }
                if(best!=null){moveToPoint(best);return;}
            }
            Vec3 fromCrystal=boss.position().subtract(pending.position()).multiply(1,0,1).normalize();
            if(fromCrystal.lengthSqr()<.01)fromCrystal=away;
            moveToPoint(pending.position().add(fromCrystal.scale(2.5)));return;
        }
        // Keep space for the real crystal entity instead of pathing into the target's occupied block.
        Vec3 destination=target.position().add(away.scale(3.5)).add(-away.z,0,away.x);
        moveToPoint(destination);
    }
    private void moveToPoint(Vec3 point) {
        // Reach the placement waypoint precisely rather than stopping a block short.
        boss.getNavigation().moveTo(boss.getNavigation().createPath(BlockPos.containing(point),0),1.1);
    }
    private void enterAir(Air next) {
        boss.stopUsingItem();boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);
        boss.setMaceWindup(false);boss.setNoGravity(false);air=next;airAge=0;
        if(next==Air.APPROACH||next==Air.READY) { chargeHit=false;chargeTarget=-1; }
        if(next==Air.LAND||next==Air.WIND_LAND) { landing=null;windFired=false;windWait=0; }
    }
    private double flightFactor() { return boss.phase()>=2?1.5:1; }
    private boolean blocked(Vec3 point) {
        return boss.level().clip(new net.minecraft.world.level.ClipContext(boss.getEyePosition(),point,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS;
    }
    private void propel(Vec3 point,boolean climb) {
        boss.getNavigation().stop();boss.setJumping(false);boss.setGliding(true);boss.setNoGravity(false);
        Vec3 next=point;
        Vec3 ahead=boss.getEyePosition().add(boss.getDeltaMovement().normalize().scale(4));
        if(blocked(ahead))next=boss.position().add(0,6,0);
        aim(next,12);
        double factor=flightFactor();
        Vec3 direction=next.subtract(boss.position()).normalize();
        Vec3 motion=boss.getDeltaMovement().scale(.92).add(direction.scale(.16*factor)).add(0,climb?.08:0,0);
        if(boss.onGround())motion=motion.add(0,.65,0);
        double cap=2.2*factor;if(motion.length()>cap)motion=motion.normalize().scale(cap);
        boss.setDeltaMovement(motion);VanillaCombat.syncMotion(boss);
    }
    private Vec3 setupPoint(LivingEntity target) {
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        return target.position().add(away.scale(SETUP_DISTANCE)).add(0,SETUP_HEIGHT,0);
    }
    private void brace() {
        boss.actions.hold(VanillaCombat.SPEARS.get("netherite").get());
        if(!boss.isUsingItem()) {
            boss.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);SpearCombat.start(boss);
            spearSequence=SpearCombat.sequence(boss);
        }
    }
    private Vec3 predictedChargePoint(LivingEntity target) {
        // Player flight is client-owned: server deltaMovement can lag behind received positions.
        Vec3 velocity=target instanceof net.minecraft.server.level.ServerPlayer?SpearCombat.motion(target):target.getDeltaMovement();
        Vec3 offset=target.getBoundingBox().getCenter().subtract(boss.getEyePosition());
        double speed=2.8*flightFactor();
        double travel=interceptTime(offset,velocity,speed);
        if(travel<0||travel>40)return null;
        return target.getBoundingBox().getCenter().add(velocity.scale(travel));
    }
    /** Earliest positive solution of |offset + velocity*t| = speed*t; -1 means no intercept. */
    private static double interceptTime(Vec3 offset,Vec3 velocity,double speed) {
        double a=velocity.lengthSqr()-speed*speed,b=2*offset.dot(velocity),c=offset.lengthSqr();
        if(c<1e-8)return 0;
        if(Math.abs(a)<1e-8)return b<0?-c/b:-1;
        double discriminant=b*b-4*a*c;
        if(discriminant<0)return -1;
        double root=Math.sqrt(discriminant),first=(-b-root)/(2*a),second=(-b+root)/(2*a);
        double earliest=Math.min(first>0?first:Double.POSITIVE_INFINITY,second>0?second:Double.POSITIVE_INFINITY);
        return Double.isFinite(earliest)?earliest:-1;
    }
    private Vec3 chargeMotion() {
        Vec3 desired=chargeDirection.scale(2.8*flightFactor()),input=desired;
        Vec3 look=boss.getLookAngle();
        double gravity=boss.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
        // travel() applies vanilla glide lift/drag after the AI. Invert that small change
        // so the actual motion, not just the pre-physics velocity, follows the locked line.
        for(int i=0;i<6;i++)input=input.add(desired.subtract(glideMotion(input,look,gravity)));
        return input;
    }
    private static Vec3 glideMotion(Vec3 input,Vec3 look,double gravity) {
        double horizontal=look.horizontalDistance(),speed=input.horizontalDistance(),lift=horizontal*horizontal;
        Vec3 motion=input.add(0,gravity*(-1+lift*.75),0);
        if(motion.y<0&&horizontal>0) {
            double up=-motion.y*.1*lift;
            motion=motion.add(look.x*up/horizontal,up,look.z*up/horizontal);
        }
        if(look.y>0&&horizontal>0) {
            double up=speed*look.y*.04;
            motion=motion.add(-look.x*up/horizontal,up*3.2,-look.z*up/horizontal);
        }
        if(horizontal>0)motion=motion.add((look.x/horizontal*speed-motion.x)*.1,0,(look.z/horizontal*speed-motion.z)*.1);
        return motion.multiply((double).99f,(double).98f,(double).99f);
    }
    /** Synchronous notification from the real spear damage path; other targets/uses cannot complete this pass. */
    public void spearHit(Entity target,int sequence) {
        if(air==Air.CHARGE&&target==boss.getTarget()&&target.getId()==chargeTarget&&sequence==spearSequence)chargeHit=true;
    }
    private void finishCharge() {
        boss.stopUsingItem();
        if(groundCharge)enterAir(Air.WIND_LAND);
        else if(chargeHit)enterAir(Air.APPROACH);
        else { enterAir(Air.STRAFE);shotsLeft=3+boss.getRandom().nextInt(8); }
    }
    private void flight(LivingEntity target) {
        airAge++;
        boss.setMaceWindup(air==Air.WINDUP);
        switch(air) {
            case APPROACH -> {
                Vec3 point=setupPoint(target);propel(point,boss.getY()<point.y-1);
                boss.actions.hold(VanillaCombat.SPEARS.get("netherite").get());
                double horizontal=target.position().subtract(boss.position()).horizontalDistance(),height=boss.getY()-target.getY();
                if(horizontal>=18&&horizontal<=24&&height>=4&&height<=6)enterAir(Air.READY);
            }
            case READY -> {
                boss.setGliding(true);boss.setNoGravity(false);brace();
                boss.setDeltaMovement(boss.getDeltaMovement().scale(.7).add(0,.04,0));
                Vec3 point=predictedChargePoint(target);
                if(point==null) { enterAir(shortCrystalCharge?Air.WIND_LAND:Air.APPROACH);break; }
                aim(point,30);
                if(airAge>=8&&boss.getTicksUsingItem()>=8) {
                    chargePoint=point;chargeDirection=point.subtract(boss.getEyePosition()).normalize();
                    // A 0.95 dot tolerance permits a ~6 block miss at the setup distance.
                    if(boss.getLookAngle().dot(chargeDirection)>.9995) {
                        chargeTarget=target.getId();chargeHit=false;air=Air.CHARGE;airAge=0;
                        boss.setDeltaMovement(chargeMotion());VanillaCombat.syncMotion(boss);
                    }
                }
            }
            case CHARGE -> {
                boss.setGliding(true);aim(boss.getEyePosition().add(chargeDirection.scale(20)),30);
                boss.setDeltaMovement(chargeMotion());VanillaCombat.syncMotion(boss);
                boolean passed=chargePoint.subtract(boss.getEyePosition()).dot(chargeDirection)<=-3;
                boolean touchdown=groundCharge&&boss.level().clip(new net.minecraft.world.level.ClipContext(boss.position(),
                    boss.position().add(boss.getDeltaMovement()).add(0,-2,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.BLOCK;
                if(chargeHit||passed||touchdown||boss.horizontalCollision||blocked(boss.getEyePosition().add(chargeDirection.scale(3)))||airAge>=40) {
                    finishCharge();if(air==Air.WIND_LAND)land();
                }
            }
            case STRAFE -> {
                Vec3 point=setupPoint(target);
                if(!boss.hasLineOfSight(target))point=point.add(Math.sin(airAge*.08)*8,3,Math.cos(airAge*.08)*8);
                propel(point,boss.getY()<target.getY()+4);
                {
                    // Shooting sets the facing; restore flight facing before vanilla glide physics runs.
                    float yaw=boss.getYRot(),pitch=boss.getXRot();
                    if(boss.actions.arrow(target,true))shotsLeft--;
                    boss.setYRot(yaw);boss.setXRot(pitch);boss.setYHeadRot(yaw);boss.setYBodyRot(yaw);
                }
                if(shotsLeft<=0)enterAir(Air.APPROACH);
            }
            case CLIMB -> {
                boss.actions.hold(VanillaCombat.MACE.get());propel(target.position().add(0,MACE_HEIGHT+3,0),true);
                if(boss.getY()>=target.getY()+MACE_HEIGHT) {
                    enterAir(Air.WINDUP);boss.setGliding(false);boss.setNoGravity(true);boss.setDeltaMovement(Vec3.ZERO);interrupt=0;boss.setMaceWindup(true);
                } else if(airAge>100)enterAir(Air.LAND);
            }
            case LAND, WIND_LAND -> land();
            case WINDUP -> {
                aim(target.position(),12);boss.setDeltaMovement(Vec3.ZERO);
                if(airAge>=WINDUP_TICKS) {
                    lockedLanding=target.position();boss.setNoGravity(false);boss.setGliding(false);boss.setMaceWindup(false);boss.fallDistance=0;
                    Vec3 delta=lockedLanding.subtract(boss.position());double duration=Math.max(5,Math.sqrt(Math.max(1,-delta.y)/.04));
                    Vec3 horizontal=new Vec3(delta.x,0,delta.z).scale(1/duration);
                    if(horizontal.length()>1.2)horizontal=horizontal.normalize().scale(1.2);
                    boss.setDeltaMovement(horizontal.add(0,-.1,0));air=Air.DROP;airAge=0;
                }
            }
            case DROP -> {
                aim(lockedLanding,12);
                if(boss.fallDistance>1.5&&boss.distanceToSqr(target)<9&&boss.hasLineOfSight(target)&&boss.actions.melee(target))enterAir(Air.FALL);
                else if(boss.onGround())finishLanding();
                else if(boss.isInWater()||airAge>100)enterAir(Air.FALL);
            }
            case FALL -> {
                boss.setGliding(false);boss.setNoGravity(false);
                if(boss.onGround())finishLanding();
                else if(boss.isInWater())enterAir(Air.LAND);
            }
            case GROUND -> { }
        }
    }
    boolean safeLanding(Vec3 point) {
        if(!safeTeleport(point))return false;
        AABB standing=new AABB(point.x-.3,point.y,point.z-.3,point.x+.3,point.y+1.8,point.z+.3);
        if(standing.maxY>boss.level().getMaxBuildHeight()||!boss.level().noCollision(boss,standing)||boss.level().containsAnyLiquid(standing))return false;
        BlockPos support=BlockPos.containing(point.add(0,-.05,0));
        var state=boss.level().getBlockState(support);
        return !state.getCollisionShape(boss.level(),support).isEmpty()&&state.getFluidState().isEmpty()
            &&!state.is(Blocks.MAGMA_BLOCK)&&!state.is(Blocks.CAMPFIRE)&&!state.is(Blocks.SOUL_CAMPFIRE)
            &&!state.is(Blocks.CACTUS)&&!boss.level().getBlockState(BlockPos.containing(point)).is(Blocks.FIRE);
    }
    private Vec3 findLanding() {
        Vec3 best=null;double score=Double.MAX_VALUE;
        BlockPos center=boss.blockPosition();
        for(int x=-8;x<=8;x+=2)for(int z=-8;z<=8;z+=2) {
            for(int y=Math.min(center.getY()+1,boss.level().getMaxBuildHeight()-3);y>=Math.max(center.getY()-64,boss.level().getMinBuildHeight());y--) {
                BlockPos support=new BlockPos(center.getX()+x,y,center.getZ()+z);
                if(!boss.level().hasChunkAt(support))break;
                var shape=boss.level().getBlockState(support).getCollisionShape(boss.level(),support);
                if(shape.isEmpty())continue;
                Vec3 point=new Vec3(support.getX()+.5,y+shape.max(net.minecraft.core.Direction.Axis.Y),support.getZ()+.5);
                if(safeLanding(point)&&!blocked(point.add(0,1,0))) {
                    double candidate=boss.position().distanceToSqr(point);
                    if(candidate<score){score=candidate;best=point;}
                    break;
                }
            }
        }
        return best;
    }
    private void land() {
        if(boss.onGround()){finishLanding();return;}
        if(landing==null||!safeLanding(landing)||airAge%20==0&&!windFired)landing=findLanding();
        if(landing==null) { propel(boss.position().add(boss.getLookAngle().multiply(1,0,1).scale(6)).add(0,1,0),false);return; }
        double height=boss.getY()-landing.y;
        Vec3 horizontal=landing.subtract(boss.position()).multiply(1,0,1);
        if(air==Air.WIND_LAND&&height<=4&&height>1&&horizontal.length()<.25&&!windFired) {
            if(boss.actions.windCharge(landing)) {
                windFired=true;windWait=0;boss.setGliding(false);
                // Brake the pass so its owner remains inside the real blast instead of outrunning it.
                boss.setDeltaMovement(horizontal.scale(.18).add(0,-.2,0));VanillaCombat.syncMotion(boss);
            }
        }
        if(windFired) {
            boss.setGliding(false);boss.setNoGravity(false);windWait++;
            // Only a real blast near the owner records impulse protection. Never manufacture it here.
            boolean buffered=ImpulseFall.hasProtection(boss);
            if(!buffered&&windWait>6) { boss.setGliding(true);windFired=false;landing=null; }
            return;
        }
        boolean windApproach=air==Air.WIND_LAND;
        boss.setGliding(true);boss.setNoGravity(false);aim(windApproach?landing.add(0,3,0):landing,12);
        // Glide down slowly; normal collision performs the final touchdown.
        Vec3 motion=horizontal.scale(.18);double cap=.6*flightFactor();
        if(motion.length()>cap)motion=motion.normalize().scale(cap);
        double vertical=windApproach?Mth.clamp((3-height)*.2,-.45,.35):-Math.min(.45,Math.max(.12,height*.12));
        boss.setDeltaMovement(motion.add(0,vertical,0));VanillaCombat.syncMotion(boss);
    }
    private void finishLanding() {
        shortCrystalCharge=false;crystalLaunchTarget=-1;
        boss.setGliding(false);enterAir(Air.GROUND);aerial=false;groundCharge=false;chargeHit=false;
        landing=lockedLanding=chargePoint=chargeDirection=null;shotsLeft=airborne=grounded=0;recover();
    }
    public void damaged(float amount) {
        if(air==Air.WINDUP||air==Air.DROP) { interrupt+=amount;if(interrupt>=INTERRUPT_DAMAGE)enterAir(Air.FALL); }
    }
    private void teleport(LivingEntity target) {
        if(air==Air.WINDUP||air==Air.DROP)return;
        ServerLevel level=(ServerLevel)boss.level();
        Vec3 best=null;double bestScore=-Double.MAX_VALUE;
        var projectiles=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,boss.getBoundingBox().inflate(12),p->p.getOwner()!=boss);
        for(int attempt=0;attempt<8;attempt++) {
            Vec3 delta=target.position().subtract(boss.position());
            Vec3 at=delta.length()>8?boss.position().add(delta.normalize().scale(7)):
                target.position().add((boss.getRandom().nextDouble()-.5)*6,aerial?1:0,(boss.getRandom().nextDouble()-.5)*6);
            if(at.distanceToSqr(boss.position())>64||!safeTeleport(at))continue;
            double score=-at.distanceToSqr(target.position());
            AABB bounds=boss.getBoundingBox().move(at.subtract(boss.position())).inflate(.2);
            for(var projectile:projectiles)if(bounds.clip(projectile.position(),projectile.position().add(projectile.getDeltaMovement().scale(4))).isPresent())score-=100;
            if(score>bestScore){bestScore=score;best=at;}
        }
        if(best==null)return;
        EntityTeleportEvent event=new EntityTeleportEvent(boss,best.x,best.y,best.z);
        if(MinecraftForge.EVENT_BUS.post(event))return;
        Vec3 destination=new Vec3(event.getTargetX(),event.getTargetY(),event.getTargetZ());
        if(destination.distanceToSqr(boss.position())>64||!safeTeleport(destination))return;
        boss.playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,1,1);
        boss.teleportTo(destination.x,destination.y,destination.z);boss.getNavigation().stop();
    }
    private boolean safeTeleport(Vec3 at) {
        AABB bounds=boss.getBoundingBox().move(at.subtract(boss.position()));
        return boss.level().hasChunksAt(BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ),BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ))
            &&at.y>=boss.level().getMinBuildHeight()&&bounds.maxY<boss.level().getMaxBuildHeight()
            &&boss.level().getWorldBorder().isWithinBounds(bounds)&&boss.level().noCollision(boss,bounds)&&!boss.level().containsAnyLiquid(bounds);
    }
    public void stop() {
        fluidTicks=riptideCharge=riptideCooldown=0;fluidCombat=false;boss.setRiptideTicks(0);
        tailGround=false;
        shortCrystalCharge=false;crystalLaunchTarget=-1;
        previousTargetPosition=null;previousTargetId=-1;spacing=escaping=false;
        boss.getNavigation().stop();boss.stopUsingItem();boss.setGliding(false);boss.setMaceWindup(false);boss.setNoGravity(false);boss.setSprinting(false);boss.setJumping(false);
        crystalAura.clear();swordAttempted=false;crystalStall=crystalRetryAt=0;pearlOpening=false;ground=Ground.ARROW;air=Air.GROUND;age=airAge=airborne=grounded=0;aerial=false;base=explosive=shield=null;lockedLanding=placementPosition=landing=chargePoint=chargeDirection=null;shotsLeft=windWait=0;chargeTarget=-1;groundCharge=chargeHit=windFired=false;
    }
}
