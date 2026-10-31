package net.everla.projectc.arkofcosmos.entity;

import com.mojang.authlib.GameProfile;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.everla.projectc.arkofcosmos.combat.WeaponDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouTransitionGameTests {
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_transition")
    public static void shieldHealthAndFifteenSecondRain(GameTestHelper h){
        var level=h.getLevel();Vec3 origin=h.absoluteVec(new Vec3(0,120,0));
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)level.setBlock(BlockPos.containing(origin.add(x,-1,z)),Blocks.STONE.defaultBlockState(),3);
        var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setPos(origin.add(0,5,0));boss.setNoAi(true);level.addFreshEntity(boss);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"MahouRain"));player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(true);player.setPos(origin.add(0,0,8));level.addNewPlayer(player);
        var restored=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);
        try{
            h.assertTrue(Math.abs(boss.totalHealthProgress()-1)<1e-6,"Total health must start at one");
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,499);boss.aiStep();
            float phaseOneEnd=boss.totalHealthProgress();
            h.assertTrue(Math.abs(phaseOneEnd-1750/2250f)<1e-6,"Phase one must consume exactly the first 500-point pool");
            while(boss.phase()==1)boss.aiStep();
            h.assertTrue(boss.phaseTransitionTicks()==300&&Math.abs(boss.totalHealthProgress()-phaseOneEnd)<1e-6,"Phase refill must not slow the shield pulse, and transition must last 300 ticks");
            boss.setNoAi(false);boss.setTarget(player);boss.aiStep();
            var drops=level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(60),e->e.kind()==MahouAttackEntity.RAIN);
            h.assertTrue(drops.size()==4,"Transition must summon four warned rain drops every ten ticks");
            CompoundTag saved=new CompoundTag();boss.saveWithoutId(saved);restored.load(saved);
            h.assertTrue(restored.phaseTransitionTicks()==299&&Math.abs(restored.totalHealthProgress()-boss.totalHealthProgress())<1e-6,"Save/load must preserve remaining invulnerability and total health");
            float health=boss.getHealth();
            h.assertTrue(!boss.hurt(boss.damageSources().generic(),100),"Transition must reject ordinary damage");
            WeaponDamage.hurt(boss,boss.damageSources().generic(),100);ArkDamage.hurt(boss,boss.damageSources().generic(),100);
            h.assertTrue(boss.getHealth()==health,"Weapon/Ark damage must not bypass transition invulnerability");
            for(int i=0;i<20;i++)for(var drop:drops)drop.tick();
            h.assertTrue(level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()).isEmpty()
                    &&level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==ElementsEntity.NEEDLE).isEmpty(),"Rain must not fire before twenty warning ticks");
            for(var drop:drops)drop.tick();
            int rainCount=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()).size()
                    +level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==ElementsEntity.NEEDLE).size();
            h.assertTrue(rainCount==4,"Each warning must release exactly one real weapon projectile");
            var bolt=GalaxiaEntity.rainBolt(boss,origin.add(-8,14,0));var needle=ElementsEntity.rainNeedle(boss,origin.add(8,14,0));
            for(int i=0;i<18;i++){if(!bolt.isRemoved())bolt.tick();if(!needle.isRemoved())needle.tick();}
            h.assertTrue(bolt.isRemoved()&&needle.isRemoved(),"Both rain types must stop at terrain instead of passing through the floor");
            for(int i=0;i<298;i++)boss.aiStep();
            h.assertTrue(boss.phaseTransitionTicks()==1&&boss.getHealth()==health,"Invulnerability must last until the last transition tick");
            h.assertTrue(level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==ElementsEntity.BLAST).isEmpty(),"Normal phase-two attacks must remain paused");
            boss.aiStep();h.assertTrue(boss.phaseTransitionTicks()==0,"Transition must end after exactly fifteen seconds");
            boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(player),10);h.assertTrue(boss.getHealth()<health,"Normal damage must resume after transition");
            boss.setNoAi(true);net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,249);boss.aiStep();float phaseTwoEnd=boss.totalHealthProgress();
            while(boss.phase()==2)boss.aiStep();
            h.assertTrue(Math.abs(phaseTwoEnd-1000/2250f)<1e-6&&Math.abs(boss.totalHealthProgress()-phaseTwoEnd)<1e-6,"Second recovery must preserve total remaining battle health");
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,1);h.assertTrue(boss.totalHealthProgress()<.01f,"Depleted shield must receive total health below one percent");
            h.succeed();
        }finally{
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(120)))e.discard();
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            restored.discard();player.discard();boss.discard();
        }
    }
}
