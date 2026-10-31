package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Compact phase-three scheduler; attacks remain server-owned ephemeral entities. */
final class MahouPhaseThreeAI {
    private final MahouTsukaiEntity boss; private final MahouPhaseOneAI movement;
    private int attack, age, cooldown=20, repeats;
    private MahouAttackEntity vortex;
    MahouPhaseThreeAI(MahouTsukaiEntity b, MahouPhaseOneAI m){boss=b;movement=m;}
    void tick(LivingEntity target){
        if(vortex==null||vortex.isRemoved()) vortex=MahouAttackEntity.voidVortex(boss,boss.position().add(0,5,15));
        if(attack==0){ movement.keepDistance(target,false); if(--cooldown<=0){attack=1+boss.getRandom().nextInt(6); age=0; repeats=attack==1?3+boss.getRandom().nextInt(3):attack==5?3+boss.getRandom().nextInt(2):1;} return; }
        movement.face(target.getBoundingBox().getCenter()); int t=age++;
        switch(attack){
            case 1 -> { movement.steer(target.position().add(0,10,0),1.1); if(t%22==10) MahouAttackEntity.spawn(boss,MahouAttackEntity.WARNING,boss.position(),target.position().subtract(boss.position()).normalize().scale(28),0,14,0); if(t%22==14) MahouAttackEntity.spawn(boss,MahouAttackEntity.PILLAR,boss.position(),target.getBoundingBox().getCenter().subtract(boss.position()).normalize().scale(1.5),8,55,14); if(t>=repeats*22)finish(); }
            case 2 -> { if(t==1)MahouAttackEntity.spawn(boss,MahouAttackEntity.CASTING,boss.position().add(0,1,0),Vec3.ZERO,30,65,0); if(t==32)MahouAttackEntity.spawn(boss,MahouAttackEntity.GRAVITY,target.position(),Vec3.ZERO,0,45,16); if(t>=70)finish(); }
            case 3 -> { movement.steer(new Vec3(boss.getX(),boss.getY(),boss.getZ()),.8); if(t==20)MahouAttackEntity.spawn(boss,MahouAttackEntity.WAVE,boss.position(),Vec3.ZERO,0,55,24); if(t==25)MahouAttackEntity.spawn(boss,MahouAttackEntity.BLOOM,boss.position().add(0,18,0),Vec3.ZERO,20,65,32); if(t>=90)finish(); }
            case 4 -> { movement.keepDistance(target,false); if(t==20)MahouAttackEntity.spawn(boss,MahouAttackEntity.CASTING,boss.position().add(0,8,0),Vec3.ZERO,30,100,0); if(t%18==40%18)MahouAttackEntity.spawn(boss,MahouAttackEntity.BLOOM,target.position().add(0,12,0),Vec3.ZERO,16,50,20); if(t>=100)finish(); }
            case 5 -> { Vec3 d=target.position().subtract(boss.position()).normalize(); if(t<24)boss.move(MoverType.SELF,d.scale(.35)); if(t%28==18)MahouAttackEntity.spawn(boss,MahouAttackEntity.GRAVITY,boss.position().add(d.scale(2)),Vec3.ZERO,0,20,18); if(t>=repeats*28)finish(); }
            case 6 -> { if(t==1)MahouAttackEntity.spawn(boss,MahouAttackEntity.DOMAIN,boss.position(),Vec3.ZERO,20,120,18); if(t%8==0)MahouAttackEntity.spawn(boss,MahouAttackEntity.DOMAIN_TEXT,boss.position().add(0,3,0),target.position().add(0,1,0).subtract(boss.position()).normalize().scale(.55),0,55,10); if(t>=130)finish(); }
        }
    }
    void reset(){attack=age=0;cooldown=20;if(vortex!=null)vortex.discard();vortex=null;}
    void cancel(){reset();}
    private void finish(){attack=0;age=0;cooldown=20;}
}
