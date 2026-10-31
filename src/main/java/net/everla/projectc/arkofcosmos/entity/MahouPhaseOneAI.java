package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Shared targeting/movement and phase-one choreography. Projectiles own collision and synchronized visuals. */
final class MahouPhaseOneAI {
    private final MahouTsukaiEntity boss;
    private final MahouPhaseTwoAI phaseTwo;
    private final MahouPhaseThreeAI phaseThree;
    private int movementPhase=1;
    private int attack, age, cooldown = 20, lastAttack, unseen;
    private Vec3 wander, territory, aim = Vec3.ZERO;
    private int patrolPause, patrolBlocked;
    private double patrolAngle;
    private MahouAttackEntity bladeWarning;
    private net.everla.projectc.arkofcosmos.util.AttackFrame swingFrame;
    private GalaxiaEntity galaxyBlade;
    private MahouAttackEntity warning;
    private ElementsEntity elementBlade;
    private Vec3 retreat, chargePoint;
    private int swings, swingWait, dashAge, summonTicks;
    private final int[] attackUses=new int[8];
    private boolean ready;
    private final java.util.List<Vec3> lightningOrigins=new java.util.ArrayList<>();
    private Vec3 lightningAim=Vec3.ZERO;
    MahouPhaseOneAI(MahouTsukaiEntity boss) { this.boss = boss; phaseTwo=new MahouPhaseTwoAI(boss,this); phaseThree=new MahouPhaseThreeAI(boss,this); }

    static boolean valid(MahouTsukaiEntity boss, LivingEntity target) {
        return target != null && target != boss && target.isAlive() && !target.isRemoved()
                && target.level() == boss.level() && !boss.isAlliedTo(target) && boss.distanceToSqr(target) <= 64 * 64
                && (!(target instanceof Player p) || !p.isCreative() && !p.isSpectator());
    }

    void tick() {
        if(boss.phaseRecovering()||boss.phaseTransitionTicks()>0){reset();phaseTwo.reset();boss.setNoGravity(true);return;}
        if(movementPhase!=boss.phase()){reset();phaseTwo.reset();phaseThree.reset();movementPhase=boss.phase();}
        boss.setNoGravity(true);
        Player player = boss.level().getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), 48,
                p -> p instanceof Player candidate && valid(boss, candidate) && boss.hasLineOfSight(candidate));
        LivingEntity target = boss.getTarget();
        if (player != null) { target = player; unseen = 0; }
        else if (!valid(boss, target) || target instanceof Player && ++unseen > 100) target = null;
        if(player==null&&!(target instanceof Player&&valid(boss,target))){
            var warden=boss.level().getEntitiesOfClass(net.minecraft.world.entity.monster.warden.Warden.class,
                    boss.getBoundingBox().inflate(48),w->valid(boss,w)&&boss.hasLineOfSight(w)).stream()
                    .min(java.util.Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
            if(warden!=null)target=warden;
        }
        if (target == null && valid(boss, boss.getLastHurtByMob())) target = boss.getLastHurtByMob();
        boss.setTarget(target);
        if (target == null) {
            if (attack != 0) reset();
            phaseTwo.cancel();phaseThree.cancel();patrol();return;
        }

        face(target.getBoundingBox().getCenter());
        if(boss.phase()==2){phaseTwo.tick(target);return;}
        if(boss.phase()==3){phaseThree.tick(target);return;}
        if (attack == 0) {
            keepDistance(target, false);
            if (--cooldown <= 0 && boss.hasLineOfSight(target)) {
                int next=nextAttack();
                attack = next; lastAttack = next; age = 0;
            }
            return;
        }
        age++;
        switch (attack) {
            case 1 -> {
                if (age == 1 || age == 19 || age == 37) {
                    Vec3 at = boss.position().add(0, 7, 0);
                    Vec3 end = target.position();
                    MahouAttackEntity.spawn(boss, MahouAttackEntity.PILLAR, at, end.subtract(at).normalize().scale(1.4), 10, 85, 12);
                }
                if (age >= 65) finish();
            }
            case 2 -> {
                Vec3 landing=boss.position();
                double floor=ground(landing);
                // Do not descend into a ravine while the target is fighting from a higher ledge.
                if(floor<target.getY()-2){
                    double targetFloor=ground(target.position());
                    if(targetFloor>=target.getY()-2){landing=target.position();floor=targetFloor;}
                }
                steer(new Vec3(landing.x,floor,landing.z),.3);
                if (age >= 20 && age < 60 && boss.position().multiply(1,0,1).distanceToSqr(landing.multiply(1,0,1))<1
                        && Math.abs(boss.getY()-floor) < .35) {
                    age = 60;
                    for (int i=0;i<3;i++) {
                        double angle = Math.toRadians(boss.getYRot()) + i*Math.PI*2/3;
                        Vec3 at = boss.position().add(Math.cos(angle)*5,0,Math.sin(angle)*5);
                        at = new Vec3(at.x,ground(at),at.z);
                        MahouAttackEntity.spawn(boss,MahouAttackEntity.COVER,at.add(0,12,0),new Vec3(0,-.8,0),12,130,10);
                    }
                    MahouAttackEntity.spawn(boss,MahouAttackEntity.WAVE,boss.position().add(0,.15,0),Vec3.ZERO,45,85,18);
                }
                if (age >= 135) finish();
            }
            case 3 -> {
                if(age==1)beginRetreat(target);
                if(!retreatComplete())return;
                if (!ready) {
                    ready=true; swingWait=age+12;
                    Vec3 side = target.position().subtract(boss.position()).multiply(1,0,1).normalize();
                    side = new Vec3(-side.z,0,side.x).scale(boss.getRandom().nextBoolean()?16:-16);
                    Vec3 start = boss.getBoundingBox().getCenter(), actual = move(side);
                    lightningOrigins.clear();
                    for(int i=0;i<6;i++)lightningOrigins.add(start.add(actual.scale(i/5d)));
                    lightningAim=target.getBoundingBox().getCenter();
                }
                if(!lightningOrigins.isEmpty()&&(swingWait-age)%2==0){
                    Vec3 at=lightningOrigins.remove(boss.getRandom().nextInt(lightningOrigins.size()));
                    GalaxiaEntity.bossBolt(boss,at,lightningAim.subtract(at).normalize());
                    ArkOfCosmos.sound(boss,"galaxia_lightning",1);
                }
                if (ready && age >= swingWait) finish();
            }
            case 4 -> {
                if(age==1){beginRetreat(target);dashAge=0;}
                if(!ready){if(!retreatComplete())return;ready=true;}
                int cycle=dashAge++%43;
                boss.setDeltaMovement(Vec3.ZERO);
                if(cycle==0){
                    ItemStack weapon=new ItemStack(ArkOfCosmos.GALAXIA.get());
                    weapon.getOrCreateTag().putInt("GalaxiaMode",2);
                    boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,weapon);
                    aim=target.getBoundingBox().getCenter().subtract(boss.getBoundingBox().getCenter()).normalize();
                    chargePoint=boss.getBoundingBox().getCenter().add(aim.scale(20));
                    galaxyBlade=GalaxiaEntity.bossCharge(boss,chargePoint);
                    warning=MahouAttackEntity.spawn(boss,MahouAttackEntity.WARNING,boss.getBoundingBox().getCenter(),aim.scale(52),0,43,0);
                }
                if(cycle<26&&warning!=null&&chargePoint!=null){
                    Vec3 direction=chargePoint.subtract(boss.getBoundingBox().getCenter()).normalize();
                    warning.pointWarning(boss.getBoundingBox().getCenter(),direction);
                    face(boss.getEyePosition().add(direction.scale(20)));
                }
                if(cycle==25&&galaxyBlade!=null)galaxyBlade.releaseBossCharge();
                // Let the released star advance through the item's native AI before pursuing it.
                if(cycle==26)galaxyBlade=GalaxiaEntity.bossDash(boss,warning);
                if(cycle>=26&&warning!=null&&!warning.isRemoved())face(boss.getEyePosition().add(MahouAttackEntity.velocity(warning.view())));
                if(dashAge>=129)finish();
                else if(dashAge%43==0){beginRetreat(target);age=1;}
            }
            case 5 -> {
                if(age==1){
                    boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get()));
                    beginRetreat(target);swings=swingWait=0;summonTicks=10;elementBlade=null;
                }
                if(!retreatComplete())return;
                ready=true;
                if(summonTicks>0){summonTicks--;keepDistance(target,false);face(target.getBoundingBox().getCenter());return;}
                if(elementBlade!=null&&!elementBlade.isRemoved())break;
                if(swings>=4){finish();return;}
                if(bladeWarning==null){
                    swingFrame=net.everla.projectc.arkofcosmos.util.AttackFrame.capture(boss);
                    bladeWarning=MahouAttackEntity.swingWarning(boss,swingFrame.forward(),swings%2);
                    bladeWarning.setPos(boss.getX(),ground(boss.position())+.08,boss.getZ());
                    swingWait=12;keepDistance(target,false);return;
                }
                keepDistance(target,false);
                face(target.getBoundingBox().getCenter());
                swingFrame=net.everla.projectc.arkofcosmos.util.AttackFrame.capture(boss);
                bladeWarning.pointSwingWarning(new Vec3(boss.getX(),ground(boss.position())+.08,boss.getZ()),swingFrame.forward());
                if(--swingWait<=0){
                    bladeWarning.discard();bladeWarning=null;
                    elementBlade=net.everla.projectc.arkofcosmos.combat.ElementsCombat.swing(boss,swings%2,0,
                            net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules.BASE_DAMAGE,swingFrame);
                    boss.swing(net.minecraft.world.InteractionHand.MAIN_HAND);swings++;
                }
            }

            case 6 -> {
                boss.setDeltaMovement(Vec3.ZERO);
                if(age==1||age==19||age==37){
                    Vec3 at=target.position();
                    MahouAttackEntity.spawn(boss,MahouAttackEntity.BLOOM,new Vec3(at.x,ground(at)+.1,at.z),Vec3.ZERO,14,26,12);
                }
                if(age>=64)finish();
            }
            case 7 -> {
                boss.setDeltaMovement(Vec3.ZERO);
                if(age==1){
                    Vec3 at=target.position();
                    MahouAttackEntity.spawn(boss,MahouAttackEntity.GRAVITY,new Vec3(at.x,ground(at)+.1,at.z),Vec3.ZERO,24,42,20);
                }
                if(age>=44)finish();
            }
            case 8 -> {
                boss.setDeltaMovement(Vec3.ZERO);
                if(age==1){
                    aim=target.getBoundingBox().getCenter().subtract(boss.getBoundingBox().getCenter()).normalize();
                    warning=MahouAttackEntity.spawn(boss,MahouAttackEntity.DOMAIN,boss.getBoundingBox().getCenter().subtract(aim.scale(3)),aim,0,64,0);
                }
                if(age<=29&&age%2==1){
                    Vec3 right=new Vec3(-aim.z,0,aim.x).normalize();
                    if(right.lengthSqr()<.1)right=new Vec3(1,0,0);
                    Vec3 at=boss.getBoundingBox().getCenter().subtract(aim.scale(3+boss.getRandom().nextDouble()*3))
                            .add(right.scale((boss.getRandom().nextDouble()-.5)*24)).add(0,1+boss.getRandom().nextDouble()*12,0);
                    int delay=4+boss.getRandom().nextInt(21);
                    MahouAttackEntity.spawn(boss,MahouAttackEntity.NEEDLE_GATE,at,target.getBoundingBox().getCenter().subtract(at),delay,delay+10,
                            net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules.BASE_DAMAGE*.8f);
                }
                if(age>=64)finish();
            }

        }
        // Native Polaris movement owns its trajectory; other casts can kite while attacking.
        if(attack!=4&&attack!=2)keepDistance(target,false);
        if(attack!=2)keepAltitude(target);
    }

    private int nextAttack(){
        int minimum=java.util.Arrays.stream(attackUses).min().orElse(0);
        double total=0;
        for(int i=0;i<8;i++)if(i+1!=lastAttack)total+=1d/Math.pow(1d+attackUses[i]-minimum,2);
        double choice=boss.getRandom().nextDouble()*total;
        int selected=1;
        for(int i=0;i<8;i++)if(i+1!=lastAttack){
            selected=i+1;choice-=1d/Math.pow(1d+attackUses[i]-minimum,2);if(choice<=0)break;
        }
        if(minimum>1000)for(int i=0;i<8;i++)attackUses[i]-=minimum;
        attackUses[selected-1]++;lastAttack=selected;return selected;
    }

    private Vec3 avoidSolid(Vec3 desired){
        // Prefer open swept volumes, but phase out when completely enclosed rather than becoming trapped.
        for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}){
            Vec3 candidate=desired.yRot((float)angle);
            if(!boss.level().getBlockCollisions(boss,boss.getBoundingBox().expandTowards(candidate)).iterator().hasNext())return candidate;
        }
        return desired.add(0,.3,0);
    }

    private void patrol(){
        Vec3 previous=boss.getDeltaMovement();double speed=previous.multiply(1,0,1).length();
        if(speed>.1)boss.setDeltaMovement(previous.x*.1/speed,Math.max(-.12,Math.min(.12,previous.y)),previous.z*.1/speed);
        if(territory==null){territory=boss.position();patrolAngle=Math.toRadians(boss.getYRot());}
        if(boss.horizontalCollision)patrolBlocked++;else patrolBlocked=0;
        if(wander!=null&&boss.position().multiply(1,0,1).distanceTo(wander.multiply(1,0,1))<1){
            wander=null;patrolPause=40+boss.getRandom().nextInt(41);
        }
        if(patrolBlocked>=20){wander=null;patrolBlocked=0;patrolPause=20;}
        if(patrolPause>0){
            patrolPause--;
            boss.setDeltaMovement(boss.getDeltaMovement().multiply(.8,0,.8).add(0,
                    Math.max(-.12,Math.min(.12,hoverGround(boss.position())+2-boss.getY())),0));return;
        }
        if(wander==null){
            patrolAngle+=.65+boss.getRandom().nextDouble()*.2;
            double radius=12+boss.getRandom().nextDouble()*4;
            Vec3 at=territory.add(Math.cos(patrolAngle)*radius,0,Math.sin(patrolAngle)*radius);
            wander=new Vec3(at.x,hoverGround(at)+2,at.z);
        }
        wander=new Vec3(wander.x,hoverGround(wander)+2,wander.z);
        Vec3 horizontal=wander.subtract(boss.position()).multiply(1,0,1);
        Vec3 delta=new Vec3(horizontal.x,hoverGround(boss.position().add(horizontal.normalize().scale(2)))+2-boss.getY(),horizontal.z);
        Vec3 travel=horizontal.length()<.10?horizontal:horizontal.normalize().scale(.10);
        Vec3 desired=travel.add(0,Math.max(-.12,Math.min(.12,delta.y)),0);
        Vec3 velocity=boss.getDeltaMovement().lerp(desired,.12);
        velocity=avoidSolid(velocity);boss.setDeltaMovement(velocity);
        if(velocity.multiply(1,0,1).lengthSqr()>1e-6)face(boss.getEyePosition().add(velocity.normalize().scale(8)));
    }

    private double hoverGround(Vec3 at){return hoverGround(at,boss.getY()-2);}
    private double hoverGround(Vec3 at,double fallback){
        double topY=boss.getY()+3;
        // Recover even if already submerged: a ray starting underwater only sees that interior fluid cell.
        if(boss.isInWaterOrBubble()||!boss.level().getFluidState(boss.blockPosition()).isEmpty()||!boss.level().getFluidState(net.minecraft.core.BlockPos.containing(at.x,boss.getY(),at.z)).isEmpty())
            topY=Math.max(topY,boss.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.util.Mth.floor(at.x),net.minecraft.util.Mth.floor(at.z))+1);
        Vec3 top=new Vec3(at.x,topY,at.z);
        BlockHitResult hit=boss.level().clip(new ClipContext(top,top.add(0,-64,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,boss));
        return hit.getType()==HitResult.Type.MISS?fallback:hit.getLocation().y;
    }

    void keepDistance(LivingEntity target,boolean grounded){
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1);
        double distance=away.length();
        if(distance<.001)away=Vec3.directionFromRotation(0,boss.getYRot()).scale(-1);
        away=away.normalize();
        Vec3 horizontal=distance<10?away.scale(Math.min(1.5,.55+(10-distance)*.16))
                :distance>18?away.scale(-Math.min(2,.5+(distance-18)*.1))
                :new Vec3(-away.z,0,away.x).scale(lastAttack%2==0?.32:-.32);
        double height=(grounded?ground(boss.position().add(horizontal)):combatHeight(boss.position().add(horizontal),target));
        boss.setDeltaMovement(avoidSolid(horizontal.add(0,Math.max(-.3,Math.min(.3,height-boss.getY())),0)));
    }

    private void beginRetreat(LivingEntity target){
        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.1)away=boss.getLookAngle().multiply(-1,0,-1).normalize();
        double distance=Math.min(18,Math.max(16,boss.position().subtract(target.position()).multiply(1,0,1).length()+6));
        Vec3 at=target.position().add(away.scale(distance));
        retreat=new Vec3(at.x,combatHeight(at,target),at.z);ready=false;
    }
    private boolean retreatComplete(){
        if(!ready&&age<28&&boss.position().distanceToSqr(retreat)>.25&&!boss.horizontalCollision){steer(retreat,.95);return false;}
        boss.setDeltaMovement(Vec3.ZERO);keepAltitude(boss.getTarget());return true;
    }
    double combatHeight(Vec3 at,LivingEntity target){
        double targetHeight=target.getBoundingBox().maxY+2;
        return Math.max(hoverGround(at,targetHeight-2)+2,targetHeight);
    }
    void keepAltitude(LivingEntity target){
        if(target==null)return;
        Vec3 velocity=boss.getDeltaMovement();
        double dy=combatHeight(boss.position().add(velocity.multiply(1,0,1)),target)-boss.getY();
        boss.setDeltaMovement(velocity.x,Math.max(-.3,Math.min(.3,dy)),velocity.z);
    }
    void face(Vec3 point){
        Vec3 direction=point.subtract(boss.getEyePosition());
        if(direction.lengthSqr()<1e-8)return;
        boss.getLookControl().setLookAt(point.x,point.y,point.z,180,180);
        boss.setYRot((float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90);
        boss.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));
        boss.yBodyRot=boss.yHeadRot=boss.getYRot();
    }
    private double ground(Vec3 at) {
        Vec3 top = new Vec3(at.x,Math.max(boss.getY()+2,boss.getTarget()==null?boss.getY()+2:boss.getTarget().getBoundingBox().maxY+2),at.z), bottom=top.add(0,-48,0);
        BlockHitResult hit=boss.level().clip(new ClipContext(top,bottom,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        // No landing surface: hold altitude instead of chasing a floor two blocks below every tick.
        return hit.getType()==HitResult.Type.MISS ? boss.getY() : hit.getLocation().y;
    }
    void steer(Vec3 at,double speed) {
        if(attack!=2&&boss.getTarget()!=null)at=new Vec3(at.x,Math.max(at.y,combatHeight(at,boss.getTarget())),at.z);
        Vec3 delta=at.subtract(boss.position());
        boss.setDeltaMovement(avoidSolid(delta.length()<speed?delta:delta.normalize().scale(speed)));
        // Respect explicitly higher waypoints; only correct motion that would cross the combat-height floor.
        if(attack!=2&&boss.getTarget()!=null){
            Vec3 velocity=boss.getDeltaMovement();
            double floor=combatHeight(boss.position().add(velocity.multiply(1,0,1)),boss.getTarget());
            if(boss.getY()+velocity.y<floor)boss.setDeltaMovement(velocity.x,Math.min(.3,floor-boss.getY()),velocity.z);
        }
    }
    private Vec3 move(Vec3 delta) {
        boss.setDeltaMovement(Vec3.ZERO);
        Vec3 before=boss.position(); boss.move(MoverType.SELF,delta); boss.hasImpulse=true;
        return boss.position().subtract(before);
    }
    private void finish() { reset(); cooldown=12+boss.getRandom().nextInt(11); }
    private void reset() {
        attack=age=0; lightningOrigins.clear(); boss.setDeltaMovement(Vec3.ZERO);
        if(galaxyBlade!=null&&!galaxyBlade.isRemoved())galaxyBlade.discard();galaxyBlade=null;
        if(elementBlade!=null&&!elementBlade.isRemoved())elementBlade.discard();elementBlade=null;
        if(warning!=null)warning.discard();warning=null;
        if(bladeWarning!=null)bladeWarning.discard();bladeWarning=null;
        boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);
    }
}
