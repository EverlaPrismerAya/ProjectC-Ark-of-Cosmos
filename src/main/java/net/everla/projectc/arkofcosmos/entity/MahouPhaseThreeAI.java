package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.MahouAttackEntity.*;

/** Server-owned phase-three choreography. */
final class MahouPhaseThreeAI {
    private final MahouTsukaiEntity boss;
    private final MahouPhaseOneAI movement;
    private int attack,age,cooldown=20,repeats,last;
    private final int[] uses=new int[6];
    private Vec3 aim=Vec3.ZERO,blueAim=Vec3.ZERO,dash=Vec3.ZERO,fusionPosition,fusionDirection=Vec3.ZERO;
    private MahouAttackEntity warning,orb;
    MahouPhaseThreeAI(MahouTsukaiEntity boss,MahouPhaseOneAI movement){this.boss=boss;this.movement=movement;}
    private int next(){
        int min=java.util.Arrays.stream(uses).min().orElse(0);double total=0;
        for(int i=0;i<6;i++)if(i+1!=last)total+=1d/Math.pow(1+uses[i]-min,2);
        double roll=boss.getRandom().nextDouble()*total;
        for(int i=0;i<6;i++)if(i+1!=last&&(roll-=1d/Math.pow(1+uses[i]-min,2))<=0){uses[i]++;return last=i+1;}
        return 1;
    }
    private Vec3 hand(){return boss.position().add(boss.getLookAngle().scale(2)).add(0,1.3,0);}
    void tick(LivingEntity target){
        if(attack==0){
            if(--cooldown<=0){
                attack=next();age=0;repeats=attack==1?3+boss.getRandom().nextInt(3):3+boss.getRandom().nextInt(2);boss.spell(attack);
                // Fusion enters its landing state immediately. Do not perform
                // the ordinary hovering correction on the selection tick.
                if(attack==3){boss.setDeltaMovement(Vec3.ZERO);landFusion(target);}
            } else movement.keepDistance(target,false);
            return;
        }
        movement.face(target.getBoundingBox().getCenter());int t=age++;
        switch(attack){
            case 1->{
                movement.steer(target.position().add(10,9,0),1.1);
                int cycle=t%32;
                if(cycle==8){aim=target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(Math.min(16,boss.distanceTo(target)/1.8)));warning=spawn(boss,RED_WARNING,hand(),aim.subtract(hand()),0,16,0);}
                if(cycle>=8&&cycle<24&&warning!=null)warning.pointSwingWarning(hand(),aim.subtract(hand()));
                if(cycle==24)spawn(boss,RED,hand(),aim.subtract(hand()).normalize().scale(1.8),0,70,18);
                if(t>=repeats*32-1)finish();
            }
            case 2->{
                if(t<16){
                    Vec3 away= boss.position().subtract(target.getBoundingBox().getCenter()).normalize();
                    movement.steer(target.getBoundingBox().getCenter().add(away.scale(14)),1.2);
                }
                if(t==16){
                    blueAim=target.getBoundingBox().getCenter();
                    spawn(boss,CASTING,hand(),blueAim.subtract(hand()),24,28,0);
                    orb=spawn(boss,BLUE_CHARGE,hand(),Vec3.ZERO,0,43,0);
                }
                if(t>=16&&t<40&&orb!=null)orb.anchor(hand());
                if(t==40)spawn(boss,BLUE,hand(),blueAim.subtract(hand()).normalize().scale(1.1),0,160,12);
                if(t>=69)finish();
            }
            case 3->{
                // Fusion begins with an immediate landing; the boss is stationary for the whole cast.
                if(t==0&&fusionPosition==null)landFusion(target);
                if(fusionPosition!=null)boss.setPos(fusionPosition);
                boss.setDeltaMovement(Vec3.ZERO);
                if(t==1)spawn(boss,FUSION,boss.position().add(0,14,0),fusionDirection,58,88,40);
                if(t>=126)finish();
            }
            case 4->{
                if(t<35)movement.steer(target.position().add(-22,7,0),1.2);else boss.setDeltaMovement(Vec3.ZERO);
                if(t==35)orb=spawn(boss,PURPLE_CORE,boss.position().add(0,7,0),Vec3.ZERO,25,130,0);
                if(t>=35&&orb!=null)orb.anchor(boss.position().add(0,7,0));
                if(t>=60&&t<150&&t%6==0){double a=boss.getRandom().nextDouble()*Math.PI*2;spawn(boss,PURPLE,orb.position(),new Vec3(Math.cos(a)*.22,.65,Math.sin(a)*.22),22,100,16);}
                if(t>=165)finish();
            }
            case 5->{
                // Hold the aim, then keep the boss moving long enough to pass
                // the player.  The old two-tick dash was effectively invisible
                // at normal distances and could be cancelled by noPhysics.
                int cycle=t%58;
                if(cycle<16){
                    Vec3 away=boss.position().subtract(target.getBoundingBox().getCenter()).normalize();
                    movement.steer(target.getBoundingBox().getCenter().add(away.scale(14)),1.2);
                }
                if(cycle==16){
                    dash=target.getBoundingBox().getCenter().subtract(boss.getBoundingBox().getCenter()).normalize().scale(1.8);
                    orb=spawn(boss,BLUE_DASH,hand(),dash,16,32,14);
                    spawn(boss,CASTING,hand(),dash,16,16,0);
                }
                if(cycle>=16&&cycle<32){boss.setDeltaMovement(Vec3.ZERO);}
                else if(cycle>=32){
                    boss.setDeltaMovement(Vec3.ZERO);
                    boss.setPos(boss.position().add(dash));
                }
                if(orb!=null)orb.anchor(hand());
                if(t>=repeats*58-1)finish();
            }
            case 6->{
                boss.setDeltaMovement(Vec3.ZERO);
                if(t==0)spawn(boss,DOMAIN,boss.position(),Vec3.ZERO,30,190,0);
                if(t>=30&&t<180&&t%5==0){
                    double a=boss.getRandom().nextDouble()*Math.PI*2;
                    Vec3 from=target.position().add(Math.cos(a)*18,2+boss.getRandom().nextDouble()*10,Math.sin(a)*18);
                    spawn(boss,DOMAIN_TEXT,from,target.getBoundingBox().getCenter().subtract(from).normalize().scale(.55),18,100,10);
                }
                if(t>=195)finish();
            }
        }
    }
    private void landFusion(LivingEntity target){
        // Fusion is a vertical landing at the boss's current horizontal
        // position. The arrow communicates the target separately; it must not
        // pull the boss toward the player and create a second position jump.
        Vec3 start=boss.position();
        Vec3 landing=start;
        // The marker is an arrow for the player to follow, so its tip points
        // from the player toward the boss's landing position.
        fusionDirection=start.subtract(target.getBoundingBox().getCenter()).normalize();
        Vec3 top=new Vec3(start.x,Math.max(boss.getY()+2,target.getY()+2),start.z);
        var floor=boss.level().clip(new net.minecraft.world.level.ClipContext(top,new Vec3(top.x,boss.level().getMinBuildHeight(),top.z),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
        if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)landing=new Vec3(start.x,floor.getLocation().y,start.z);
        boss.setPos(landing);
        fusionPosition=boss.position();boss.setDeltaMovement(Vec3.ZERO);
    }
    void reset(){attack=age=0;cooldown=20;blueAim=Vec3.ZERO;fusionPosition=null;fusionDirection=Vec3.ZERO;boss.spell(0);if(warning!=null)warning.discard();if(orb!=null)orb.discard();warning=orb=null;}
    void cancel(){reset();}
    private void finish(){reset();cooldown=12+boss.getRandom().nextInt(11);}
}
