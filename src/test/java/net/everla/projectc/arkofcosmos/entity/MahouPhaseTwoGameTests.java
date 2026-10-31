package net.everla.projectc.arkofcosmos.entity;

import com.mojang.authlib.GameProfile;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouPhaseTwoGameTests {
    private static void set(Object object,String name,int value)throws Exception{
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.setInt(object,value);
    }
    private static void phase(MahouTsukaiEntity boss,int phase){
        var tag=new net.minecraft.nbt.CompoundTag();boss.addAdditionalSaveData(tag);tag.putByte("Phase",(byte)phase);boss.readAdditionalSaveData(tag);
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_phase_two")
    public static void patternsAndSharedMovement(GameTestHelper h)throws Exception{
        var level=h.getLevel();Vec3 origin=h.absoluteVec(new Vec3(0,120,0));
        var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setPos(origin);level.addFreshEntity(boss);phase(boss,2);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"MahouPhaseTwo"));
        player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(true);player.setPos(origin.add(0,0,14));level.addNewPlayer(player);boss.setTarget(player);
        var shared=new MahouPhaseOneAI(boss);var ai=new MahouPhaseTwoAI(boss,shared);
        try{
            for(int stage:new int[]{2,3}){
                phase(boss,stage);boss.setPos(origin);player.setPos(origin.add(0,8,2));shared.tick();
                h.assertTrue(boss.getDeltaMovement().y>0&&boss.getDeltaMovement().z<0,"Later phases must rise and retreat from close elevated target");
                player.setPos(origin.add(0,0,30));shared.tick();h.assertTrue(boss.getDeltaMovement().z>0,"Later phases must pursue distant targets");
                player.setGameMode(GameType.CREATIVE);boss.setTarget(null);shared.tick();
                h.assertTrue(boss.getTarget()==null&&boss.getDeltaMovement().multiply(1,0,1).length()<=.101,"Later phases must patrol calmly without an enemy");
                player.setGameMode(GameType.SURVIVAL);
            }
            phase(boss,2);player.setPos(origin.add(0,0,14));boss.setTarget(player);
            for(int attack=1;attack<=6;attack++){
                ai.reset();boss.setPos(origin.add(0,5,0));set(ai,"attack",attack);set(ai,"age",0);set(ai,"repeats",attack==3?2:attack==6?5:3);
                int stitches=0;
                java.util.List<Vec3> fan=new ArrayList<>();
                Set<Integer> casting=new HashSet<>();
                Set<Integer> bolts=new HashSet<>(),stars=new HashSet<>(),needles=new HashSet<>(),rifts=new HashSet<>(),warnings=new HashSet<>();
                for(int tick=0;tick<230;tick++){
                    ai.tick(player);
                    for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100))){
                        if(e.kind()==MahouAttackEntity.WARNING)warnings.add(e.getId());
                        if(e.kind()==MahouAttackEntity.CASTING)casting.add(e.getId());
                    }
                    for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId())){
                        if(e.kind()==GalaxiaEntity.BOLT&&bolts.add(e.getId())){
                            if(attack==2)h.assertTrue(boss.getY()>=player.getBoundingBox().maxY+12,"Overhead scatter must gain substantial height before firing");
                            if(attack==4)h.assertTrue(tick>=20&&!casting.isEmpty(),"Machine gun must show its casting circle for a full second before firing");
                        }
                    }
                    for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId())){
                        if(e.kind()==ElementsEntity.STAR)stars.add(e.getId());
                        if(e.kind()==ElementsEntity.NEEDLE&&needles.add(e.getId())){
                            if(fan.size()<5)fan.add(ElementsEntity.vector(e.view(),"Velocity").normalize());
                            if(attack==6)h.assertTrue(boss.position().subtract(player.position()).multiply(1,0,1).length()>=22&&boss.getDeltaMovement().lengthSqr()<1e-8,"Needle fans must retreat before firing from a stationary position");
                        }
                        if(e.kind()==ElementsEntity.BLAST){rifts.add(e.getId());stitches=Math.max(stitches,e.view().getInt("Stitches"));}
                        e.tick();
                    }
                    boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());
                    var field=MahouPhaseTwoAI.class.getDeclaredField("attack");field.setAccessible(true);if(field.getInt(ai)==0)break;
                }
                switch(attack){
                    case 1->h.assertTrue(rifts.size()==3&&warnings.size()==3&&stitches==8&&boss.position().distanceTo(origin)>8,"Rift combo must produce native full blasts, warned dashes and movement");
                    case 2->h.assertTrue(bolts.size()==24,"Overhead volleys must emit eight bolts per volley");
                    case 3->h.assertTrue(stars.size()==2&&bolts.size()==16,"Each homing element star must burst into eight bolts: stars="+stars.size()+", bolts="+bolts.size()+", target="+boss.getTarget()+", playerAlive="+player.isAlive());
                    case 4->h.assertTrue(bolts.size()==60&&casting.size()==1,"Rapid fire must emit exactly sixty bolts");
                    case 5->h.assertTrue(bolts.size()==3,"Side attack must emit one accelerating bolt each half second");
                    case 6->h.assertTrue(needles.size()==25,"Fan attack must emit five evenly spaced needles in each of five volleys");
                }
                if(attack==6){
                    Vec3 normal=fan.get(0).cross(fan.get(1)).normalize();
                    for(int i=0;i<5;i++){
                        h.assertTrue(Math.abs(fan.get(i).dot(normal))<1e-6,"Needle fan must remain in a single aim plane");
                        if(i>0)h.assertTrue(Math.abs(fan.get(i-1).dot(fan.get(i))-Math.cos(Math.PI/6))<1e-6,"Needle fan must use equal angular spacing");
                    }
                }
                for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(250)))e.discard();
                for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(250)))e.discard();
                for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(250)))e.discard();
            }
            boss.setPos(origin);player.setPos(origin.add(0,0,14));boss.setTarget(player);
            var fanProbe=ElementsEntity.bossFanNeedle(boss,new AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1)));
            h.assertTrue(Math.abs(fanProbe.view().getFloat("Scale")-.6f)<1e-6,"Boss needle must use quarter-size visuals");
            Vec3 fanStart=fanProbe.position();for(int i=0;i<42&&!fanProbe.isRemoved();i++)fanProbe.tick();
            h.assertTrue(fanProbe.position().distanceTo(fanStart)>26,"Slow needle fan must reach the increased twenty-four-block cast distance");fanProbe.discard();
            var homing=ElementsEntity.bossStar(boss,new AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1)));
            homing.tick();
            h.assertTrue(ElementsEntity.vector(homing.view(),"Velocity").z>0,"Burst stars must turn slowly toward current target");homing.discard();
            player.setPos(origin.add(0,0,2));
            var contact=ElementsEntity.bossStar(boss,new AttackFrame(new Vec3(0,0,1),new Vec3(1,0,0)));
            contact.setPos(player.getBoundingBox().getCenter());
            int before=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)).size();
            contact.tick();
            h.assertTrue(contact.isRemoved(),"Star must burst immediately on contact even against invulnerable target");
            var burst=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100));
            h.assertTrue(burst.size()==before+8,"Contact burst must create exactly eight bolts");
            for(var bolt:burst)h.assertTrue(GalaxiaEntity.vector(bolt.view(),"Velocity").lengthSqr()>0,"Burst directions must be nonzero");
            var accelerating=GalaxiaEntity.bossBolt(boss,origin.add(0,20,0),new Vec3(1,0,0),.15,1.025);
            accelerating.tick();Vec3 first=GalaxiaEntity.vector(accelerating.view(),"Velocity");
            player.setPos(origin.add(-12,0,0));accelerating.tick();Vec3 second=GalaxiaEntity.vector(accelerating.view(),"Velocity");
            h.assertTrue(second.length()>first.length()&&second.normalize().distanceToSqr(new Vec3(1,0,0))<1e-8,"Acceleration must preserve nonhoming direction");
            phase(boss,3);accelerating.tick();h.assertTrue(accelerating.isRemoved(),"Changing phase must cancel earlier projectiles");
            phase(boss,2);var choose=MahouPhaseTwoAI.class.getDeclaredMethod("choose");choose.setAccessible(true);
            int previous=0;for(int i=0;i<600;i++){int next=(int)choose.invoke(ai);h.assertTrue(next!=previous,"Weighted attacks must not repeat consecutively");previous=next;}
            var counts=MahouPhaseTwoAI.class.getDeclaredField("uses");counts.setAccessible(true);int[] uses=(int[])counts.get(ai);
            h.assertTrue(Arrays.stream(uses).max().orElse(0)-Arrays.stream(uses).min().orElse(0)<10,"Weighted choices must favor less-used attacks");
            h.succeed();
        }finally{
            ai.reset();
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(300),e->e.ownerId()==boss.getId()))e.discard();
            for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(300),e->e.ownerId()==boss.getId()))e.discard();
            player.discard();boss.discard();
        }
    }
}
