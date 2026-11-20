package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter;
import net.everla.projectc.arkofcosmos.enchant.WitchFlames;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import static net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter.Attack.*;

@GameTestHolder("arkofcosmos") @PrefixGameTestTemplate(false)
public final class InfernumGameTests {
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void summonOwnerFromEntitySync(GameTestHelper h){
        var boss=boss(h);boss.infernum().entrance=0;boss.infernum().tick();
        for(var kind:new MahouInfernumSummonEntity.Kind[]{MahouInfernumSummonEntity.Kind.CATACLYSM,MahouInfernumSummonEntity.Kind.CATASTROPHE}){
            var original=MahouInfernumSummonEntity.spawn(boss,kind,boss.position(),0);
            var replica=new MahouInfernumSummonEntity(ArkOfCosmos.MAHOU_INFERNUM_SUMMON.get(),h.getLevel());
            replica.getEntityData().assignValues(original.getEntityData().getNonDefaultValues());
            h.assertTrue(replica.ownedBy(boss)&&replica.kind()==kind,"entity sync lost summon ownership or brother kind");
        }
        boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void mahouRetreatWithoutTarget(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        var old=boss.getTarget();old.discard();boss.setTarget(null);boss.setLastHurtByMob(null);
        double y=boss.getY();ai.tick();
        h.assertTrue(!boss.isRemoved()&&boss.retreatOpacity()<1&&boss.getY()>y,"retreat did not fade and ascend");
        var saved=new CompoundTag();ai.save(saved);h.assertTrue(saved.getCompound("Infernum").getFloat("RetreatOpacity")<1,"retreat progress not saved");
        for(int i=0;i<20&&!boss.isRemoved();i++)ai.tick();
        h.assertTrue(boss.isRemoved()&&!boss.defeating(),"retreat incorrectly entered victory or failed to remove boss");
        h.assertTrue(!CalamitasBattleData.get(h.getLevel()).active(boss.getUUID()),"retreat left active battle journal");
        h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void legacyMahouAttackRetires(GameTestHelper h){
        var attack=new MahouAttackEntity(ArkOfCosmos.MAHOU_ATTACK.get(),h.getLevel());
        var tag=new CompoundTag();tag.putInt("Kind",20);tag.putInt("Life",600);
        attack.readAdditionalSaveData(tag);
        h.getLevel().addFreshEntity(attack);
        attack.tick();
        h.assertTrue(attack.isRemoved(),"legacy attack survived its first server tick");
        for(int phase=1;phase<=3;phase++){
            var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(h.getLevel());
            var saved=new CompoundTag();boss.addAdditionalSaveData(saved);
            saved.remove("Infernum");saved.putByte("Phase",(byte)phase);
            saved.putFloat("Health",boss.getMaxHealth()*.5f);
            boss.readAdditionalSaveData(saved);
            close(h,boss.totalHealthProgress(),phase==1?.875:phase==2?.60:.35,"old phase health migration");
            h.assertTrue(boss.infernum().stage==phase-1,"old phase entered wrong Infernum stage");
            boss.discard();
        }
        h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void infernumDamageConfiguration(GameTestHelper h) throws Exception {
        var config=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.snapshot();
        var boss=boss(h);var victim=boss.getTarget();
        var defaults=config.copy();
        try {
            h.assertTrue(config.getAllKeys().stream().noneMatch(k->k.startsWith("boss.mahou.skills.")),"obsolete skill keys remain");
            var values=config.copy();values.putDouble("boss.mahou.skill_damage_multiplier",2);
            int count=0;
            for(var kind:MahouInfernumAttackEntity.Kind.values()) {
                String key="boss.mahou.attacks."+kind.name().toLowerCase(java.util.Locale.ROOT);
                boolean damaging=switch(kind){case BOMB,WARNING,BOMB_WARNING,PILLAR_WARNING,ORB,RITUAL,HEART_STAR->false;default->true;};
                h.assertTrue(values.contains(key)==damaging,"wrong configuration coverage for "+kind);
                if(!damaging)continue;
                // Spawn before changing configuration: existing shots must use live hit-time values.
                var shot=MahouInfernumAttackEntity.spawn(boss,kind,boss.position(),Vec3.ZERO,new Vec3(1,0,0),999,10,1,false);
                values.putDouble(key,3);net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(values);
                var source=new net.minecraft.world.damagesource.DamageSource(boss.damageSources().mobAttack(boss).typeHolder(),shot,boss);
                close(h,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.bossDamage(victim,source,10),60*boss.outgoingDamageMultiplier(),kind+" independent multiplier");
                close(h,shot.view().getFloat("Damage"),10,"scaled damage persisted into child source");
                values.putDouble(key,0);net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(values);
                close(h,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.bossDamage(victim,source,10),0,kind+" zero multiplier");
                values.putDouble(key,1);shot.retire();count++;
            }
            h.assertTrue(count==22,"missing damaging projectile kinds");
            var kindField=MahouInfernumSummonEntity.class.getDeclaredField("kind");kindField.setAccessible(true);
            for(var kind:MahouInfernumSummonEntity.Kind.values()) {
                String key="boss.mahou.contact."+kind.name().toLowerCase(java.util.Locale.ROOT);
                h.assertTrue(values.contains(key),"missing summon contact key "+kind);
                var summon=ArkOfCosmos.MAHOU_INFERNUM_SUMMON.get().create(h.getLevel());kindField.set(summon,kind);
                values.putDouble(key,4);net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(values);
                var source=new net.minecraft.world.damagesource.DamageSource(boss.damageSources().mobAttack(boss).typeHolder(),summon,boss);
                close(h,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.bossDamage(victim,source,10),80*boss.outgoingDamageMultiplier(),kind+" contact multiplier");
                values.putDouble(key,1);summon.discard();
            }
            values.putDouble("boss.mahou.contact.boss",5);net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(values);
            close(h,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.bossDamage(victim,boss.damageSources().mobAttack(boss),10),100*boss.outgoingDamageMultiplier(),"boss charge multiplier");
            var file=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            file.set("boss.mahou.health",2000d);file.set("boss.mahou.skill_damage_multiplier",2d);
            file.set("boss.mahou.skills.pillar",7d);file.set("boss.mahou.skills.other",8d);
            net.everla.projectc.arkofcosmos.ProjectCCommonConfig.SPEC.correct(file);
            h.assertTrue(!file.contains("boss.mahou.skills"),"obsolete table not removed");
            close(h,((Number)file.get("boss.mahou.health")).doubleValue(),2000,"health lost on config correction");
            close(h,((Number)file.get("boss.mahou.skill_damage_multiplier")).doubleValue(),2,"global multiplier lost");
            close(h,((Number)file.get("boss.mahou.attacks.pillar")).doubleValue(),1,"old pillar multiplier leaked into new pillar");
        } finally {net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(defaults);boss.discard();}
        h.succeed();
    }

    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void pillarVisualGeometryMatchesDamage(GameTestHelper h) throws Exception {
        var hit=MahouInfernumAttackEntity.class.getDeclaredMethod("capsuleHits",AABB.class,Vec3.class,Vec3.class,double.class);
        hit.setAccessible(true);
        for(double pixels:new double[]{8,16,32})for(int age=0;age<=105;age++){
            var shape=MahouInfernumAttackEntity.pillarShape(age,pixels);
            double height=MahouInfernumAttackEntity.pillarHeight(age),radius=MahouInfernumAttackEntity.pillarWidth(age)*.5/pixels;
            h.assertTrue(shape.bottom()==(height*.5-2440)/pixels&&shape.top()==(height*1.5-2440)/pixels&&shape.radius()==radius,"changed damage geometry");
            Vec3 from=new Vec3(0,shape.bottom(),0),to=new Vec3(0,shape.top(),0);
            double top=shape.top()+radius,bottom=shape.bottom()-radius;
            for(double y:new double[]{top-.0001,bottom+.0001})h.assertTrue((boolean)hit.invoke(null,new AABB(-.00001,y,-.00001,.00001,y,.00001),from,to,radius),"visible capsule cap must damage");
            for(double y:new double[]{top+.0001,bottom-.0001})h.assertTrue(!(boolean)hit.invoke(null,new AABB(-.00001,y,-.00001,.00001,y,.00001),from,to,radius),"outside visual capsule must miss");
        }
        var full=MahouInfernumAttackEntity.pillarShape(52.5,16);
        h.assertTrue(full.bottom()==-1240/16d&&full.top()==1160/16d,"telegraph maximum spine changed");
        h.succeed();
    }

    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void projectilesPauseWithoutTarget(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        var target=boss.getTarget();
        var shot=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.SLASH,ai.center(),new Vec3(.1,0,0),ai.plane,120,1,0,false);
        Vec3 before=shot.position();boss.setTarget(null);shot.sourceStep(boss);
        h.assertTrue(shot.position().equals(before)&&!shot.isRemoved(),"missing target did not pause projectile");
        boss.setTarget(target);shot.sourceStep(boss);shot.sourceStep(boss);
        h.assertTrue(shot.view().getInt("Age")>0,"projectile failed to resume after target recovery");
        boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void mahouProjectileBoundaryCleanup(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();var box=ai.arena.bounds();Vec3 plane=ai.plane;
        Vec3 outside=new Vec3(box.maxX+3,ai.center().y,ai.center().z);
        var outward=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,outside,new Vec3(.1,0,0),plane,690,1,0,false);outward.sourceStep(boss);
        h.assertTrue(outward.view().getLong("ArenaFade")>=0&&!outward.isRemoved(),"outward shot failed to start fade");
        var inward=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,outside,new Vec3(-.1,0,0),plane,690,1,0,false);inward.sourceStep(boss);h.assertTrue(inward.view().getLong("ArenaFade")==-1,"incoming shot culled");
        var emitter=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.BOMB,outside,new Vec3(.1,0,0),plane,120,0,5,false);emitter.sourceStep(boss);h.assertTrue(emitter.view().getLong("ArenaFade")==-1,"emitter lost native lifetime");
        var child=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,outside,new Vec3(.1,0,0),plane,690,1,0,false);var save=new CompoundTag();child.addAdditionalSaveData(save);save.getCompound("Infernum").putBoolean("SplitChild",true);child.readAdditionalSaveData(save);child.sourceStep(boss);h.assertTrue(child.view().getLong("ArenaFade")==-1,"new split child lost grace period");
        var far=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.ARROW,outside.add(2500/ai.pixels(),0,0),new Vec3(.1,0,0),plane,120,1,0,false);far.sourceStep(boss);h.assertTrue(far.isRemoved(),"distant escaping shot not culled");
        var beam=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM,outside,new Vec3(1,0,0),plane,450,0,0,false);beam.sourceStep(boss);h.assertTrue(beam.view().getLong("ArenaFade")==-1,"laser anchor culled");
        var reloaded=ArkOfCosmos.MAHOU_INFERNUM_ATTACK.get().create(h.getLevel());var fading=new CompoundTag();outward.addAdditionalSaveData(fading);reloaded.readAdditionalSaveData(fading);h.assertTrue(reloaded.view().getLong("ArenaFade")==outward.view().getLong("ArenaFade"),"reload lost fade state");reloaded.discard();
        h.runAtTickTime(6,()->{outward.sourceStep(boss);h.assertTrue(outward.isRemoved(),"fade did not retire after five ticks");boss.discard();h.succeed();});
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void dynamicReductionRatiosAndMusic(GameTestHelper h){
        var reduction=new net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasDynamicReduction();
        close(h,reduction.apply(0,160,4444),160,"Calamitas original baseline changed");
        var scaled=new net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasDynamicReduction();
        double hit=scaled.apply(0,3200,8888);close(h,hit,320+320*Math.log(10),"Calamitas logarithmic ratio changed");h.assertTrue(hit>320,"Calamitas was hard capped");
        close(h,scaled.apply(20,320,8888),320,"rolling budget did not expire");
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.start(HorizontalDarkSoulRelease);
        for(int i=0;i<30;i++){boss.invulnerableTime=0;boss.actuallyHurt(boss.damageSources().playerAttack(player(h)),boss.getMaxHealth());}
        close(h,boss.getMaxHealth()-boss.getHealth(),boss.getMaxHealth()/60d,"Mahou exceeded one second damage budget");
        h.assertTrue(boss.musicPhase()==1,"opening music phase incorrect");ai.stage=1;h.assertTrue(boss.musicPhase()==1,"75 percent changed music");ai.stage=2;h.assertTrue(boss.musicPhase()==2,"45 percent failed to change music");ai.stage=4;h.assertTrue(boss.musicPhase()==2,"desperation restarted music");boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void mahouArenaFlightLease(GameTestHelper h){
        var boss=boss(h);boss.infernum().entrance=0;boss.infernum().tick();h.getLevel().addFreshEntity(boss);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ArenaFlight")){
            @Override public void onUpdateAbilities(){}
        };
        player.setPos(boss.infernum().arena.origin());var abilities=player.getAbilities();float speed=abilities.getFlyingSpeed();
        net.everla.projectc.arkofcosmos.calamity.CalamitasArenaFlight.update(player);
        h.assertTrue(abilities.mayfly,"Mahou arena did not grant creative-style flight");close(h,abilities.getFlyingSpeed(),speed*2,"Mahou flight acceleration differs from Calamitas");
        net.everla.projectc.arkofcosmos.calamity.CalamitasArenaFlight.update(player);close(h,abilities.getFlyingSpeed(),speed*2,"arena speed stacked every tick");
        abilities.flying=true;player.setPos(boss.infernum().arena.origin().add(1000,0,0));
        net.everla.projectc.arkofcosmos.calamity.CalamitasArenaFlight.update(player);
        h.assertTrue(!abilities.mayfly&&!abilities.flying,"arena flight survived exit");close(h,abilities.getFlyingSpeed(),speed,"arena speed not restored");
        player.setPos(boss.infernum().arena.origin());abilities.mayfly=true;
        net.everla.projectc.arkofcosmos.calamity.CalamitasArenaFlight.update(player);net.everla.projectc.arkofcosmos.calamity.CalamitasArenaFlight.release(player);
        h.assertTrue(abilities.mayfly,"arena removed preexisting flight ability");close(h,abilities.getFlyingSpeed(),speed,"release did not restore original speed");boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void continuousPlayerPlane(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();ai.start(SummonShadowDemon);ai.timer=10;ai.velocity=Vec3.ZERO;
        var pivot=ai.center();ai.plane=new Vec3(1,0,0);boss.getTarget().setPos(pivot.add(0,0,12));
        var warning=ai.shot(MahouInfernumAttackEntity.Kind.BOMB_WARNING,pivot.add(3,0,0),new Vec3(1,0,0),1,0,0);
        var beam=ai.shot(MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM,pivot,new Vec3(1,0,0),450,0,0);
        var aimed=ai.shot(MahouInfernumAttackEntity.Kind.GIGA,pivot,new Vec3(1,0,0),90,0,0);
        ai.tick();close(h,ai.plane.dot(new Vec3(0,0,1)),1,"attack did not continuously face target");
        var data=CalamitasBattleData.get(h.getLevel());int bombs=0;
        for(UUID id:data.members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.BOMB){
            close(h,MahouInfernumAttackEntity.vector(shot.view(),"Plane").dot(new Vec3(1,0,0)),1,"warning child changed launch plane");h.assertTrue(!shot.view().getBoolean("FollowPlane"),"fired child still follows player");bombs++;
            var saved=new CompoundTag();shot.addAdditionalSaveData(saved);var restored=ArkOfCosmos.MAHOU_INFERNUM_ATTACK.get().create(h.getLevel());restored.readAdditionalSaveData(saved);h.assertTrue(!restored.view().getBoolean("FollowPlane"),"reload reactivated projectile tracking");restored.discard();
        }
        h.assertTrue(bombs==1&&warning.isRemoved(),"warning did not become bomb");
        close(h,MahouInfernumAttackEntity.vector(beam.view(),"Velocity").z,0,"fired laser followed player");h.assertTrue(!aimed.view().getBoolean("FollowPlane"),"three dimensional seeker was made planar");
        boss.getTarget().setPos(pivot.add(-12,0,0));ai.tick();close(h,ai.plane.dot(new Vec3(-1,0,0)),1,"plane stopped tracking after first turn");
        close(h,MahouInfernumAttackEntity.vector(beam.view(),"Velocity").z,0,"laser did not follow second turn");boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void arkBrotherDeathReleasesGate(GameTestHelper h){
        for(boolean hitFollower:new boolean[]{false,true}){
            var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.stage=2;ai.start(SummonBrothers);ai.timer=284;
            var leader=MahouInfernumSummonEntity.spawn(boss,MahouInfernumSummonEntity.Kind.CATACLYSM,boss.position(),0);
            var follower=MahouInfernumSummonEntity.spawn(boss,MahouInfernumSummonEntity.Kind.CATASTROPHE,boss.position().add(4,0,0),0);
            CalamitasBattleData.get(h.getLevel()).add(boss.getUUID(),UUID.randomUUID(),"mahou_CATASTROPHE");
            close(h,leader.getMaxHealth(),1225,"brother maximum health incorrect");close(h,follower.getMaxHealth(),1225,"partner maximum health incorrect");
            var target=hitFollower?follower:leader;var source=leader.damageSources().generic();float before=leader.getHealth();
            h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(target,source,150),"Ark brother hit rejected");
            h.assertTrue(leader.getHealth()<before&&leader.getHealth()>0,"Ark did not damage brother shared health");close(h,follower.getHealth(),leader.getHealth(),"brother health diverged");
            leader.invulnerableTime=0;net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(target,source,leader.getMaxHealth()*2);
            h.assertTrue(!leader.isAlive()&&follower.isRemoved(),"Ark brother death skipped partner cleanup");
            var data=CalamitasBattleData.get(h.getLevel());h.assertTrue(data.living(boss.getUUID(),"mahou_CATACLYSM")==0&&data.living(boss.getUUID(),"mahou_CATASTROPHE")==0,"brother death did not update gate ledger");
            ai.tick();h.assertTrue(ai.attack!=SummonBrothers,"Ark kill stuck summon phase");boss.discard();
        }
        h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void arkRespectsSummonProtection(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        for(var kind:new MahouInfernumSummonEntity.Kind[]{MahouInfernumSummonEntity.Kind.SHADOW,MahouInfernumSummonEntity.Kind.BOMBER,MahouInfernumSummonEntity.Kind.SEEKER,MahouInfernumSummonEntity.Kind.WORM}){
            var member=MahouInfernumSummonEntity.spawn(boss,kind,boss.position(),0);float before=member.getHealth();
            if(kind==MahouInfernumSummonEntity.Kind.SHADOW){var player=player(h);h.assertTrue(!net.everla.projectc.arkofcosmos.combat.WeaponUser.canTrack(player,member),"shadow demon remains trackable");h.assertTrue(!net.everla.projectc.arkofcosmos.combat.ArkCombat.enemy(player,member),"homing enemy filter selected shadow demon");}
            h.assertTrue(!net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(member,member.damageSources().generic(),Float.MAX_VALUE),"Ark bypassed protection for "+kind);
            close(h,member.getHealth(),before,"protected summon lost health");h.assertTrue(!member.isRemoved(),"protected summon removed");member.retire();
        }
        boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void wormSharedHealthTargetingAndPlane(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        var player=player(h);player.setPos(boss.position().add(9,5,12));boss.setTarget(player);
        var head=MahouInfernumSummonEntity.spawn(boss,MahouInfernumSummonEntity.Kind.WORM,boss.position(),0);
        var saved=new CompoundTag();head.addAdditionalSaveData(saved);saved.getCompound("Infernum").putInt("State",5);saved.getCompound("Infernum").putInt("Timer",0);head.readAdditionalSaveData(saved);
        var segments=new java.util.ArrayList<MahouInfernumSummonEntity>();
        for(UUID id:CalamitasBattleData.get(h.getLevel()).members(boss.getUUID(),false))if(h.getLevel().getEntity(id) instanceof MahouInfernumSummonEntity m&&m.kind()==MahouInfernumSummonEntity.Kind.SEGMENT)segments.add(m);
        h.assertTrue(segments.size()==40,"missing worm segments");
        h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkCombat.enemy(player,head),"weapon cannot lock worm head");
        for(var segment:segments){h.assertTrue(net.everla.projectc.arkofcosmos.combat.WeaponUser.canTrack(player,segment),"weapon cannot lock segment");close(h,segment.getMaxHealth(),head.getMaxHealth(),"segment has independent maximum health");}
        var source=player.damageSources().playerAttack(player);float before=head.getHealth();var segment=segments.get(0);
        h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(segment,source,150),"Ark hit rejected");
        h.assertTrue(head.getHealth()<before&&head.getHealth()>0&&!segment.isRemoved()&&!head.isRemoved(),"Ark removed segment before shared health depleted");
        for(var part:segments)close(h,part.getHealth(),head.getHealth(),"segment has independent health");
        head.invulnerableTime=0;before=head.getHealth();head.hurt(source,10);h.assertTrue(head.getHealth()<before,"native hit missed shared health");
        Vec3 expected=player.position().subtract(head.position()).multiply(1,0,1).normalize();head.sourceStep(boss);
        int bombs=0;for(UUID id:CalamitasBattleData.get(h.getLevel()).members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.SOUL_BOMB){close(h,MahouInfernumAttackEntity.vector(shot.view(),"Plane").dot(expected),1,"worm bomb plane does not aim at player");bombs++;}
        h.assertTrue(bombs==1,"worm did not emit aimed soul bomb");
        var wallState=new CompoundTag();head.addAdditionalSaveData(wallState);wallState.getCompound("Infernum").putInt("State",1);wallState.getCompound("Infernum").putInt("Timer",31);head.readAdditionalSaveData(wallState);head.sourceStep(boss);
        int wallBombs=0;for(UUID id:CalamitasBattleData.get(h.getLevel()).members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.BOMB){
            Vec3 locked=MahouInfernumAttackEntity.vector(shot.view(),"Plane");close(h,shot.position().subtract(player.position()).dot(locked.cross(new Vec3(0,1,0))),0,"worm bomb wall used boss plane instead of player plane");wallBombs++;
        }
        h.assertTrue(wallBombs==1,"worm bomb wall not emitted");
        var restored=ArkOfCosmos.MAHOU_INFERNUM_SUMMON.get().create(h.getLevel());var partSave=new CompoundTag();segment.addAdditionalSaveData(partSave);restored.readAdditionalSaveData(partSave);close(h,restored.getHealth(),head.getHealth(),"reloaded segment lost shared health");restored.discard();
        head.invulnerableTime=0;h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(segment,head.damageSources().generic(),head.getMaxHealth()*2),"lethal shared hit rejected");
        h.assertTrue(head.getHealth()==0&&!head.isAlive(),"shared health depletion did not kill head");for(var part:segments){part.tick();h.assertTrue(part.isRemoved(),"head death left segment alive");}boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void threeDimensionalChargeAndFinalLanding(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.start(ExplosiveCharges);Vec3 plane=ai.plane;
        boss.getTarget().setPos(ai.center().add(8,8,8));ai.timer=131;ai.tick();
        h.assertTrue(ai.velocity.y>0&&Math.abs(ai.velocity.z)>0,"charge flattened a three dimensional target");h.assertTrue(ai.plane.z>0,"attack plane did not follow target");
        var floor=net.minecraft.core.BlockPos.containing(boss.getTarget().position().add(0,-2,0));for(var block:net.minecraft.core.BlockPos.betweenClosed(floor.offset(-2,1,-2),floor.offset(2,100,2)))h.getLevel().setBlockAndUpdate(block,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(floor,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        ai.stage=4;ai.start(DesperationPhase);ai.finalState=4;
        for(int i=0;i<100;i++)ai.tick();
        h.assertTrue(boss.getY()>=floor.getY()+1-1e-5,"acceptance descent tunneled through ground");
        h.assertTrue(boss.getY()<floor.getY()+3,"acceptance descent never reached ground: boss="+boss.position()+", floor="+floor+", velocity="+ai.velocity);
        boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=80)
    public static void fanRatesAndTransitionCycle(GameTestHelper h){
        for(boolean rage:new boolean[]{false,true}){
            var boss=boss(h);if(rage)boss.enrageWithMistilteinn(player(h));var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();ai.start(CondemnationFanBurst);ai.timer=120;
            for(int i=0;i<18;i++)ai.tick();
            int arrows=0;for(UUID id:CalamitasBattleData.get(h.getLevel()).members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.ARROW)arrows++;
            h.assertTrue(arrows==(rage?52:26),"fan source shoot rate changed, rage="+rage+", arrows="+arrows);
            ai.stage=1;ai.cycle=0;ai.start(PhaseTransition);ai.timer=104;ai.tick();
            h.assertTrue(ai.attack==SummonShadowDemon&&ai.cycle==1,"transition failed to advance source cycle before summon");
            ai.timer=169;ai.tick();h.assertTrue(ai.attack==ShadowDemon_ReleaseExplodingShadowBlasts,"wrong first attack after shadow summon");boss.discard();
        }
        h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void harmlessBombAndPlaneEscape(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        var victim=EntityType.PIG.create(h.getLevel());victim.setPos(boss.position());h.getLevel().addFreshEntity(victim);float health=victim.getHealth();
        var bomb=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.BOMB,victim.position(),Vec3.ZERO,ai.plane,120,15,30,false);
        bomb.sourceStep(boss);close(h,victim.getHealth(),health,"bomb body dealt contact damage");bomb.retire();
        var plane=new Vec3(.6,0,.8);var normal=plane.cross(new Vec3(0,1,0));victim.setPos(boss.position().add(normal.scale(5)));
        var beam=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM,boss.position(),plane,plane,450,10,0,false);
        for(int i=0;i<50;i++)beam.sourceStep(boss);close(h,victim.getHealth(),health,"beam hit target outside its plane thickness");
        close(h,victim.position().subtract(boss.position()).dot(normal),5,"attack forced target into plane");boss.discard();victim.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void thresholdOvershootAndLegacyRecovery(GameTestHelper h){
        close(h,MahouInfernumEncounter.nextStage(0,.2f),3,"one hit must cross all source thresholds");
        close(h,MahouInfernumEncounter.nextStage(1,.25f),2,"exact last threshold must not advance");
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.stage=3;ai.start(FireLaserSpin);ai.timer=400;
        var saved=new CompoundTag();ai.save(saved);var old=saved.getCompound("Infernum");old.putInt("Version",1);
        var restored=boss(h);restored.infernum().load(saved);
        h.assertTrue(restored.infernum().stage==3&&restored.infernum().attack==PhaseTransition&&restored.infernum().timer==0,"legacy attack resumed halfway through");
        close(h,restored.infernum().pixels(),ai.pixels(),"legacy migration changed scale");
        boss.discard();restored.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void ritualInheritanceAndLaserLifetime(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        Vec3 plane=new Vec3(.6,0,.8);
        var ritual=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.RITUAL,boss.position(),Vec3.ZERO,plane,1,0,0,true);
        ai.plane=new Vec3(0,0,1);ritual.sourceStep(boss);
        var data=CalamitasBattleData.get(h.getLevel());int bombers=0;
        for(UUID id:data.members(boss.getUUID(),false))if(h.getLevel().getEntity(id) instanceof MahouInfernumSummonEntity summon&&summon.kind()==MahouInfernumSummonEntity.Kind.BOMBER){
            h.assertTrue(summon.tail(),"ritual demon lost launch phase");close(h,MahouInfernumAttackEntity.vector(summon.view(),"Plane").dot(plane),1,"ritual demon lost launch plane");
            var saved=new CompoundTag();summon.addAdditionalSaveData(saved);var restored=ArkOfCosmos.MAHOU_INFERNUM_SUMMON.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
            h.assertTrue(restored.tail(),"summon reload lost launch phase");close(h,MahouInfernumAttackEntity.vector(restored.view(),"Plane").dot(plane),1,"summon reload lost launch plane");restored.discard();bombers++;
        }
        h.assertTrue(bombers==1,"ritual did not spawn exactly one demon");
        ai.start(FireLaserSpin);ai.timer=289;ai.tick();
        int beams=0;for(UUID id:data.members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM){
            for(int i=0;i<390;i++)shot.sourceStep(boss);
            h.assertTrue(!shot.isRemoved(),"spin beam vanished before source fadeout");
            for(int i=0;i<60;i++)shot.sourceStep(boss);h.assertTrue(shot.isRemoved(),"spin beam outlived source fadeout");beams++;
        }
        h.assertTrue(beams==5,"wrong spin beam count");boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void optionalContentRegistry(GameTestHelper h){
        var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","satsuki_rin");
        boolean enabled=ArkOfCosmos.FIRST_FRACTAL_ENABLED;
        h.assertTrue(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(key)==enabled,"Satsuki registration does not match dependencies");
        h.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","satsuki_rin_spawn_egg"))==enabled,"Satsuki egg registration does not match dependencies");
        h.assertTrue(ArkOfCosmos.MAHOU_TSUKAI.isPresent(),"standalone Mahou is absent");
        h.succeed();
    }
    private static MahouTsukaiEntity boss(GameTestHelper h){
        var boss=ArkOfCosmos.MAHOU_TSUKAI.get().create(h.getLevel());boss.applyConfiguredHealth();
        boss.setPos(h.absoluteVec(new Vec3(1,4,1)));var target=EntityType.PIG.create(h.getLevel());target.setPos(boss.position().add(8,0,0));boss.setTarget(target);return boss;
    }
    private static ServerPlayer player(GameTestHelper h){return new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"InfernumTest"));}
    private static void close(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<.0002,message+": "+a+" != "+b);}
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void rageAndReload(GameTestHelper h){
        var boss=boss(h);var player=player(h);float maximum=boss.getMaxHealth();boss.actuallyHurt(player.damageSources().playerAttack(player),5);float previous=boss.getHealth();
        boss.enrageWithMistilteinn(player);close(h,boss.getMaxHealth(),maximum*.01,"maximum shrink");close(h,boss.getHealth(),previous*.01,"current shrink");
        h.assertTrue(!boss.enrageWithMistilteinn(player(h)),"repeat trigger accepted");close(h,boss.outgoingDamageMultiplier(),6,"rage damage");
        var saved=new CompoundTag();boss.addAdditionalSaveData(saved);var restored=boss(h);restored.readAdditionalSaveData(saved);
        close(h,restored.getMaxHealth(),boss.getMaxHealth(),"reload maximum");close(h,restored.getHealth(),boss.getHealth(),"reload current");
        h.assertTrue(restored.mistilteinnEnraged()&&saved.getUUID("MistilteinnProvoker").equals(player.getUUID()),"rage/provoker lost");
        boss.discard();restored.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void mitigationAndRevenge(GameTestHelper h){
        var ordinary=boss(h);var enraged=boss(h);var player=player(h);
        enraged.enrageWithMistilteinn(player);
        float old=ordinary.getHealth(),rageOld=enraged.getHealth();
        ordinary.actuallyHurt(player.damageSources().playerAttack(player),2);
        enraged.actuallyHurt(player.damageSources().playerAttack(player),2);
        close(h,rageOld-enraged.getHealth(),(old-ordinary.getHealth())*.01,"99 percent mitigation");
        var source=new net.minecraft.world.damagesource.DamageSource(enraged.damageSources().magic().typeHolder(),enraged,enraged);
        enraged.awardKillScore(player(h),0,source);h.assertTrue(!enraged.isRemoved(),"different player caused retreat");
        enraged.awardKillScore(player,0,source);h.assertTrue(enraged.isRemoved()&&!enraged.defeating(),"provoker kill did not retreat without loot");
        ordinary.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void stagesAndGates(GameTestHelper h){
        close(h,MahouInfernumEncounter.nextStage(0,.75f),0,"exact threshold");
        close(h,MahouInfernumEncounter.nextStage(0,.749f),1,"first threshold");close(h,MahouInfernumEncounter.nextStage(1,.449f),2,"second threshold");close(h,MahouInfernumEncounter.nextStage(2,.249f),3,"third threshold");
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        h.assertTrue(ai.attack==SummonSepulcher,"initial summon lost");
        ai.shortRitual=false;ai.timer=301;ai.tick();h.assertTrue(ai.living(MahouInfernumSummonEntity.Kind.WORM)==1,"worm not summoned");
        ai.timer=442;ai.tick();h.assertTrue(ai.attack==SummonSepulcher,"living summon gate bypassed");
        ai.clearKind(MahouInfernumSummonEntity.Kind.WORM);ai.tick();h.assertTrue(ai.attack==HorizontalDarkSoulRelease,"first cycle entry");
        ai.stage=4;ai.start(DesperationPhase);ai.finalState=0;ai.timer=719;ai.tick();h.assertTrue(ai.finalState==1,"pillar phase did not end");
        ai.timer=635;ai.tick();h.assertTrue(ai.finalState==2,"bomb phase did not end");
        ai.timer=1049;ai.tick();h.assertTrue(ai.finalState==3,"dart phase did not end");
        ai.finalState=4;ai.timer=779;ai.tick();
        h.assertTrue(boss.defeating()&&!CalamitasBattleData.get(h.getLevel()).active(boss.getUUID()),"final completion failed to clean battle");
        boss.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void projectileClockAndSplit(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        var dart=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,boss.position(),new Vec3(.1,0,0),new Vec3(1,0,0),690,0,0,false);
        dart.sourceStep(boss);close(h,MahouInfernumAttackEntity.vector(dart.view(),"Velocity").x,.1,"snapshot before third frame");
        dart.sourceStep(boss);dart.sourceStep(boss);close(h,MahouInfernumAttackEntity.vector(dart.view(),"Velocity").x,.1*Math.pow(1.01,3),"dart acceleration");
        dart.retire();var data=CalamitasBattleData.get(h.getLevel());int before=data.members(boss.getUUID(),true).size();
        var giga=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.GIGA,boss.position(),new Vec3(.1,0,0),new Vec3(1,0,0),1,0,0,true);giga.parameter(13);giga.sourceStep(boss);
        h.assertTrue(data.members(boss.getUUID(),true).size()==before+13,"final giga split count");
        for(UUID id:data.members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot)h.assertTrue(shot.tail(),"child lost final status");
        UUID unloaded=UUID.randomUUID();data.add(boss.getUUID(),unloaded,"mahou_BOMBER");ai.clearKind(MahouInfernumSummonEntity.Kind.BOMBER);h.assertTrue(data.confirmedDead(boss.getUUID(),unloaded),"unloaded summon survives retirement");
        boss.discard();h.assertTrue(!data.active(boss.getUUID()),"battle retained after removal");h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void hexWithoutGoety(GameTestHelper h){
        var target=EntityType.PIG.create(h.getLevel());WitchFlames.hex(target,60,1);h.assertTrue(WitchFlames.strength(target)==1,"hex unavailable");WitchFlames.clear(target);h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=80)
    public static void allAttacksAndStatuses(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        for(var attack:MahouInfernumEncounter.Attack.values()){
            ai.clearShots();for(var kind:MahouInfernumSummonEntity.Kind.values())ai.clearKind(kind);
            ai.stage=attack==DesperationPhase?4:3;ai.finalState=0;ai.start(attack);
            for(int i=0;i<120;i++)ai.tick();
            h.assertTrue(Double.isFinite(boss.getX())&&Double.isFinite(boss.getY())&&Double.isFinite(boss.getZ()),"nonfinite movement in "+attack);
        }
        var ordinary=EntityType.PIG.create(h.getLevel());var tail=EntityType.PIG.create(h.getLevel());
        for(boolean finalShot:new boolean[]{false,true}){
            var target=finalShot?tail:ordinary;
            var shot=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,boss.position(),Vec3.ZERO,new Vec3(1,0,0),690,0,0,finalShot);
            var source=new net.minecraft.world.damagesource.DamageSource(boss.damageSources().generic().typeHolder(),shot,boss);
            h.assertTrue(net.everla.projectc.arkofcosmos.combat.WeaponDamage.hurt(target,source,.1f),"status hit rejected");
            h.assertTrue(WitchFlames.weakened(target)==finalShot,"wrong hex applicability");
            h.assertTrue(target.hasEffect(ArkOfCosmos.ELEMENTAL_MIX.get())!=finalShot,"wrong resonance applicability");
            WitchFlames.clear(target);
        }
        boss.discard();h.succeed();
    }

    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void verticalPlaneAndReload(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();
        ai.plane=new Vec3(.6,0,.8);Vec3 normal=ai.plane.cross(new Vec3(0,1,0));
        ai.boss.getTarget().setPos(ai.center().add(0,9,0));ai.lockPlane();
        close(h,ai.plane.dot(new Vec3(.6,0,.8)),1,"vertical target lost previous plane");
        ai.start(FireLaserSpin);ai.timer=289;ai.tick();
        int beams=0;
        var data=CalamitasBattleData.get(h.getLevel());
        for(UUID id:data.members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot&&shot.kind()==MahouInfernumAttackEntity.Kind.OVERLOAD_BEAM){
            for(int step=0;step<12;step++)shot.sourceStep(boss);
            Vec3 v=MahouInfernumAttackEntity.vector(shot.view(),"Velocity");
            close(h,v.dot(normal),0,"laser escaped vertical plane");close(h,v.length(),1,"laser direction is not unit length");beams++;
        }
        h.assertTrue(beams==5,"spin did not create five beams");
        ai.clearShots();ai.start(SummonSeekers);ai.timer=79;ai.tick();
        for(UUID id:data.members(boss.getUUID(),false))if(h.getLevel().getEntity(id) instanceof MahouInfernumSummonEntity seeker&&seeker.kind()==MahouInfernumSummonEntity.Kind.SEEKER)
            close(h,seeker.position().subtract(ai.arena.origin()).dot(normal),0,"seeker escaped vertical plane");
        var save=new CompoundTag();ai.save(save);var restored=boss(h);restored.infernum().load(save);
        close(h,restored.infernum().plane.dot(ai.plane),1,"saved plane changed");
        close(h,restored.infernum().pixels(),ai.pixels(),"saved pixel scale changed");
        h.assertTrue(restored.infernum().attack==ai.attack&&restored.infernum().timer==ai.timer,"saved choreography changed");
        boss.discard();restored.discard();h.succeed();
    }
    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void warningSplitKeepsPlane(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        Vec3 plane=new Vec3(.6,0,.8),direction=plane.scale(-1);
        var warning=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.BOMB_WARNING,boss.position(),direction,plane,1,0,12,true);
        ai.plane=new Vec3(0,0,1);warning.sourceStep(boss);
        var data=CalamitasBattleData.get(h.getLevel());int bombs=0;
        for(UUID id:data.members(boss.getUUID(),true))if(h.getLevel().getEntity(id) instanceof MahouInfernumAttackEntity shot){
            close(h,MahouInfernumAttackEntity.vector(shot.view(),"Plane").dot(plane),1,"child recaptured boss plane");
            close(h,MahouInfernumAttackEntity.vector(shot.view(),"Velocity").normalize().dot(direction),1,"bomb differs from warning direction");
            h.assertTrue(shot.tail(),"warning lost desperation status");bombs++;
            var saved=new CompoundTag();shot.addAdditionalSaveData(saved);
            var restored=ArkOfCosmos.MAHOU_INFERNUM_ATTACK.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
            close(h,MahouInfernumAttackEntity.vector(restored.view(),"Plane").dot(plane),1,"projectile reload lost plane");
            h.assertTrue(restored.tail(),"projectile reload lost desperation status");restored.discard();
        }
        h.assertTrue(bombs==1,"warning did not produce exactly one bomb");boss.discard();h.succeed();
    }

    @GameTest(template="infernum_empty",timeoutTicks=40)
    public static void projectileCollisionAndExtraUpdates(GameTestHelper h){
        var boss=boss(h);var ai=boss.infernum();ai.entrance=0;ai.tick();ai.clearShots();
        var victim=EntityType.PIG.create(h.getLevel());victim.setPos(boss.position().add(1.5,0,0));h.getLevel().addFreshEntity(victim);
        float before=victim.getHealth();
        var dart=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.DART,boss.position().add(0,.4,0),new Vec3(3,0,0),new Vec3(1,0,0),690,1,0,false);
        dart.sourceStep(boss);h.assertTrue(victim.getHealth()<before,"fast projectile tunneled through victim");
        h.assertTrue(victim.hasEffect(ArkOfCosmos.ELEMENTAL_MIX.get())&&!WitchFlames.weakened(victim),"ordinary collision applied wrong status");
        var slash=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.SLASH,boss.position().add(0,20,0),new Vec3(.1,0,0),new Vec3(1,0,0),1500,0,0,false);
        Vec3 origin=slash.position();slash.sourceStep(boss);close(h,slash.getX()-origin.x,.2,"slash missing source extra update");
        var fist=MahouInfernumAttackEntity.spawn(boss,MahouInfernumAttackEntity.Kind.FIST,origin,new Vec3(.1,0,0),new Vec3(1,0,0),1200,0,0,false);
        for(int i=0;i<6;i++)fist.sourceStep(boss);
        close(h,MahouInfernumAttackEntity.vector(fist.view(),"Velocity").y,-Math.sin(1)*5/ai.pixels(),"fist missing sinusoidal movement");
        boss.discard();victim.discard();h.succeed();
    }

}
