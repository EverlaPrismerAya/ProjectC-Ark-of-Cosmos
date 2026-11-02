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
    private enum Air { APPROACH,STRAFE,CLIMB,WINDUP,DROP,LAND }
    private final FourthCalamityEntity boss;
    private final FourthCalamityCrystalAura crystalAura;
    private Ground ground=Ground.ARROW;
    private Air air=Air.APPROACH;
    private int age,airAge,airborne,grounded,crystals,remaining,meleeDelay;
    private int crystalStall,crystalRetryAt;
    private boolean aerial,hadTarget,pearlOpening;
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
        if(boss.phase()==3&&boss.tickCount%TAIL_INTERVAL==0)teleport(target);
        boolean jumped=lastTargetId==target.getId()&&targetWasGrounded&&!target.onGround()
            &&target.getDeltaMovement().y>0&&!target.isInWater()&&!target.isFallFlying();
        lastTargetId=target.getId();targetWasGrounded=target.onGround();
        if(jumped&&!aerial&&(air==Air.APPROACH||air==Air.STRAFE)) {
            boss.actions.pearl(target);boss.stopUsingItem();boss.getNavigation().stop();
            base=null;crystals=0;remaining=1+boss.getRandom().nextInt(3);transition(Ground.CRYSTALS);
        }
        if(boss.actions.consumePearlArrival()&&boss.phase()==2&&!aerial) {
            pearlOpening=true;transition(Ground.ARROW);boss.getNavigation().stop();boss.stopUsingItem();
        }
        updateMode(target);
        if(aerial||air==Air.CLIMB||air==Air.WINDUP||air==Air.DROP||air==Air.LAND)flight(target);
        else ground(target);
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
        if(!aerial&&airborne>=20) {
            aerial=true;pearlOpening=false;air=Air.APPROACH;airAge=0;boss.getNavigation().stop();boss.stopUsingItem();ground=Ground.ARROW;age=0;
        } else if(aerial&&grounded>=10) {
            aerial=false;air=Air.CLIMB;airAge=0;boss.stopUsingItem();
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
    private void recover() { pearlOpening=false;transition(Ground.RECOVER);boss.getNavigation().stop(); }
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
            if(crystalAura.tick(target)) {
                boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);
            } else positionForCrystals(target);
            return;
        }
        if(boss.phase()==3&&ground!=Ground.RETREAT&&ground!=Ground.PREPARE&&ground!=Ground.SHIELD&&ground!=Ground.PLACE&&ground!=Ground.CHARGE&&ground!=Ground.DETONATE) {
            chase(target);
            if(boss.tickCount%AURA_INTERVAL==0) {
                var crystal=boss.actions.bestCrystal(target);
                if(crystal!=null)boss.actions.breakCrystal(crystal);
                else {BlockPos candidate=boss.actions.bestBase(target);if(candidate!=null)boss.actions.crystal(candidate);}
            }
            if(boss.distanceToSqr(target)<9&&meleeDelay==0){boss.actions.hold(Items.NETHERITE_SWORD);boss.actions.melee(target);meleeDelay=13;}
            return;
        }
        switch(ground) {
            case ARROW -> { if(age==1)boss.actions.arrow(target,false);if(age>=12)transition(pearlOpening?Ground.HIT:Ground.PEARL); }
            case PEARL -> { boss.actions.pearl(target);chase(target);if(boss.distanceToSqr(target)<9)transition(Ground.HIT);else if(age>60)recover(); }
            case HIT -> {
                chase(target);boss.actions.hold(Items.NETHERITE_SWORD);
                if(meleeDelay==0&&boss.actions.melee(target)){meleeDelay=13;transition(Ground.CHECK);}
                else if(age>30)recover();
            }
            case CHECK -> {
                if(!target.onGround()||target.getDeltaMovement().y>.05) {
                    base=boss.actions.bestBase(target);crystals=0;remaining=1+boss.getRandom().nextInt(3);
                    transition(Ground.CRYSTALS);
                } else if(age>=8)transition(Ground.RETREAT);
            }
            case CRYSTALS -> {
                boolean progressed=false;
                var crystal=boss.actions.bestCrystal(target);
                if(crystal!=null) {
                    if(boss.actions.breakCrystal(crystal)){crystals++;progressed=true;}
                } else {
                    if(base==null||!boss.actions.crystalSpace(base))base=boss.actions.bestBase(target);
                    if(base!=null) {
                        boss.getNavigation().stop();boss.setSprinting(false);boss.setJumping(false);
                        boolean hadBase=boss.level().getBlockState(base).is(Blocks.OBSIDIAN)||boss.level().getBlockState(base).is(Blocks.BEDROCK);
                        progressed=boss.actions.crystal(base);
                        if(!hadBase&&boss.level().getBlockState(base).is(Blocks.OBSIDIAN))progressed=true;
                    } else chase(target);
                }
                crystalStall=progressed?0:crystalStall+1;
                if(crystals>=remaining)recover();
                else if(crystalStall>=CRYSTAL_STALL_TICKS) {
                    crystalRetryAt=boss.tickCount+CRYSTAL_RETRY_TICKS;base=null;
                    if(crystals==0)transition(Ground.HIT);else recover();
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
                // Cover is best effort; never abandon an armed explosive to walk back.
                if(boss.level().getBlockState(shield).is(Blocks.OBSIDIAN)||boss.actions.place(Items.OBSIDIAN,shield)||age>=2)
                    transition(anchor?Ground.CHARGE:Ground.DETONATE);
            }
            case CHARGE -> {
                if(!boss.level().getBlockState(explosive).is(Blocks.RESPAWN_ANCHOR)){recover();break;}
                if(boss.level().getBlockState(explosive).getValue(RespawnAnchorBlock.CHARGE)>0)transition(Ground.DETONATE);
                else if(boss.actions.useBlock(Items.GLOWSTONE,explosive)&&boss.level().getBlockState(explosive).is(Blocks.RESPAWN_ANCHOR)
                    &&boss.level().getBlockState(explosive).getValue(RespawnAnchorBlock.CHARGE)>0)transition(Ground.DETONATE);else if(age>12)recover();
            }
            case DETONATE -> {
                var state=boss.level().getBlockState(explosive);
                boolean usable=anchor?state.is(Blocks.RESPAWN_ANCHOR)&&state.getValue(RespawnAnchorBlock.CHARGE)>0&&!boss.level().dimensionType().respawnAnchorWorks():state.getBlock() instanceof BedBlock&&!boss.level().dimensionType().bedWorks();
                if(!usable||boss.actions.useBlock(Items.OBSIDIAN,explosive)||age>12)recover();
            }
            case RECOVER -> { if(age>=RECOVER_TICKS)transition(Ground.ARROW); }
        }
    }
    private void positionForCrystals(LivingEntity target) {
        boss.actions.hold(Items.END_CRYSTAL);boss.setSprinting(false);boss.setJumping(false);
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        // Keep space for the real crystal entity instead of pathing into the target's occupied block.
        Vec3 destination=target.position().add(away.scale(3.5)).add(-away.z,0,away.x);
        moveToPoint(destination);
    }
    private void moveToPoint(Vec3 point) {
        // Reach the placement waypoint precisely rather than stopping a block short.
        boss.getNavigation().moveTo(boss.getNavigation().createPath(BlockPos.containing(point),0),1.1);
    }
    private void propel(Vec3 point,boolean climb) {
        boss.getNavigation().stop();boss.setJumping(false);boss.setGliding(true);boss.setNoGravity(false);
        Vec3 next=point;
        Vec3 ahead=boss.position().add(boss.getDeltaMovement().normalize().scale(4));
        if(boss.level().clip(new net.minecraft.world.level.ClipContext(boss.getEyePosition(),ahead,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS)next=boss.position().add(0,6,0);
        aim(next,8);
        double factor=boss.phase()>=2?1.5:1;
        Vec3 motion=boss.getDeltaMovement().add(boss.getLookAngle().scale(.07*factor)).add(0,climb?.12:0,0);
        if(boss.onGround())motion=motion.add(0,.65,0);
        double cap=2.2*factor;if(motion.length()>cap)motion=motion.normalize().scale(cap);
        boss.setDeltaMovement(motion);VanillaCombat.syncMotion(boss);
    }
    private void flight(LivingEntity target) {
        airAge++;
        boss.setMaceWindup(air==Air.WINDUP);
        switch(air) {
            case APPROACH -> {
                Vec3 point=target.getEyePosition().add(target.getDeltaMovement().scale(5));propel(point,target.getY()>boss.getY()+3);
                boss.actions.hold(VanillaCombat.SPEARS.get("netherite").get());
                if(!boss.isUsingItem()){boss.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);SpearCombat.start(boss);}
                Vec3 delta=target.position().subtract(boss.position());
                if(airAge>12&&(delta.length()<6&&delta.normalize().dot(boss.getLookAngle())<.25||airAge>60)) {
                    boss.stopUsingItem();air=Air.STRAFE;airAge=0;
                }
            }
            case STRAFE -> {
                propel(target.position().add(0,10,0),true);aim(target.getEyePosition(),20);
                if(airAge%CROSSBOW_INTERVAL==0)boss.actions.arrow(target,true);
                if(airAge>=30){air=Air.APPROACH;airAge=0;}
            }
            case CLIMB -> {
                boss.actions.hold(VanillaCombat.MACE.get());propel(target.position().add(0,MACE_HEIGHT+3,0),true);
                if(boss.getY()>=target.getY()+MACE_HEIGHT) {
                    boss.setGliding(false);boss.setNoGravity(true);boss.setDeltaMovement(Vec3.ZERO);interrupt=0;air=Air.WINDUP;airAge=0;boss.setMaceWindup(true);
                } else if(airAge>100){boss.stopUsingItem();air=Air.LAND;airAge=0;}
            }
            case LAND -> {
                if(boss.onGround()||boss.isInWater())finishMace();
                else propel(target.position(),false);
            }
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
                if(boss.fallDistance>1.5&&boss.distanceToSqr(target)<9&&boss.hasLineOfSight(target)&&boss.actions.melee(target))finishMace();
                else if(boss.onGround()||boss.isInWater()||airAge>100)finishMace();
            }
        }
    }
    private void finishMace() {
        boss.setNoGravity(false);boss.setGliding(false);boss.setMaceWindup(false);air=Air.APPROACH;airAge=0;lockedLanding=null;recover();
    }
    public void damaged(float amount) { if(air==Air.WINDUP||air==Air.DROP){interrupt+=amount;if(interrupt>=INTERRUPT_DAMAGE)finishMace();} }
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
        crystalAura.clear();crystalStall=crystalRetryAt=0;pearlOpening=false;lastTargetId=-1;targetWasGrounded=false;ground=Ground.ARROW;air=Air.APPROACH;age=airAge=airborne=grounded=0;aerial=false;base=explosive=shield=null;lockedLanding=placementPosition=null;
    }
}
