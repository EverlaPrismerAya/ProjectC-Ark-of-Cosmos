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
    private Vec3 aim=Vec3.ZERO,dash=Vec3.ZERO;
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
        if(attack==0){movement.keepDistance(target,false);if(--cooldown<=0){attack=next();age=0;repeats=attack==1?3+boss.getRandom().nextInt(3):3+boss.getRandom().nextInt(2);boss.spell(attack);}return;}
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
                if(t<16){movement.steer(boss.position().subtract(target.getBoundingBox().getCenter().subtract(boss.position()).normalize().scale(14)),1.2);}
                if(t==16){spawn(boss,CASTING,hand(),target.getBoundingBox().getCenter().subtract(hand()),42,44,0);orb=spawn(boss,BLUE_CHARGE,hand(),Vec3.ZERO,0,43,0);}
                if(t>=16&&t<58&&orb!=null)orb.anchor(hand());
                if(t==58)spawn(boss,BLUE,hand(),target.position().subtract(hand()).normalize().scale(1.1),0,160,12);
                if(t>=87)finish();
            }
            case 3->{
                // Fusion begins with an immediate landing; the boss is stationary for the whole cast.
                if(t==0){
                    Vec3 top=new Vec3(target.getX()+5,Math.max(boss.getY()+2,target.getY()+2),target.getZ());
                    var floor=boss.level().clip(new net.minecraft.world.level.ClipContext(top,new Vec3(top.x,boss.level().getMinBuildHeight(),top.z),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                    if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)boss.setPos(floor.getLocation());
                    boss.setDeltaMovement(Vec3.ZERO);
                }
                boss.setDeltaMovement(Vec3.ZERO);
                if(t==1)spawn(boss,FUSION,boss.position().add(0,14,0),Vec3.ZERO,52,82,40);
                if(t>=120)finish();
            }
            case 4->{
                if(t<35)movement.steer(target.position().add(-22,7,0),1.2);else boss.setDeltaMovement(Vec3.ZERO);
                if(t==35)orb=spawn(boss,PURPLE_CORE,boss.position().add(0,7,0),Vec3.ZERO,25,130,0);
                if(t>=35&&orb!=null)orb.anchor(boss.position().add(0,7,0));
                if(t>=60&&t<150&&t%6==0){double a=boss.getRandom().nextDouble()*Math.PI*2;spawn(boss,PURPLE,orb.position(),new Vec3(Math.cos(a)*.22,.65,Math.sin(a)*.22),22,100,16);}
                if(t>=165)finish();
            }
            case 5->{
                int cycle=t%46;
                if(cycle<16){movement.steer(boss.position().subtract(target.getBoundingBox().getCenter().subtract(boss.position()).normalize().scale(14)),1.2);}
                if(cycle==16){orb=spawn(boss,BLUE_DASH,hand(),Vec3.ZERO,28,44,14);spawn(boss,CASTING,hand(),target.position().subtract(hand()),28,28,0);}
                if(cycle>=16&&cycle<44){boss.setDeltaMovement(Vec3.ZERO);dash=target.getBoundingBox().getCenter().subtract(boss.position().add(0,1,0)).normalize().scale(1.8);}
                else if(cycle>=44)boss.setDeltaMovement(dash);
                if(orb!=null)orb.anchor(hand());
                if(t>=repeats*46-1)finish();
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
    void reset(){attack=age=0;cooldown=20;boss.spell(0);if(warning!=null)warning.discard();if(orb!=null)orb.discard();warning=orb=null;}
    void cancel(){reset();}
    private void finish(){reset();cooldown=12+boss.getRandom().nextInt(11);}
}
