package net.everla.projectc.arkofcosmos.entity;

import com.mojang.authlib.GameProfile;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouPhaseOneGameTests {
    private static void weapons(MahouTsukaiEntity boss){
        var area=boss.getBoundingBox().inflate(200);
        for(var e:boss.level().getEntitiesOfClass(ElementsEntity.class,area,e->e.ownerId()==boss.getId()))e.tick();
        for(var e:boss.level().getEntitiesOfClass(GalaxiaEntity.class,area,e->e.ownerId()==boss.getId()))e.tick();
    }
    private static void set(Object object,String name,int value)throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.setInt(object,value);
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_ai")
    public static void targetingTelegraphsAndCover(GameTestHelper h)throws Exception {
        var level=h.getLevel();Vec3 origin=h.absoluteVec(new Vec3(0,120,0));
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)level.setBlock(BlockPos.containing(origin.add(x,-1,z)),Blocks.STONE.defaultBlockState(),3);
        for(int x=-18;x<=18;x++)for(int y=0;y<18;y++)for(int z=-18;z<=18;z++)level.setBlock(BlockPos.containing(origin.add(x,y,z)),Blocks.AIR.defaultBlockState(),3);
        MahouTsukaiEntity boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setPos(origin.add(0,2,0));level.addFreshEntity(boss);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"MahouAI"));player.setGameMode(GameType.SURVIVAL);player.setPos(origin.add(0,0,12));level.addNewPlayer(player);
        var cow=EntityType.COW.create(level);cow.setPos(origin.add(10,0,0));cow.setNoAi(true);level.addFreshEntity(cow);
        var ai=new MahouPhaseOneAI(boss);
        try {
            h.assertTrue(boss instanceof Enemy,"Boss must pass native hostile-entity filter");
            boss.setLastHurtByMob(cow);ai.tick();h.assertTrue(boss.getTarget()==player,"Player priority: players="+level.players().size()+", valid="+MahouPhaseOneAI.valid(boss,player)+", LOS="+boss.hasLineOfSight(player)+", eyes="+boss.getEyePosition()+" / "+player.getEyePosition()+", block="+level.clip(new net.minecraft.world.level.ClipContext(boss.getEyePosition(),player.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss)).getBlockPos());
            player.setGameMode(GameType.CREATIVE);ai.tick();h.assertTrue(boss.getTarget()==cow,"No valid player: retaliate against attacker");
            boss.setLastHurtByMob(null);boss.setTarget(null);ai.tick();h.assertTrue(boss.getTarget()==null&&boss.isNoGravity(),"Idle must hover without attacking passive mobs");
            Vec3 moving=boss.getDeltaMovement().multiply(1,0,1).normalize();
            Vec3 looking=boss.getLookAngle().multiply(1,0,1).normalize();
            h.assertTrue(looking.dot(moving)>.999&&Math.abs(boss.yHeadRot-boss.getYRot())<.001,"Roaming head/body must face travel direction");
            var patrolField=MahouPhaseOneAI.class.getDeclaredField("wander");patrolField.setAccessible(true);
            Vec3 patrolPoint=(Vec3)patrolField.get(ai);
            for(int i=0;i<30;i++)ai.tick();
            h.assertTrue(patrolPoint.equals(patrolField.get(ai)),"Patrol waypoint must remain stable instead of jittering every few ticks");
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).length()<=.101,"Patrol must use a calm speed");
            var warden=EntityType.WARDEN.create(level);warden.setNoAi(true);warden.setPos(origin.add(6,0,0));level.addFreshEntity(warden);
            ai.tick();h.assertTrue(boss.getTarget()==warden,"Debug targeting must proactively acquire wardens");
            player.setGameMode(GameType.SURVIVAL);ai.tick();h.assertTrue(boss.getTarget()==player,"Player must retain priority over warden");warden.discard();
            Vec3 water=origin.add(-10,0,-10);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<5;y++)level.setBlock(BlockPos.containing(water.add(x,y,z)),Blocks.WATER.defaultBlockState(),3);
            boss.setPos(water.add(0,1,0));
            var hover=MahouPhaseOneAI.class.getDeclaredMethod("hoverGround",Vec3.class);hover.setAccessible(true);
            h.assertTrue((double)hover.invoke(ai,water)>water.y+4,"Hover height must find water surface even from underwater");
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<5;y++)level.setBlock(BlockPos.containing(water.add(x,y,z)),Blocks.AIR.defaultBlockState(),3);
            // More than 64 blocks above the platform, both downward probes must miss.
            player.setGameMode(GameType.SURVIVAL);player.setPos(origin.add(0,80,12));
            boss.setPos(origin.add(0,90,0));set(ai,"attack",0);set(ai,"cooldown",1000);
            ai.tick();h.assertTrue(boss.getDeltaMovement().y<0,"Over void, combat hover must descend toward target altitude");
            for(int i=0;i<50;i++){boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());ai.tick();}
            h.assertTrue(Math.abs(boss.getY()-player.getBoundingBox().maxY-2)<.01,"Over void, hover must converge above target");
            player.setPos(origin.add(0,86,12));ai.tick();
            h.assertTrue(boss.getDeltaMovement().y>0,"Over void, combat hover must follow a rising target");
            set(ai,"attack",2);set(ai,"age",0);double voidY=boss.getY();
            for(int i=0;i<18;i++){ai.tick();boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());}
            h.assertTrue(Math.abs(boss.getY()-voidY)<.01,"Without a landing surface, gravity cast must not sink indefinitely");
            // Target stands on a ledge; boss is outside it with a canyon floor below.
            player.setPos(origin.add(12,0,0));boss.setPos(origin.add(22,8,0));
            level.setBlock(BlockPos.containing(origin.add(22,-12,0)),Blocks.STONE.defaultBlockState(),3);
            set(ai,"attack",0);set(ai,"cooldown",1000);ai.tick();
            h.assertTrue(boss.getDeltaMovement().y<0,"Canyon hover must converge toward the ledge target, not retain excess altitude");
            set(ai,"attack",2);set(ai,"age",0);ai.tick();
            h.assertTrue(boss.getDeltaMovement().x<0,"Gravity landing must head toward target ledge instead of canyon bottom");
            for(int i=0;i<45;i++){
                boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());ai.tick();
                h.assertTrue(boss.getY()>=origin.y-.01,"Gravity landing must not sink below the target ledge");
            }
            level.setBlock(BlockPos.containing(origin.add(22,-12,0)),Blocks.AIR.defaultBlockState(),3);
            player.setPos(origin.add(0,0,12));
            player.setGameMode(GameType.SURVIVAL);boss.setPos(origin.add(0,2,0));
            set(ai,"attack",4);set(ai,"age",0);
            ai.tick();h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).dot(player.position().subtract(boss.position()).multiply(1,0,1))<0,"Polaris must retreat before charge");
            set(ai,"age",27);
            Vec3 start=boss.position();
            cow.setPos(boss.getBoundingBox().getCenter().add(0,0,5));cow.setHealth(cow.getMaxHealth());cow.invulnerableTime=0;
            for(int i=0;i<14;i++){ai.tick();weapons(boss);}
            h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"Boss Galaxia charge must never damage nearby targets");
            h.assertTrue(boss.position().distanceToSqr(start)<.001,"Dash started before 14-tick charge");
            var warning=level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(30),e->e.kind()==MahouAttackEntity.WARNING).get(0);
            h.assertTrue(warning.view().getInt("Life")>=14,"Warning must cover the .7-second charge");
            h.assertTrue(net.everla.projectc.arkofcosmos.item.GalaxiaItem.mode(boss.getMainHandItem())==2,"Dash must use Galaxia Polaris mode");
            Vec3 locked=MahouAttackEntity.velocity(warning.view()).normalize();player.setPos(origin.add(12,0,0));ai.tick();weapons(boss);
            var star=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==GalaxiaEntity.STAR).get(0);
            cow.setPos(star.position());cow.invulnerableTime=0;star.tick();
            h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"Released Polaris star must remain harmless before actual dash movement");
            cow.setPos(origin.add(10,0,0));weapons(boss);ai.tick();weapons(boss);
            h.assertTrue(boss.position().subtract(start).normalize().dot(locked)>.95&&Math.abs(boss.getX()-start.x)<.01,"Dash must chase the released star, not the moved player");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(80)))e.discard();
            boss.setPos(origin);cow.setPos(origin.add(10,0,0));cow.setHealth(cow.getMaxHealth());
            var exposed=EntityType.COW.create(level);exposed.setPos(origin.add(0,0,10));exposed.setNoAi(true);level.addFreshEntity(exposed);
            var cover=MahouAttackEntity.spawn(boss,MahouAttackEntity.COVER,origin.add(5,12,0),new Vec3(0,-.8,0),0,150,0);
            for(int i=0;i<25;i++)cover.tick();
            h.assertTrue(cover.view().getBoolean("Landed")&&Math.abs(cover.getY()-origin.y)<.01,"Full-size pillar must land on the floor");
            h.assertTrue(Math.abs(cover.pillarBox().getXsize()-2)<.0001&&Math.abs(cover.pillarBox().getYsize()-5)<.0001&&Math.abs(cover.pillarBox().getZsize()-2)<.0001,"Pillar must be 2x5x2");
            var wave=MahouAttackEntity.spawn(boss,MahouAttackEntity.WAVE,origin.add(0,.15,0),Vec3.ZERO,2,30,8);
            for(int i=0;i<30;i++)wave.tick();
            h.assertTrue(cow.getHealth()==cow.getMaxHealth()&&exposed.getHealth()<exposed.getMaxHealth(),"Cover must protect the hidden target, but not exposed targets");
            exposed.discard();
            var bolt=GalaxiaEntity.bossBolt(boss,origin.add(0,4,0),new Vec3(0,0,1));
            for(int i=0;i<5;i++)bolt.tick();Vec3 first=bolt.position();player.setPos(origin.add(-12,0,0));bolt.tick();
            h.assertTrue(bolt.position().subtract(first).distanceToSqr(new Vec3(0,0,30/16d))<.001,"Galactic lightning must not home");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            set(ai,"attack",1);set(ai,"age",0);for(int i=0;i<37;i++)ai.tick();
            h.assertTrue(level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.PILLAR).size()==3,"First attack must summon three lunar pillars");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            boss.setPos(origin);set(ai,"attack",2);set(ai,"age",19);ai.tick();
            var triangle=level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(30),e->e.kind()==MahouAttackEntity.COVER);
            h.assertTrue(triangle.size()==3,"Gravity attack must summon three cover pillars");
            for(int i=0;i<3;i++)h.assertTrue(Math.abs(triangle.get(i).distanceToSqr(triangle.get((i+1)%3))-75)<.001,"Cover pillars must form an equilateral triangle");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            boss.setPos(origin.add(0,2,0));start=boss.position();set(ai,"attack",3);set(ai,"age",0);ai.tick();
            h.assertTrue(boss.getDeltaMovement().dot(player.position().subtract(boss.position()))<0,"Lightning must retreat before sidestepping");
            bolt.discard();
            set(ai,"age",27);ai.tick();
            h.assertTrue(Math.abs(boss.position().distanceTo(start)-16)<.001,"Sidestep must phase sixteen blocks");
            h.assertTrue(level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(40),e->e.ownerId()==boss.getId()&&e.kind()==GalaxiaEntity.BOLT).size()==1,"Lightning must fire one bolt at a time");
            for(int i=0;i<10;i++)ai.tick();
            var lightning=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(30),e->e.ownerId()==boss.getId()&&e.kind()==GalaxiaEntity.BOLT);
            h.assertTrue(lightning.size()==6&&lightning.stream().map(e->e.position()).distinct().count()==6,"Sidestep must fire six bolts from distinct path positions");
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            boss.setPos(origin.add(0,2,0));player.setPos(origin.add(0,0,8));
            set(ai,"attack",5);set(ai,"age",0);start=boss.position();ai.tick();
            h.assertTrue(level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100)).isEmpty(),"Elements must retreat before swinging");
            h.assertTrue(boss.getDeltaMovement().dot(player.position().subtract(boss.position()))<0,"Retreat must move away from target");
            java.util.Set<Integer> bladeIds=new java.util.HashSet<>();java.util.List<Integer> modes=new java.util.ArrayList<>();
            int needles=0,stars=0;java.util.Set<Integer> warnings=new java.util.HashSet<>();int firstWarningTick=-1;
            java.util.Map<Integer,Vec3> warningStarts=new java.util.HashMap<>(),warningEnds=new java.util.HashMap<>();java.util.Set<Integer> projectileIds=new java.util.HashSet<>();
            for(int tick=0;tick<140;tick++){
                boss.move(net.minecraft.world.entity.MoverType.SELF,boss.getDeltaMovement());ai.tick();
                for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.SWING_WARNING)){
                    warnings.add(e.getId());warningStarts.putIfAbsent(e.getId(),e.position());warningEnds.put(e.getId(),e.position());if(firstWarningTick<0)firstWarningTick=tick;
                }
                for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId())){
                    if(e.kind()==ElementsEntity.BLADE&&bladeIds.isEmpty())h.assertTrue(firstWarningTick>=0&&tick-firstWarningTick>=12,"First slash requires twelve full warning ticks");
                    if(e.kind()==ElementsEntity.BLADE&&bladeIds.add(e.getId()))modes.add(e.view().getInt("Mode"));
                    if(projectileIds.add(e.getId())){if(e.kind()==ElementsEntity.NEEDLE)needles++;if(e.kind()==ElementsEntity.STAR)stars++;}
                }
                weapons(boss);
                if(modes.size()==4&&level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==ElementsEntity.BLADE).isEmpty())break;
            }
            h.assertTrue(modes.equals(java.util.List.of(0,1,0,1)),"Four native swings must be 0/1/0/1, never throw: "+modes);
            h.assertTrue(warnings.size()==4,"Every slash needs a separate range warning");
            long shifted=warningStarts.entrySet().stream().filter(e->e.getValue().multiply(1,0,1).distanceTo(warningEnds.get(e.getKey()).multiply(1,0,1))>2).count();
            h.assertTrue(shifted==4,"All four warnings must move while winding up: "+shifted);
            h.assertTrue(needles==2&&stars==8,"Use the item's mode-one needle/four-star pattern, not a custom fan");
            h.assertTrue(boss.position().distanceTo(start)>5,"Boss must open distance before its first swing");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(200)))e.discard();
            boss.setPos(origin.add(0,2,0));player.setPos(origin.add(0,0,12));
            set(ai,"attack",1);set(ai,"age",0);ai.tick();
            var rock=level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(30),e->e.kind()==MahouAttackEntity.PILLAR).get(0);
            h.assertTrue(rock.position().distanceToSqr(boss.position().add(0,7,0))<.001,"Rock must spawn above boss");
            start=rock.position();for(int i=0;i<10;i++)rock.tick();
            h.assertTrue(rock.position().distanceToSqr(start)<.001,"Rock must wait exactly .5 seconds");
            var orientation=MahouAttackEntity.pillarRotation(MahouAttackEntity.velocity(rock.view()));
            var axis=new org.joml.Vector3f(0,1,0).rotate(orientation);
            h.assertTrue(new Vec3(axis.x,axis.y,axis.z).dot(MahouAttackEntity.velocity(rock.view()).normalize())>.999,"Pillar long axis must align with flight direction");
            h.assertTrue(rock.pillarBox().getZsize()>2||rock.pillarBox().getXsize()>2,"Tilted pillar collision must rotate with the model");
            rock.tick();h.assertTrue(rock.position().distanceToSqr(start)>.1,"Rock must launch after anticipation");
            for(int i=0;i<50&&!rock.isRemoved();i++)rock.tick();
            h.assertTrue(rock.isRemoved(),"Impact rock must shatter immediately");
            h.assertTrue(MahouAttackEntity.radius(18,0,18)==3,"Impact wave radius must be three blocks");
            cow.setPos(origin.add(4,0,0));cow.setHealth(cow.getMaxHealth());cow.invulnerableTime=0;
            var bloom=MahouAttackEntity.spawn(boss,MahouAttackEntity.BLOOM,cow.position(),Vec3.ZERO,14,26,2);
            for(int i=0;i<14;i++)bloom.tick();h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"Bloom must not hit during warning");
            bloom.tick();h.assertTrue(cow.getHealth()<cow.getMaxHealth(),"Bloom must strike after warning");
            cow.setHealth(cow.getMaxHealth());cow.invulnerableTime=0;
            var gravity=MahouAttackEntity.spawn(boss,MahouAttackEntity.GRAVITY,cow.position(),Vec3.ZERO,24,42,2);
            for(int i=0;i<24;i++)gravity.tick();h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"Gravity must not hit during warning");
            gravity.tick();h.assertTrue(cow.getHealth()<cow.getMaxHealth(),"Gravity must strike after warning");
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(200)))e.discard();
            set(ai,"attack",6);set(ai,"age",0);for(int i=0;i<37;i++)ai.tick();
            h.assertTrue(level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.BLOOM).size()==3,"Bloom attack must schedule three strikes");
            for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(200)))e.discard();
            boss.getRandom().setSeed(601);
            set(ai,"attack",8);set(ai,"age",0);for(int i=0;i<29;i++)ai.tick();
            h.assertTrue(level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.DOMAIN).size()==1,"Needles require a rear domain");
            var gates=level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.NEEDLE_GATE);
            h.assertTrue(gates.size()==15,"Domain must create fifteen independent random portals");
            double minY=gates.stream().mapToDouble(e->e.getY()).min().orElse(0),maxY=gates.stream().mapToDouble(e->e.getY()).max().orElse(0);
            h.assertTrue(maxY-minY>6,"Random portals must extend well above the old three rows");
            h.assertTrue(gates.stream().map(e->e.view().getInt("Delay")).distinct().count()>1,"Portal firing order must differ from appearance order");
            var gate=MahouAttackEntity.spawn(boss,MahouAttackEntity.NEEDLE_GATE,boss.position().add(0,4,0),player.position().subtract(boss.position()),4,14,4);
            for(int i=0;i<4;i++)gate.tick();
            h.assertTrue(level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==ElementsEntity.NEEDLE).isEmpty(),"Needle warning must last four complete ticks");
            gate.tick();
            h.assertTrue(level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==ElementsEntity.NEEDLE).size()==1,"Domain must fire native needle after its four-tick warning");
            set(ai,"attack",1);set(ai,"age",40);boss.setPos(origin.add(0,2,0));player.setPos(origin.add(0,0,2));
            ai.tick();h.assertTrue(boss.getDeltaMovement().dot(boss.position().subtract(player.position()).multiply(1,0,1))>0,"Casting boss must retreat from close targets");
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).length()>.9,"Close-range retreat must be substantially faster");
            player.setPos(origin.add(0,0,30));ai.tick();
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).length()>1.5,"Distant targets require fast pursuit");
            h.assertTrue(boss.getDeltaMovement().dot(player.position().subtract(boss.position()).multiply(1,0,1))>0,"Casting boss must approach distant targets");
            player.setPos(origin.add(0,0,14));ai.tick();
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).length()>.2&&Math.abs(boss.getDeltaMovement().dot(player.position().subtract(boss.position()).multiply(1,0,1)))<.001,"Comfort band must orbit rather than stand still or oscillate radially");
            player.setPos(boss.position());ai.tick();
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).lengthSqr()>.1,"Overlapping target must not trap retreat direction at zero");
            set(ai,"attack",6);set(ai,"age",20);player.setPos(origin.add(0,0,2));ai.tick();
            h.assertTrue(boss.getDeltaMovement().dot(boss.position().subtract(player.position()).multiply(1,0,1))>0,"Bloom cast must retain distance control");
            player.setPos(origin.add(0,10,14));boss.setPos(origin.add(0,2,0));
            set(ai,"attack",1);set(ai,"age",40);ai.tick();
            h.assertTrue(boss.getDeltaMovement().y>0,"Ordinary casts must seek altitude above elevated targets");
            set(ai,"attack",4);set(ai,"age",1);ai.tick();
            h.assertTrue(boss.getDeltaMovement().y>0,"Polaris charge must also try to rise above target");
            set(ai,"attack",5);set(ai,"age",0);ai.tick();
            h.assertTrue(boss.getDeltaMovement().y>0,"Elements retreat must rise above elevated targets");
            set(ai,"attack",2);set(ai,"age",1);ai.tick();
            h.assertTrue(boss.getDeltaMovement().y<0,"Cover gravity-wave attack must still descend to ground");
            h.assertTrue(boss.getDeltaMovement().multiply(1,0,1).lengthSqr()<.001,"Gravity-wave cast must hold horizontal position");
            var balanced=new MahouPhaseOneAI(boss);var choose=MahouPhaseOneAI.class.getDeclaredMethod("nextAttack");choose.setAccessible(true);
            var usesField=MahouPhaseOneAI.class.getDeclaredField("attackUses");usesField.setAccessible(true);int[] uses=(int[])usesField.get(balanced);
            java.util.Arrays.fill(uses,10);uses[7]=0;boss.getRandom().setSeed(601);int previous=0;
            for(int i=0;i<600;i++){int selected=(int)choose.invoke(balanced);h.assertTrue(selected!=previous,"Weighted selection must avoid repeats");previous=selected;}
            h.assertTrue(java.util.Arrays.stream(uses).max().orElse(0)-java.util.Arrays.stream(uses).min().orElse(0)<=8,"Weighted choices must catch up underused attacks");
            boss.setPos(origin.add(0,4,0));level.setBlock(BlockPos.containing(origin.add(0,4,2)),Blocks.STONE.defaultBlockState(),3);
            boss.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,0,4));
            h.assertTrue(Math.abs(boss.getZ()-origin.z-4)<.001,"Boss body must phase through solid blocks");
            level.setBlock(BlockPos.containing(origin.add(0,4,2)),Blocks.AIR.defaultBlockState(),3);
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            boss.setPos(origin.add(0,6,0));
            var probe=EntityType.COW.create(level);probe.setNoAi(true);probe.setPos(origin.add(0,6,5));level.addFreshEntity(probe);
            try {
                var charged=GalaxiaEntity.bossCharge(boss,boss.getBoundingBox().getCenter().add(0,0,30));
                for(int i=0;i<14;i++)charged.tick();charged.releaseBossCharge();charged.tick();
                var released=level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100),e->e.ownerId()==boss.getId()&&e.kind()==GalaxiaEntity.STAR).get(0);
                BlockPos starWall=BlockPos.containing(released.position().add(0,0,2));
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)level.setBlock(starWall.offset(x,y,0),Blocks.STONE.defaultBlockState(),3);
                released.tick();h.assertTrue(released.getZ()>starWall.getZ()+1,"Boss Polaris star must pass through terrain");
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)level.setBlock(starWall.offset(x,y,0),Blocks.AIR.defaultBlockState(),3);
                // Keep contact inside the prepared platform; a long-flying star may enter unloaded test sections.
                var velocityField=GalaxiaEntity.class.getDeclaredField("velocity");velocityField.setAccessible(true);velocityField.set(released,Vec3.ZERO);
                released.setPos(boss.getBoundingBox().getCenter().add(0,0,16));probe.setPos(released.position());released.tick();
                h.assertTrue(probe.getHealth()==probe.getMaxHealth(),"Pre-dash star must not damage on contact");
                var actualDash=GalaxiaEntity.bossDash(boss,null);
                var wheelField=GalaxiaEntity.class.getDeclaredField("wheel");wheelField.setAccessible(true);
                h.assertTrue(wheelField.get(actualDash)==released,"Damage probe must follow the released test star");
                probe.setPos(origin.add(12,10,0));
                start=boss.position();
                BlockPos dashWall=BlockPos.containing(start.add(0,0,2));level.setBlock(dashWall,Blocks.STONE.defaultBlockState(),3);
                var dashStep=GalaxiaEntity.class.getDeclaredMethod("bladeStep",net.minecraft.world.entity.LivingEntity.class);dashStep.setAccessible(true);dashStep.invoke(actualDash,boss);
                level.setBlock(dashWall,Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(boss.position().z-start.z>3,"Boss dash must cross a solid wall");
                probe.setPos(released.position());released.tick();
                h.assertTrue(probe.getHealth()<probe.getMaxHealth(),"Native damage must resume after real dash movement: removed="+released.isRemoved()+", alive="+probe.isAlive()+", star="+released.position()+", probe="+probe.position()+", health="+probe.getHealth());
            }finally{probe.discard();}
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,400);boss.aiStep();h.assertTrue(boss.isNoGravity(),"Phasing boss must not fall underground during recovery");bolt.tick();h.assertTrue(bolt.isRemoved(),"Phase recovery must cancel old attacks");
            h.succeed();
        } finally {
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(200),e->e.ownerId()==boss.getId()))e.discard();
            player.discard();cow.discard();boss.discard();
        }
    }
}
