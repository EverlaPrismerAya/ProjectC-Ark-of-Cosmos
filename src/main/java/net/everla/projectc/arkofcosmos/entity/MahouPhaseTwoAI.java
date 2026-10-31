package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Phase-two choreography; movement and native weapon state machines remain shared. */
final class MahouPhaseTwoAI {
    private final MahouTsukaiEntity boss;
    private final MahouPhaseOneAI movement;
    private final int[] uses=new int[6];
    private int attack,age,cooldown=20,lastAttack,repeats,positioningTicks;
    private boolean positioned;
    private Vec3 destination,dash=Vec3.ZERO;
    private AttackFrame locked;
    private MahouAttackEntity warning;
    private ElementsEntity rift;

    MahouPhaseTwoAI(MahouTsukaiEntity boss,MahouPhaseOneAI movement){this.boss=boss;this.movement=movement;}

    void tick(LivingEntity target){
        if(attack==0){
            movement.keepDistance(target,false);
            if(--cooldown<=0&&boss.hasLineOfSight(target)){
                attack=choose();age=0;
                repeats=switch(attack){case 1->3+boss.getRandom().nextInt(3);case 2->3+boss.getRandom().nextInt(2);
                    case 3->2+boss.getRandom().nextInt(3);case 6->5+boss.getRandom().nextInt(3);default->8;};
                boss.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(attack==1||attack==3||attack==6?
                        ArkOfCosmos.ARK_OF_THE_ELEMENTS.get():ArkOfCosmos.GALAXIA.get()));
            }
            return;
        }
        int tick=age++;
        movement.face(target.getBoundingBox().getCenter());
        switch(attack){
            case 1->{
                int cycle=tick%44;
                if(cycle==0){
                    locked=frame(target);dash=locked.forward().multiply(1,0,1).normalize();
                    warning=MahouAttackEntity.spawn(boss,MahouAttackEntity.WARNING,boss.getBoundingBox().getCenter(),locked.forward().scale(32),0,12,0);
                }
                if(cycle<12){movement.keepDistance(target,false);warning.pointWarning(boss.getBoundingBox().getCenter(),locked.forward());}
                else{
                    boss.setDeltaMovement(Vec3.ZERO);
                    if(cycle==12){clearWarning();rift=ElementsEntity.bossRift(boss,locked);}
                    if(cycle>=21&&cycle<=35){
                        boss.move(MoverType.SELF,dash.scale(1.25));boss.hasImpulse=true;
                    }
                    movement.keepAltitude(target);
                }
                if(tick>=repeats*44-1)finish();
            }
            case 2->{
                int cycle=tick%32;
                if(cycle==0){
                    double angle=boss.getRandom().nextDouble()*Math.PI*2;
                    destination=new Vec3(Math.cos(angle)*3,0,Math.sin(angle)*3);positioningTicks=0;
                }
                Vec3 above=target.position().add(destination.x,target.getBbHeight()+14,destination.z);
                movement.steer(above,1.2);
                if(cycle==16){
                    if((boss.getY()<target.getBoundingBox().maxY+12||boss.position().subtract(target.position()).multiply(1,0,1).length()>6)&&positioningTicks++<40){age--;break;}
                    AttackFrame f=frame(target);
                    for(int i=0;i<8;i++){
                        double x=binomial()*.08,y=binomial()*.08;
                        Vec3 direction=f.forward().add(f.right().scale(x)).add(f.normal().scale(y)).normalize();
                        bolt(direction,.5,1);
                    }
                    sound();
                }
                if(tick>=repeats*32-1)finish();
            }
            case 3->{
                movement.keepDistance(target,false);
                if(tick<repeats*20&&tick%20==10)ElementsEntity.bossStar(boss,frame(target));
                if(tick>=repeats*20+16)finish();
            }
            case 4->{
                boss.setDeltaMovement(Vec3.ZERO);
                Vec3 direction=frame(target).forward(),origin=boss.getBoundingBox().getCenter().add(direction.scale(2.3));
                if(tick==0)warning=MahouAttackEntity.spawn(boss,MahouAttackEntity.CASTING,origin,direction.scale(52),20,21,0);
                if(tick<20)warning.pointWarning(origin,direction);
                if(tick==20)clearWarning();
                if(tick>=20&&tick<80){bolt(direction,.5,1);if(tick%4==0)sound();}
                if(tick>=80)finish();
            }
            case 5->{
                if(tick==0){
                    Vec3 side=AttackFrame.capture(boss).right().scale(boss.getRandom().nextBoolean()?14:-14);
                    destination=side;
                }
                Vec3 at=target.position().add(destination);
                movement.steer(new Vec3(at.x,movement.combatHeight(at,target),at.z),.9);
                if(tick>=20&&(tick-20)%10==0){bolt(frame(target).forward(),.15,1.025);sound();}
                if(tick>=20+repeats*10-1)finish();
            }
            case 6->{
                if(!positioned){
                    if(tick==0){
                        Vec3 away=boss.position().subtract(target.position()).multiply(1,0,1).normalize();
                        if(away.lengthSqr()<.1)away=AttackFrame.capture(boss).forward().multiply(-1,0,-1).normalize();
                        if(away.lengthSqr()<.1)away=new Vec3(1,0,0);
                        destination=away.scale(24);
                    }
                    Vec3 at=target.position().add(destination);
                    movement.steer(new Vec3(at.x,movement.combatHeight(at,target),at.z),1.5);
                    double distance=boss.position().subtract(target.position()).multiply(1,0,1).length();
                    if(Math.abs(distance-24)>2&&tick<40)return;
                    positioned=true;age=0;boss.setDeltaMovement(Vec3.ZERO);return;
                }
                boss.setDeltaMovement(Vec3.ZERO);
                if(tick%16==10){
                    AttackFrame f=frame(target);
                    for(int i=0;i<5;i++){
                        Vec3 direction=f.direction((i-2)*Math.PI/6);
                        ElementsEntity.bossFanNeedle(boss,GalaxiaEntity.aimFrame(direction,f.right()));
                    }
                    ArkOfCosmos.sound(boss,"item71",1);
                }
                if(tick>=repeats*16-1)finish();
            }
        }
    }
    private AttackFrame frame(LivingEntity target){
        return GalaxiaEntity.aimFrame(target.getBoundingBox().getCenter().subtract(boss.getBoundingBox().getCenter()).normalize(),AttackFrame.capture(boss).right());
    }
    // Sum of eight independent Bernoulli trials: a true centered binomial spread, not uniform jitter.
    private int binomial(){int value=-4;for(int i=0;i<8;i++)if(boss.getRandom().nextBoolean())value++;return value;}
    private void bolt(Vec3 direction,double speed,double acceleration){GalaxiaEntity.bossBolt(boss,boss.getBoundingBox().getCenter(),direction,speed,acceleration);}
    private void sound(){ArkOfCosmos.sound(boss,"galaxia_lightning",1);}
    private int choose(){
        int min=java.util.Arrays.stream(uses).min().orElse(0);double sum=0;
        for(int i=0;i<6;i++)if(i+1!=lastAttack)sum+=1d/Math.pow(1d+uses[i]-min,2);
        double choice=boss.getRandom().nextDouble()*sum;int selected=1;
        for(int i=0;i<6;i++)if(i+1!=lastAttack){selected=i+1;choice-=1d/Math.pow(1d+uses[i]-min,2);if(choice<=0)break;}
        if(min>1000)for(int i=0;i<6;i++)uses[i]-=min;
        uses[selected-1]++;lastAttack=selected;return selected;
    }
    private void clearWarning(){if(warning!=null)warning.discard();warning=null;}
    private void finish(){reset();cooldown=12+boss.getRandom().nextInt(11);}
    void cancel(){if(attack!=0)reset();}
    void reset(){
        attack=age=positioningTicks=0;positioned=false;destination=null;clearWarning();
        if(rift!=null)rift.discard();rift=null;
        boss.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        boss.setDeltaMovement(Vec3.ZERO);
    }
}
