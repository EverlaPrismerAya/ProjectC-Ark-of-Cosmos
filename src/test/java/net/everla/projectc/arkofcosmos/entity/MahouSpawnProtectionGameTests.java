package net.everla.projectc.arkofcosmos.entity;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouSpawnProtectionGameTests {
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_spawn")
    public static void spawnRainAndThreeBoltStars(GameTestHelper h){
        var level=h.getLevel();var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);
        CompoundTag summon=new CompoundTag();summon.putString("id","arkofcosmos:mahou_tsukai");boss.load(summon);
        Vec3 at=h.absoluteVec(new Vec3(0,120,0));boss.setPos(at);level.addFreshEntity(boss);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"RainStar"));
        player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(true);player.setPos(at.add(0,0,8));level.addNewPlayer(player);
        try {
            h.assertTrue(boss.phaseTransitionTicks()==300,"Spawn must start a fifteen-second transition");
            h.assertTrue(!WeaponDamage.hurt(boss,boss.damageSources().playerAttack(player),100),"Spawn transition must reject player cooldown-bypass damage");
            boss.aiStep();
            h.assertTrue(level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==MahouAttackEntity.RAIN).size()==4,"Spawn must produce warned random rain");
            h.assertTrue(level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100)).isEmpty(),"Spawn must not include phase-two tracking stars");
            boss.setNoAi(true);for(int i=0;i<299;i++)boss.aiStep();
            h.assertTrue(boss.phaseTransitionTicks()==0,"Spawn must end after exactly 300 ticks");
            CompoundTag tag=new CompoundTag();boss.addAdditionalSaveData(tag);tag.putByte("Phase",(byte)2);tag.putInt("PhaseTransitionTicks",300);boss.readAdditionalSaveData(tag);boss.setNoAi(false);
            boss.aiStep();
            var stars=level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==ElementsEntity.STAR);
            h.assertTrue(stars.size()==1,"Phase-two transition must produce a tracking star without requiring boss target selection");
            var star=stars.get(0);Vec3 start=star.position();star.tick();
            h.assertTrue(star.position().distanceToSqr(player.position())<start.distanceToSqr(player.position()),"Rain star must track the player");
            star.setPos(player.getBoundingBox().getCenter());star.tick();
            h.assertTrue(star.isRemoved(),"Rain star must burst on contact");
            h.assertTrue(level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)).size()==3,"Rain star must split into exactly three bolts");
            for(var bolt:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)))bolt.discard();
            var timed=ElementsEntity.rainStar(boss,player,at.add(20,15,0));
            h.assertTrue(timed.view().getInt("Life")==48,"Rain star must inherit the skill star's sixteen-tick lifespan");
            for(int i=0;i<15;i++)timed.tick();
            h.assertTrue(!timed.isRemoved(),"Timed star must remain until its final tick");
            timed.tick();h.assertTrue(timed.isRemoved(),"Timed star must burst at sixteen ticks even without contact");
            h.assertTrue(level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)).size()==3,"Timed rain burst must also produce three bolts");
            player.setGameMode(GameType.CREATIVE);
            var enemy=EntityType.WARDEN.create(level);enemy.setPos(at.add(0,0,8));level.addFreshEntity(enemy);
            boss.setTarget(enemy);
            tag.putInt("PhaseTransitionTicks",280);boss.readAdditionalSaveData(tag);boss.setNoAi(false);boss.aiStep();
            var hostileStar=level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100),e->e.kind()==ElementsEntity.STAR);
            h.assertTrue(hostileStar.size()==1,"Rain must also target the boss's valid non-player opponent");
            hostileStar.get(0).tick();
            h.assertTrue(!hostileStar.get(0).isRemoved(),"Non-player tracking star must remain active");
            enemy.discard();
            h.succeed();
        } finally {
            for(var e:level.getEntitiesOfClass(MahouAttackEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            for(var e:level.getEntitiesOfClass(ElementsEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            for(var e:level.getEntitiesOfClass(GalaxiaEntity.class,boss.getBoundingBox().inflate(100)))e.discard();
            boss.discard();player.discard();
        }
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_raw_protection")
    public static void directWritesStartCooldown(GameTestHelper h){
        var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(h.getLevel());
        boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setNoAi(true);
        var key=net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;
        boss.getEntityData().set(key,990f);
        h.assertTrue(boss.getHealth()==990,"First raw hit must be accepted");
        boss.getEntityData().set(key,980f);boss.setHealth(970);
        h.assertTrue(boss.getHealth()==990&&boss.getEntityData().get(key)==990,"First raw hit must immediately establish ten-tick protection");
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"RawPlayer"));
        h.assertTrue(ArkDamage.hurt(boss,boss.damageSources().playerAttack(player),10),"Player damage must work after a raw hit");
        CompoundTag saved=new CompoundTag();boss.saveWithoutId(saved);
        var restored=ArkOfCosmos.MAHOU_TSUKAI.get().create(h.getLevel());restored.load(saved);
        h.assertTrue(restored.getHealth()==boss.getHealth(),"Save/load must preserve authoritative vitality");
        restored.setHealth(1);h.assertTrue(restored.getHealth()==boss.getHealth(),"Save/load must preserve independent cooldown");
        boss.discard();restored.discard();player.discard();h.succeed();
    }
    // Models modded servants implementing the vanilla ownership interface, without optional dependencies.
    private static final class Servant extends net.minecraft.world.entity.animal.Cow implements net.minecraft.world.entity.OwnableEntity {
        net.minecraft.world.entity.LivingEntity master;
        Servant(net.minecraft.world.level.Level level){super(EntityType.COW,level);}
        @Override public UUID getOwnerUUID(){return master==null?null:master.getUUID();}
        @Override public net.minecraft.world.entity.LivingEntity getOwner(){return master;}
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_owner_protection")
    public static void playerOwnedAttacksBypassProtection(GameTestHelper h){
        var level=h.getLevel();var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);
        boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setNoAi(true);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OwnedDamage"));level.addNewPlayer(player);
        var enemy=EntityType.COW.create(level);
        var wolf=EntityType.WOLF.create(level);wolf.tame(player);
        var servant=new Servant(level);servant.master=wolf;
        var hostileServant=new Servant(level);hostileServant.master=enemy;
        var cycle=new Servant(level);cycle.master=cycle;
        var arrow=EntityType.ARROW.create(level);arrow.setOwner(servant);
        var hostileArrow=EntityType.ARROW.create(level);hostileArrow.setOwner(hostileServant);
        var base=boss.damageSources().mobAttack(enemy).typeHolder();
        try {
            for(var attacker:new net.minecraft.world.entity.Entity[]{player,wolf,servant,arrow}){
                var source=new net.minecraft.world.damagesource.DamageSource(base,attacker);
                h.assertTrue(WeaponUser.playerOwnedDamage(source),"Player ownership chain must be recognized");
                float before=boss.getHealth();
                h.assertTrue(WeaponDamage.hurt(boss,source,2)&&WeaponDamage.hurt(boss,source,2),"Owned attacks must apply repeatedly in the same tick");
                h.assertTrue(boss.getHealth()<before,"Owned attacks must actually lower health");
            }
            h.assertTrue(WeaponDamage.hurt(boss,boss.damageSources().mobAttack(enemy),2),"Owned hits must not establish a non-player cooldown");
            for(var attacker:new net.minecraft.world.entity.Entity[]{wolf,servant,arrow}){
                var source=new net.minecraft.world.damagesource.DamageSource(base,attacker);
                h.assertTrue(WeaponDamage.hurt(boss,source,2)&&WeaponDamage.hurt(boss,source,2),"Owned attacks must bypass an existing hostile cooldown");
            }
            for(var attacker:new net.minecraft.world.entity.Entity[]{enemy,hostileServant,hostileArrow,cycle}){
                var source=new net.minecraft.world.damagesource.DamageSource(base,attacker);
                h.assertTrue(!WeaponUser.playerOwnedDamage(source)&&!WeaponDamage.hurt(boss,source,2),"Hostile ownership and cycles must not grant player exemption");
            }
            var directOnly=new net.minecraft.world.damagesource.DamageSource(base,arrow,null);
            h.assertTrue(WeaponDamage.hurt(boss,directOnly,2),"Direct projectile owner must resolve even without a causing entity");
            h.succeed();
        } finally {boss.discard();player.discard();enemy.discard();wolf.discard();servant.discard();hostileServant.discard();cycle.discard();arrow.discard();hostileArrow.discard();}
    }
    @GameTest(template="empty",timeoutTicks=30,batch="mahou_protection")
    public static void independentNonPlayerCooldown(GameTestHelper h)throws Exception{
        var level=h.getLevel();var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(level);
        boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});boss.setNoAi(true);boss.setPos(h.absoluteVec(new Vec3(0,120,0)));level.addFreshEntity(boss);
        var mob=EntityType.COW.create(level);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"PlayerBypass"));
        var mobSource=boss.damageSources().mobAttack(mob);
        h.assertTrue(ArkDamage.hurt(boss,mobSource,10),"First non-player Ark hit must apply");float health=boss.getHealth();
        boss.invulnerableTime=0;
        h.assertTrue(!ArkDamage.hurt(boss,mobSource,100),"Ark damage must not clear the independent cooldown");
        h.assertTrue(!WeaponDamage.hurt(boss,mobSource,100),"Native weapon bypass must also be blocked");
        boss.actuallyHurt(mobSource,100);boss.setHealth(1);ArkDamage.setHealth(boss,1);
        var key=net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;
        boss.getEntityData().set(key,1f);
        h.assertTrue(boss.getEntityData().get(key)==health,"Raw synchronized health setter must be repaired immediately");
        var getter=net.minecraft.network.syncher.SynchedEntityData.class.getDeclaredMethod("getItem",net.minecraft.network.syncher.EntityDataAccessor.class);
        getter.setAccessible(true);Object item=getter.invoke(boss.getEntityData(),key);
        // Replay a helper's direct DataItem write plus explicit entity callback, without hurt/setHealth.
        @SuppressWarnings("unchecked") var raw=(net.minecraft.network.syncher.SynchedEntityData.DataItem<Float>)(Object)item;
        raw.setValue(Float.NEGATIVE_INFINITY);boss.onSyncedDataUpdated(key);
        h.assertTrue(boss.getEntityData().get(key)==health,"Direct DataItem write must be repaired before external death checks");
        raw.setValue(0f);
        h.assertTrue(boss.getHealth()==health&&boss.isAlive(),"Silent DataItem writes must not replace authoritative vitality");
        boss.aiStep();h.assertTrue(boss.getEntityData().get(key)==health,"AI tick must repair even silent writes");
        h.assertTrue(boss.getHealth()==health,"Actual damage and direct setter must not bypass the independent lock");
        var playerSource=new net.minecraft.world.damagesource.DamageSource(boss.damageSources().playerAttack(player).typeHolder(),mob,player);
        h.assertTrue(ArkDamage.hurt(boss,playerSource,10)&&ArkDamage.hurt(boss,playerSource,10),"Player-owned attacks must still bypass cooldown during non-player lock");
        float afterPlayer=boss.getHealth();
        h.runAfterDelay(9,()->{
            h.assertTrue(!WeaponDamage.hurt(boss,mobSource,10)&&boss.getHealth()==afterPlayer,"Non-player lock must remain through tick nine");
        });
        h.runAfterDelay(10,()->{
            try { h.assertTrue(ArkDamage.hurt(boss,mobSource,10),"Non-player damage must resume at tick ten");h.succeed(); }
            finally {boss.discard();mob.discard();player.discard();}
        });
    }
}
