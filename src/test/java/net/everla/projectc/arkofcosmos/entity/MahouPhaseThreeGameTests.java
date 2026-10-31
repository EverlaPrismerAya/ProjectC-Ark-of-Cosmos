package net.everla.projectc.arkofcosmos.entity;

import com.mojang.authlib.GameProfile;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;
import static net.everla.projectc.arkofcosmos.entity.MahouAttackEntity.*;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouPhaseThreeGameTests {
    private static void set(Object object,String name,int value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.setInt(object,value);}
    private static MahouTsukaiEntity boss(GameTestHelper h,Vec3 at){
        var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(h.getLevel());
        CompoundTag tag=new CompoundTag();tag.putByte("Phase",(byte)3);tag.putInt("PhaseTransitionTicks",0);tag.putFloat("Health",1000);
        boss.readAdditionalSaveData(tag);boss.setPos(at);h.getLevel().addFreshEntity(boss);return boss;
    }
    private static FakePlayer player(GameTestHelper h,Vec3 at){
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"PhaseThree")){@Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}};p.setGameMode(GameType.SURVIVAL);
        try{var field=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(p,0);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        p.setPos(at);h.getLevel().addNewPlayer(p);return p;
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_three")
    public static void sixPatternsAndFixedAim(GameTestHelper h)throws Exception{
        Vec3 at=h.absoluteVec(new Vec3(0,130,0));var boss=boss(h,at);var player=player(h,at.add(0,0,20));boss.setTarget(player);
        var ai=new MahouPhaseThreeAI(boss,new MahouPhaseOneAI(boss));
        try{
            for(int attack=1;attack<=6;attack++){
                boss.setPos(at);ai.reset();set(ai,"attack",attack);set(ai,"age",0);set(ai,"repeats",3);boss.spell(attack);
                Map<Integer,Set<Integer>> kinds=new HashMap<>();
                for(int tick=0;tick<210;tick++){
                    ai.tick(player);
                    for(var e:h.getLevel().getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(180),e->e.ownedBy(boss)))kinds.computeIfAbsent(e.kind(),k->new HashSet<>()).add(e.getId());
                    boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());
                    var field=MahouPhaseThreeAI.class.getDeclaredField("attack");field.setAccessible(true);if(field.getInt(ai)==0)break;
                }
                int expectedKind=switch(attack){case 1->RED;case 2->BLUE;case 3->FUSION;case 4->PURPLE;case 5->BLUE_DASH;default->DOMAIN_TEXT;};
                int expected=switch(attack){case 1,5->3;case 4->15;case 6->30;default->1;};
                h.assertTrue(kinds.getOrDefault(expectedKind,Set.of()).size()==expected,"Phase-three pattern "+attack+" must emit "+expected+" actual attacks");
                if(attack==1)h.assertTrue(kinds.getOrDefault(RED_WARNING,Set.of()).size()==3,"Each Red needs a warning");
                for(var e:h.getLevel().getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(200),e->e.ownedBy(boss)))e.discard();
            }
            boss.setPos(at);player.setPos(at.add(12,0,0));boss.setTarget(player);
            var small=spawn(boss,PURPLE,at,new Vec3(0,.5,0),3,50,1);
            for(int i=0;i<4;i++)small.tick();Vec3 locked=MahouAttackEntity.velocity(small.view());
            player.setPos(at.add(-12,0,0));small.tick();h.assertTrue(locked.distanceTo(MahouAttackEntity.velocity(small.view()))<1e-8,"Small Purple must aim once, never home after launch");
            CompoundTag rainTag=new CompoundTag();boss.addAdditionalSaveData(rainTag);rainTag.putInt("PhaseTransitionTicks",100);boss.readAdditionalSaveData(rainTag);
            var star=ElementsEntity.rainStar(boss,player,at.add(0,10,0));
            for(int i=0;i<20&&!star.isRemoved();i++)star.tick();
            int bolts=h.getLevel().getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==GalaxiaEntity.BOLT).size();
            h.assertTrue(bolts==5,"Phase-three rain star must split into five bolts, not five stars");
        }finally{ai.reset();boss.discard();player.discard();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_three")
    public static void shelterAndShikigami(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,160,0));var boss=boss(h,at);var inside=player(h,at.add(1,0,0));var outside=player(h,at.add(12,0,0));
        try{
            h.assertTrue(MahouPhaseOneAI.valid(boss,outside),"Outside fixture must be valid");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,boss.getBoundingBox().inflate(64)).contains(outside),"Outside fixture must be queryable");
            boss.spell(3);var vortex=voidVortex(boss,at.add(1,0,0));boss.setTarget(inside);vortex.tick();
            h.assertTrue(vortex.position().distanceTo(inside.getBoundingBox().getCenter())>=15,"Fusion vortex must leave the shelter");
            var fusion=spawn(boss,FUSION,at.add(0,14,0),Vec3.ZERO,2,10,8);for(int i=0;i<3;i++)fusion.tick();
            h.assertTrue(inside.getHealth()==inside.getMaxHealth()&&outside.getHealth()<outside.getMaxHealth(),"Fusion protects inside shield and damages outside: inside="+inside.getHealth()+", outside="+outside.getHealth()+", removed="+fusion.isRemoved()+", age="+fusion.view().getInt("Age"));
            boss.spell(0);MahouTestHealth.set(boss,501);boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(inside),10);
            h.assertTrue(boss.shikigamiAlive(),"Crossing half health must summon a Warden");float health=boss.getHealth();
            boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(inside),100);boss.setHealth(1);
            h.assertTrue(boss.getHealth()==health,"Living shikigami must block damage and direct health loss");
            var summon=h.getLevel().getEntitiesOfClass(MahoragaWardenEntity.class,boss.getBoundingBox().inflate(30)).get(0);
            h.assertTrue(!MahouPhaseOneAI.valid(boss,summon)&&!summon.canTargetEntity(boss),"Boss and shikigami must not target each other");
            CompoundTag saved=new CompoundTag();boss.addAdditionalSaveData(saved);boss.readAdditionalSaveData(saved);h.assertTrue(boss.shikigamiAlive(),"Summon protection must survive save/load");
            summon.die(summon.damageSources().playerAttack(inside));h.assertTrue(!boss.shikigamiAlive(),"Summon death must release protection");
            boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(inside),10);h.assertTrue(boss.getHealth()<health,"Boss must become vulnerable again");summon.discard();
        }finally{boss.discard();inside.discard();outside.discard();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_three")
    public static void entranceRainAndBluePull(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,170,0));var boss=boss(h,at);var target=player(h,at.add(0,0,30));boss.setTarget(target);
        try{
            CompoundTag tag=new CompoundTag();boss.addAdditionalSaveData(tag);tag.putInt("PhaseTransitionTicks",300);boss.readAdditionalSaveData(tag);boss.aiStep();
            var attacks=h.getLevel().getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.ownedBy(boss));
            h.assertTrue(boss.phaseTransitionTicks()==299&&boss.raisedHands(),"Third entrance starts fifteen-second raised-hands rain");
            h.assertTrue(attacks.stream().filter(e->e.kind()==RAIN).count()==8&&attacks.stream().filter(e->e.kind()==VOID_VORTEX).count()==1,"Third entrance must double rain density and summon exactly one vortex");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==ElementsEntity.STAR).size()==1,"Rain must spawn one star per interval");
            boss.addAdditionalSaveData(tag);tag.putInt("PhaseTransitionTicks",0);boss.readAdditionalSaveData(tag);
            Vec3 blueAt=at.add(0,0,10);var blue=spawn(boss,BLUE,blueAt,new Vec3(0,-1,0),0,100,1);
            h.getLevel().setBlock(net.minecraft.core.BlockPos.containing(blueAt.add(0,-1,0)),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            blue.tick();h.assertTrue(blue.view().getBoolean("Landed"),"Blue must land against actual terrain");
            var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(blueAt.add(8,0,0));h.getLevel().addFreshEntity(cow);blue.tick();
            h.assertTrue(cow.getDeltaMovement().x<0,"Landed Blue must pull non-player entities within ten blocks");cow.discard();
            target.setPos(blueAt.add(3,0,0));target.setDeltaMovement(Vec3.ZERO);var dash=spawn(boss,BLUE_DASH,blueAt,Vec3.ZERO,24,40,1);dash.tick();
            h.assertTrue(target.getDeltaMovement().x<0,"Dash Blue must pull players within four blocks during charge");
            float health=boss.getHealth();boss.hurt(boss.damageSources().playerAttack(target),1);h.assertTrue(boss.getHealth()<health,"Fixture leaves rain protection before normal combat");
        }finally{
            for(var e:h.getLevel().getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(200),e->e.ownedBy(boss)))e.discard();
            for(var e:h.getLevel().getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            boss.discard();target.discard();
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_three")
    public static void separateWeaponsAndWheelTurn(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,250,0));var player=player(h,at);
        var w=ArkOfCosmos.MAHORAGA.get().create(h.getLevel());w.setPos(at);w.setNoAi(true);w.setNoGravity(true);h.getLevel().addFreshEntity(w);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get()));
        var element=ElementsEntity.create(player,ElementsEntity.STAR,0,0,1,at,Vec3.ZERO,net.everla.projectc.arkofcosmos.util.AttackFrame.capture(player));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ArkOfCosmos.GALAXIA.get()));
        var galaxy=GalaxiaEntity.blade(player,0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        try{
            var elementSource=net.everla.projectc.arkofcosmos.combat.WeaponUser.source(element,player);
            var galaxySource=net.everla.projectc.arkofcosmos.combat.WeaponUser.source(galaxy,player);
            h.assertTrue(MahoragaWardenEntity.sourceKey(elementSource).equals("item:arkofcosmos:ark_of_the_elements"),"Element projectile retains its firing weapon after switching");
            h.assertTrue(MahoragaWardenEntity.sourceKey(galaxySource).equals("item:arkofcosmos:galaxia"),"Galaxia retains a separate firing weapon key");
            CompoundTag tag=new CompoundTag();w.addAdditionalSaveData(tag);var values=new CompoundTag();values.putInt(MahoragaWardenEntity.sourceKey(elementSource),5);tag.put("Adaptation",values);w.readAdditionalSaveData(tag);
            float health=w.getHealth();w.invulnerableTime=0;net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,elementSource,1);
            h.assertTrue(Math.abs(health-w.getHealth()-1)<.01,"Fully adapted Elements still takes one point after player switches weapon");health=w.getHealth();
            w.invulnerableTime=0;net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,galaxySource,1);
            h.assertTrue(w.getHealth()<health&&w.adaptation(MahoragaWardenEntity.sourceKey(galaxySource))==1,"Galaxia must deal damage and start adapting independently");
            w.handleEntityEvent((byte)61);h.assertTrue(Math.abs(w.wheelAngle(0))<.001,"Wheel starts at zero on adaptation event");
            w.tickCount+=10;h.assertTrue(Math.abs(w.wheelAngle(0)-Math.PI)<.001,"Wheel turns half a revolution at half animation time");
            w.tickCount+=10;h.assertTrue(Math.abs(w.wheelAngle(0)-Math.PI*2)<.001,"Wheel completes exactly one revolution");
        }finally{element.discard();galaxy.discard();w.discard();player.discard();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="mahou_three")
    public static void fusionLandingAndWardenTarget(GameTestHelper h)throws Exception{
        Vec3 at=h.absoluteVec(new Vec3(0,180,0));var boss=boss(h,at.add(0,75,0));var target=player(h,at);
        h.getLevel().setBlock(net.minecraft.core.BlockPos.containing(at.add(0,-1,0)),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(net.minecraft.core.BlockPos.containing(at.add(5,-4,0)),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var ai=new MahouPhaseThreeAI(boss,new MahouPhaseOneAI(boss));boss.setTarget(target);boss.spell(3);set(ai,"attack",3);
        ai.tick(target);Vec3 landed=boss.position();h.assertTrue(Math.abs(landed.y-(at.y-3))<.01&&boss.getDeltaMovement().lengthSqr()==0,"Fusion must land immediately and stop");
        target.setPos(at.add(10,0,0));ai.tick(target);h.assertTrue(boss.position().equals(landed)&&boss.getDeltaMovement().lengthSqr()==0,"Fusion must not chase player after landing");
        var summon=ArkOfCosmos.MAHORAGA.get().create(h.getLevel());summon.setPos(at);h.getLevel().addFreshEntity(summon);
        summon.customServerAiStep();
        h.assertTrue(summon.getTarget()==target,"Shikigami must proactively acquire survival player without waiting for vibrations");
        h.assertTrue(Math.abs(summon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-.23*1.5*.85)<.001,"Speed uses 85 percent of baby zombie baseline");
        h.assertTrue(summon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==20,"Melee damage must be twenty");
        ai.reset();boss.discard();summon.discard();target.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_three")
    public static void rainFollowsEscapingLockedTarget(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,130,0));var boss=boss(h,at);var target=player(h,at.add(24,0,0));
        try{
            for(int phase=1;phase<=3;phase++){
                CompoundTag tag=new CompoundTag();boss.addAdditionalSaveData(tag);tag.putByte("Phase",(byte)phase);tag.putInt("PhaseTransitionTicks",101);boss.readAdditionalSaveData(tag);
                boss.setPos(at);boss.setDeltaMovement(Vec3.ZERO);target.setPos(at.add(24,0,0));boss.setTarget(target);boss.aiStep();
                h.assertTrue(boss.position().distanceTo(at)<.01,"Target on rain boundary must not trigger teleport");
                target.setPos(at.add(80,0,0));boss.aiStep();
                h.assertTrue(Math.abs(boss.getX()-target.getX())<.01&&Math.abs(boss.getZ()-target.getZ())<.01&&Math.abs(boss.getY()-target.getBoundingBox().maxY-6)<.01,"Every rain phase must teleport above its escaping lock, even beyond acquisition range");
                h.assertTrue(boss.phaseTransitionTicks()==99&&boss.getTarget()==target,"Teleport must preserve remaining rain time and locked target");
                boss.addAdditionalSaveData(tag);tag.putInt("PhaseTransitionTicks",39);boss.readAdditionalSaveData(tag);
                target.setPos(at.add(120,0,0));boss.aiStep();
                h.assertTrue(Math.abs(boss.getX()-target.getX())<.01&&boss.phaseTransitionTicks()==38,"Teleport must still work during rain's final quiet tail");
            }
        }finally{boss.discard();target.discard();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=35,batch="mahoraga_protection")
    public static void wardenForcedInvulnerability(GameTestHelper h){
        var w=ArkOfCosmos.MAHORAGA.get().create(h.getLevel());w.setNoAi(true);w.setNoGravity(true);w.setPos(h.absoluteVec(new Vec3(0,250,0)));h.getLevel().addFreshEntity(w);
        var enemy=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());var target=player(h,w.position().add(2,0,0));
        var wolf=net.minecraft.world.entity.EntityType.WOLF.create(h.getLevel());wolf.tame(target);
        var arrow=net.minecraft.world.entity.EntityType.ARROW.create(h.getLevel());arrow.setOwner(wolf);
        var source=w.damageSources().mobAttack(enemy);
        h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(w,source,10),"Initial mob damage must apply through native adaptation");
        float health=w.getHealth();w.invulnerableTime=0;
        h.assertTrue(!net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,source,100),"Clearing vanilla immunity must not bypass the independent lock");
        w.actuallyHurt(source,100);w.setHealth(1);net.everla.projectc.arkofcosmos.combat.ArkDamage.setHealth(w,1);
        var key=net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;w.getEntityData().set(key,1f);
        h.assertTrue(w.getHealth()==health&&w.getEntityData().get(key)==health,"Actual damage, setters and synchronized health writes must all respect the lock");
        for(var owned:new net.minecraft.world.entity.Entity[]{target,wolf,arrow}){
            var ownedSource=new net.minecraft.world.damagesource.DamageSource(source.typeHolder(),owned);
            h.assertTrue(net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,ownedSource,2)&&net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,ownedSource,2),"Player, tame and projectile attacks must bypass hostile protection");
        }
        h.runAtTickTime(9,()->{
            h.assertTrue(!net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(w,source,10),"Independent protection must last through tick nine");
            CompoundTag saved=new CompoundTag();w.saveWithoutId(saved);
            h.assertTrue(saved.getInt("NonPlayerInvulnerabilityTicks")==1,"Save remaining protection precisely");w.load(saved);
        });
        h.runAtTickTime(10,()->{
            h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(w,source,10),"Non-player damage resumes at tick ten after loading");
        });
        h.runAtTickTime(20,()->{
            try{
                float before=w.getHealth();w.setHealth(before-2);w.setHealth(1);
                h.assertTrue(w.getHealth()==before-2,"Unattributed health loss must establish its own ten-tick lock");
                h.succeed();
            }finally{w.discard();enemy.discard();target.discard();wolf.discard();arrow.discard();}
        });
    }
    @GameTest(template="empty",timeoutTicks=180,batch="mahoraga_chase")
    public static void wardenActuallyChasesAndAttacks(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,180,0));
        var floor=net.minecraft.core.BlockPos.containing(at).below();
        for(int x=-3;x<=12;x++)for(int z=-4;z<=4;z++){
            h.getLevel().setBlock(floor.offset(x,0,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<=5;y++)h.getLevel().setBlock(floor.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        var target=player(h,at.add(8,0,0));
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        var summon=ArkOfCosmos.MAHORAGA.get().create(h.getLevel());summon.setPos(at);h.getLevel().addFreshEntity(summon);
        h.runAtTickTime(80,()->{
            try{
                h.assertTrue(summon.position().distanceTo(at)>3,"Warden must actually chase: pos="+summon.position()+", activity="+summon.getBrain().getActiveNonCoreActivity()+", pose="+summon.getPose()+", anger="+summon.getAngerLevel()+", target="+summon.getTarget()+", walk="+summon.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET));
            }catch(Throwable failure){summon.discard();target.discard();throw failure;}
        });
        h.runAtTickTime(160,()->{
            try{h.assertTrue(target.getHealth()<200,"Warden must deal actual melee damage after chasing");h.succeed();}
            finally{summon.discard();target.discard();}
        });
    }
    @GameTest(template="empty",timeoutTicks=250,batch="mahou_three_adaptation")
    public static void adaptationAndWeaponFloor(GameTestHelper h){
        Vec3 at=h.absoluteVec(new Vec3(0,250,0));var w=ArkOfCosmos.MAHORAGA.get().create(h.getLevel());w.setPos(at);w.setNoAi(true);w.setNoGravity(true);h.getLevel().addFreshEntity(w);var player=player(h,at.add(2,0,0));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        var source=w.damageSources().playerAttack(player);String key=MahoragaWardenEntity.sourceKey(source);
        for(int i=0;i<5;i++){int stage=i;h.runAtTickTime(1+i*50,()->{
            w.invulnerableTime=0;float health=w.getHealth();net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,source,10);
            h.assertTrue(w.adaptation(key)==stage+1,"One adaptation per eligible 50-tick interval: stage="+stage+", layers="+w.adaptation(key)+", before="+health+", after="+w.getHealth()+", alive="+w.isAlive()+", time="+w.level().getGameTime());
            h.assertTrue(Math.abs((health-w.getHealth())-(10-stage*2))<.01,"Weapon minimum must respect adaptation");
            w.invulnerableTime=0;w.hurt(source,1);h.assertTrue(w.adaptation(key)==stage+1,"Same-tick hits cannot learn twice");
        });}
        h.runAtTickTime(50,()->{
            w.invulnerableTime=0;w.hurt(source,1);
            h.assertTrue(w.adaptation(key)==1,"At 49 elapsed ticks the 2.5-second cooldown must still block learning");
            CompoundTag saved=new CompoundTag();w.addAdditionalSaveData(saved);
            h.assertTrue(saved.getInt("LearnCooldown")==1,"Remaining adaptation cooldown must be saved exactly");
            w.readAdditionalSaveData(saved);
        });
        h.runAtTickTime(225,()->{
            float health=w.getHealth();w.invulnerableTime=0;net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(w,source,100);
            h.assertTrue(Math.abs(health-w.getHealth()-1)<.01,"Five adaptations must retain exactly one point of weapon damage");
            w.invulnerableTime=0;health=w.getHealth();w.hurt(source,100);
            h.assertTrue(Math.abs(health-w.getHealth()-1)<.01,"Fully adapted ordinary attacks also retain exactly one point");
            CompoundTag tag=new CompoundTag();w.addAdditionalSaveData(tag);w.readAdditionalSaveData(tag);h.assertTrue(w.adaptation(key)==5,"Adaptation survives save/load");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
            w.invulnerableTime=0;w.hurt(w.damageSources().playerAttack(player),1);h.assertTrue(w.getHealth()<health,"Different registered weapon remains usable");
            CompoundTag capped=new CompoundTag();w.addAdditionalSaveData(capped);var values=new CompoundTag();
            for(int i=0;i<20;i++)values.putInt("damage:test_"+i,1);capped.put("Adaptation",values);capped.putInt("LearnCooldown",0);w.readAdditionalSaveData(capped);
            w.invulnerableTime=0;w.hurt(w.damageSources().playerAttack(player),1);
            h.assertTrue(w.adaptation("item:minecraft:iron_axe")==0,"Additional sources cannot exceed the twenty-type cap");
            w.discard();player.discard();h.succeed();
        });
    }
}
