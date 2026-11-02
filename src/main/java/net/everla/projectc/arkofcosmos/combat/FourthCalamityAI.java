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
    public static final int RECOVER_TICKS=20,AURA_INTERVAL=2,CROSSBOW_INTERVAL=5,TAIL_INTERVAL=2;
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
    private boolean targetWasGrounded;
    private int lastTargetId=-1;
    private float interrupt;
    private BlockPos base,explosive,shield;
    private Vec3 lockedLanding;
    private Vec3 placementPosition;
    private boolean anchor;
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
        boolean jumped=lastTargetId==target.getId()&&targetWasGrounded&&!target.onGround()
            &&target.getDeltaMovement().y>0&&!target.isInWater()&&!target.isFallFlying();
        lastTargetId=target.getId();targetWasGrounded=target.onGround();
        if(jumped&&boss.phase()==1&&ground!=Ground.CRYSTALS&&boss.actions.pendingCrystal()==null&&!explosivePending()&&air==Air.GROUND) {
            boss.actions.pearl(target);boss.stopUsingItem();boss.getNavigation().stop();
            base=null;crystals=0;remaining=1+boss.getRandom().nextInt(3);transition(Ground.CRYSTALS);
        }
        if(boss.actions.consumePearlArrival()&&boss.phase()==2&&air==Air.GROUND&&!explosivePending()) {
            pearlOpening=true;transition(Ground.ARROW);boss.getNavigation().stop();boss.stopUsingItem();
        }
        if(!explosivePending())updateMode(target);
        if(boss.phase()==3&&air==Air.GROUND&&boss.tickCount%TAIL_INTERVAL==0)teleport(target);
        if(air!=Air.GROUND)flight(target);
        else ground(target);
    }
    private boolean explosivePending() {
        return ground==Ground.SHIELD||ground==Ground.CHARGE||ground==Ground.DETONATE;
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
        boss.getNavigation().moveTo(target,1.2);boss.setSprinting(true);
        if(boss.horizontalCollision&&boss.onGround())boss.setJumping(true);else boss.setJumping(false);
    }
    private void transition(Ground next) { ground=next;age=0;if(next==Ground.CRYSTALS)crystalStall=0; }
    private void recover() { swordAttempted=false;pearlOpening=false;transition(Ground.RECOVER);boss.getNavigation().stop(); }
    private void ground(LivingEntity target) {
        boss.setGliding(false);boss.setNoGravity(false);aim(target.getEyePosition(),30);age++;
        if(boss.distanceToSqr(target)>16*16)boss.actions.pearl(target);
        // In normal ground combat, a legal crystal opportunity takes precedence over the sword setup.
        if(boss.phase()==1&&boss.tickCount>=crystalRetryAt&&(ground==Ground.ARROW||ground==Ground.PEARL||ground==Ground.HIT||ground==Ground.CHECK)) {
            var available=boss.actions.bestCrystal(target);
            BlockPos candidate=available==null?boss.actions.bestBase(target):null;
            if(available!=null||candidate!=null) {
                base=candidate;crystals=0;remaining=1+boss.getRandom().nextInt(3);
                boss.getNavigation().stop();transition(Ground.CRYSTALS);
            }
        }
        if(boss.phase()==2&&!pearlOpening&&ground!=Ground.RETREAT&&ground!=Ground.PREPARE&&ground!=Ground.SHIELD&&ground!=Ground.PLACE&&ground!=Ground.CHARGE&&ground!=Ground.DETONATE) {
            var result=crystalAura.tick(target);
            if(result==FourthCalamityCrystalAura.TickResult.PROGRESSED||result==FourthCalamityCrystalAura.TickResult.WAITING) {
                boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);
            } else positionForCrystals(target);
            return;
        }
        if(boss.phase()==3&&ground!=Ground.RETREAT&&ground!=Ground.PREPARE&&ground!=Ground.SHIELD&&ground!=Ground.PLACE&&ground!=Ground.CHARGE&&ground!=Ground.DETONATE) {
            if(boss.tickCount%AURA_INTERVAL==0)crystalStep(target);
            if(boss.isAlive()&&boss.actions.attackAvailable()&&boss.distanceToSqr(target)<9&&meleeDelay==0){boss.actions.hold(Items.NETHERITE_SWORD);boss.actions.melee(target);meleeDelay=13;}
            return;
        }
        switch(ground) {
            case ARROW -> { if(age==1)boss.actions.arrow(target,false);if(age>=12)transition(pearlOpening?Ground.HIT:Ground.PEARL); }
            case PEARL -> { boss.actions.pearl(target);chase(target);if(boss.distanceToSqr(target)<9)transition(Ground.HIT);else if(age>60)recover(); }
            case HIT -> {
                chase(target);boss.actions.hold(Items.NETHERITE_SWORD);
                if(meleeDelay==0&&boss.actions.melee(target)){swordAttempted=true;meleeDelay=13;transition(Ground.CHECK);}
                else if(age>30)recover();
            }
            case CHECK -> {
                if(!target.onGround()||target.getDeltaMovement().y>.05) {
                    base=boss.actions.bestBase(target);crystals=0;remaining=1+boss.getRandom().nextInt(3);
                    transition(Ground.CRYSTALS);
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
                    if(crystals==0&&!swordAttempted)transition(Ground.HIT);else recover();
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
        if(crystal==null)crystal=boss.actions.bestCrystal(target);
        FourthCalamityActions.CrystalResult result;
        if(crystal!=null)result=boss.actions.detonateCrystal(crystal);
        else {
            if(!boss.actions.validCrystalBase(base))base=boss.actions.bestBase(target);
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
        double travel=Mth.clamp(boss.distanceTo(target)/(2.8*flightFactor()),1,20);
        return target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(travel));
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
                Vec3 point=predictedChargePoint(target);aim(point,30);
                if(airAge>=8&&boss.getTicksUsingItem()>=8) {
                    chargePoint=point;chargeDirection=point.subtract(boss.getEyePosition()).normalize();
                    if(boss.getLookAngle().dot(chargeDirection)>.95) {
                        chargeTarget=target.getId();chargeHit=false;air=Air.CHARGE;airAge=0;
                        boss.setDeltaMovement(chargeDirection.scale(2.8*flightFactor()));VanillaCombat.syncMotion(boss);
                    }
                }
            }
            case CHARGE -> {
                boss.setGliding(true);aim(boss.getEyePosition().add(chargeDirection.scale(20)),30);
                boss.setDeltaMovement(chargeDirection.scale(2.8*flightFactor()));VanillaCombat.syncMotion(boss);
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
                if(airAge%CROSSBOW_INTERVAL==0) {
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
    private boolean safeLanding(Vec3 point) {
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
        boss.getNavigation().stop();boss.stopUsingItem();boss.setGliding(false);boss.setMaceWindup(false);boss.setNoGravity(false);boss.setSprinting(false);boss.setJumping(false);
        crystalAura.clear();swordAttempted=false;crystalStall=crystalRetryAt=0;pearlOpening=false;lastTargetId=-1;targetWasGrounded=false;ground=Ground.ARROW;air=Air.GROUND;age=airAge=airborne=grounded=0;aerial=false;base=explosive=shield=null;lockedLanding=placementPosition=landing=chargePoint=chargeDirection=null;shotsLeft=windWait=0;chargeTarget=-1;groundCharge=chargeHit=windFired=false;
    }
}
