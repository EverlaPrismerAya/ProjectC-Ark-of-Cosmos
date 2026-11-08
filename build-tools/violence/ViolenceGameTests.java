package net.everla.projectc.arkofcosmos;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.everla.projectc.arkofcosmos.entity.*;
import net.everla.projectc.arkofcosmos.combat.*;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect;

@GameTestHolder("arkofcosmos") @PrefixGameTestTemplate(false)
public final class ViolenceGameTests {
    static void check(GameTestHelper h,boolean value,String message){h.assertTrue(value,message);}
    static void near(GameTestHelper h,double actual,double expected,String message){check(h,Math.abs(actual-expected)<.002,message+": "+actual+" != "+expected);}
    static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    static Object get(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    static ServerPlayer player(GameTestHelper h){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"violence-test"));
        h.getLevel().addNewPlayer(p);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(4,5,4)));p.setNoGravity(true);p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArkOfCosmos.VIOLENCE.get()));p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(24.25);return p;
    }
    static net.minecraft.world.entity.monster.Zombie target(GameTestHelper h,Vec3 at){
        var e=EntityType.ZOMBIE.create(h.getLevel());e.setPos(at);e.setNoAi(true);e.setNoGravity(true);e.setInvulnerable(false);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.setHealth(1000);h.getLevel().addFreshEntity(e);return e;
    }
    static void end(GameTestHelper h,ServerPlayer p){
        for(var e:h.getLevel().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(256),e->e.ownerId()==p.getId()))e.discard();
        ViolenceCombat.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
        h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.succeed();
    }
    static void damageTick(LivingEntity e){e.tickCount=20;WitchFlames.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(e));}
    @GameTest(template="violence_empty",timeoutTicks=40) public static void innateRealityPiercer(GameTestHelper h){
        var p=player(h);var enchantment=net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety_revelation","reality_piercer"));
        check(h,enchantment!=null,"Revelation test dependency loaded");var stack=p.getMainHandItem();
        near(h,InnateEnchantment.combatEnchantments(stack).getOrDefault(enchantment,0),10,"innate Reality Piercer X");check(h,stack.getAllEnchantments().isEmpty(),"innate enchantment does not mutate NBT");
        stack.enchant(enchantment,2);near(h,InnateEnchantment.combatEnchantments(stack).get(enchantment),12,"actual enchantment adds to innate level");
        var attack=ViolenceEntity.launch(p,true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));near(h,InnateEnchantment.combatEnchantments(attack.weapon()).get(enchantment),12,"thrown snapshot keeps innate and actual enchantments");attack.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void sepulcherNotTracked(GameTestHelper h){
        var p=player(h);
        for(var kind:CalamitasSepulcherEntity.Kind.values()){
            var worm=CalamitasContent.SEPULCHER.get(kind).get().create(h.getLevel());worm.setPos(p.position().add(0,0,4));
            check(h,ArkCombat.canHit(p,worm),"direct hits remain allowed for "+kind);
            check(h,!WeaponUser.canTrack(p,worm)&&!ArkCombat.enemy(p,worm)&&!WeaponUser.enemy(p,worm),"tracking excludes "+kind);
        }
        var zombie=target(h,p.position().add(0,0,10));check(h,WeaponUser.canTrack(p,zombie)&&ArkCombat.enemy(p,zombie),"ordinary enemy still tracked");zombie.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void crosshairLockRange(GameTestHelper h){
        var p=player(h);p.setPos(p.position().add(5000,0,5000));
        h.getLevel().getChunkAt(p.blockPosition());
        h.getLevel().getChunkAt(net.minecraft.core.BlockPos.containing(p.position().add(10,0,200)));
        h.getLevel().getChunkAt(net.minecraft.core.BlockPos.containing(p.position().add(0,0,260)));
        var within=target(h,p.position().add(10,0,200));var beyond=target(h,p.position().add(0,0,260));
        Vec3 point=within.getBoundingBox().getCenter();
        near(h,ArkCombat.aim(p,256).distanceTo(point),0,"locks offset crosshair enemy within 256 instead of aligned enemy beyond range");
        var attack=ViolenceEntity.launch(p,true);
        Vec3 direction=net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector(attack.view(),"Velocity").normalize();
        near(h,direction.distanceTo(point.subtract(p.getBoundingBox().getCenter()).normalize()),0,"throw points at locked center");attack.discard();
        within.discard();beyond.discard();
        near(h,ArkCombat.aim(p,256).distanceTo(p.getBoundingBox().getCenter().add(p.getLookAngle().scale(20))),0,"unlocked sight fallback");
        end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void identityTagsAndLoot(GameTestHelper h){
        var p=player(h);var stack=p.getMainHandItem();check(h,stack.getItem() instanceof SwordItem,"sword class");check(h,!stack.isDamageableItem(),"no durability");near(h,stack.getHoverName().getStyle().getColor().getValue(),0x6C2DC7,"Ark name color");
        check(h,stack.is(net.minecraft.tags.ItemTags.SWORDS),"vanilla sword tag");
        var modifiers=stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);near(h,modifiers.iterator().next().getAmount()+1,24.25,"panel damage");
        var allowed=EnumSet.of(Curse.AFLAME,Curse.EPHEMERAL,Curse.WITHERING,Curse.RESENTFUL,Curse.BLOODTHIRSTY,Curse.LECHEROUS);
        for(var curse:Curse.values())check(h,curse.accepts(stack)==allowed.contains(curse),"qualification "+curse);
        var boss=CalamitasContent.BOSS.get().create(h.getLevel());boss.setPos(p.position());
        var context=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY,boss).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,p.position()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,p.damageSources().playerAttack(p)).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        var table=h.getLevel().getServer().getLootData().getLootTable(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","entities/supreme_calamitas"));
        for(int i=0;i<20;i++)near(h,table.getRandomItems(context).stream().filter(s->s.is(ArkOfCosmos.VIOLENCE.get())).mapToInt(ItemStack::getCount).sum(),1,"one weapon each loot roll");end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void sourceClocksAndSingleEntity(GameTestHelper h){
        var p=player(h);ViolenceCombat.input(p,new ArkNetwork.Input(true,false,false));
        var attacks=h.getLevel().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(10));check(h,attacks.size()==1,"one left attack");var left=attacks.get(0);
        for(int i=0;i<20;i++)ViolenceCombat.input(p,new ArkNetwork.Input(true,true,false));near(h,h.getLevel().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(10)).size(),1,"duplicate input cannot spawn second mode");
        left.tick();near(h,((Integer)get(left,"age")),6,"left two updates times three frames");near(h,((Float)get(left,"rotation")),1.35,"source rotation");
        ViolenceCombat.input(p,new ArkNetwork.Input(false,false,false));left.tick();check(h,left.isRemoved()||left.view().getBoolean("Returning"),"release returns");left.discard();
        var right=ViolenceEntity.launch(p,true);near(h,((Vec3)get(right,"velocity")).length(),15*1.6666/16,"right velocity");near(h,(Float)get(right,"damage"),363.75,"15x base");right.tick();near(h,(Integer)get(right,"age"),15,"five updates times three frames");right.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void javelinSweepSnapshotAndFalloff(GameTestHelper h){
        var p=player(h);Curse.apply(p.getMainHandItem(),Curse.RESENTFUL);var t=target(h,p.position().add(0,0,7));
        var attack=ViolenceEntity.launch(p,true);var source=WeaponUser.source(attack,p);near(h,((Vec3)get(attack,"velocity")).length(),1.5624375,"launch speed");
        check(h,Curse.of(CurseCombat.weapon(source))==Curse.RESENTFUL,"curse captured at spawn");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));check(h,Curse.of(CurseCombat.weapon(source))==Curse.RESENTFUL,"swap preserves source snapshot");
        float factor=CurseCombat.modify(t,source,1);float before=t.getHealth();attack.tick();float dealt=before-t.getHealth();near(h,dealt,363.75*factor,"single curse multiplier through ArkDamage");
        check(h,ArkDamage.health(t)>=0,"Ark health authority installed");near(h,(Float)get(attack,"damage"),363.75*.9,"penetration decay");check(h,(Integer)get(attack,"hitstop")>0,"source damage-based hitstop");check(h,WitchFlames.strength(t)==1,"right applies ordinary Hex");
        before=t.getHealth();attack.tick();near(h,t.getHealth(),before,"javelin hits same target once");attack.discard();t.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void leftCooldownAndNoHex(GameTestHelper h){
        var p=player(h);ViolenceCombat.input(p,new ArkNetwork.Input(true,false,false));var attack=h.getLevel().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(10)).get(0);
        var t=target(h,p.position().add(0,0,1));set(attack,"velocity",Vec3.ZERO);attack.tick();near(h,t.getHealth(),975.75,"one left hit in first six substeps");check(h,!WitchFlames.weakened(t),"left has no intrinsic Hex");
        // Keep the source-controlled blade over the target for the next local cooldown.
        attack.setPos(t.getBoundingBox().getCenter());set(attack,"velocity",Vec3.ZERO);attack.tick();check(h,t.getHealth()<975.75,"local eight-update cooldown expires");attack.discard();t.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void mitigationTotemAndDeath(GameTestHelper h){
        var p=player(h);var attack=ViolenceEntity.launch(p,true);var source=WeaponUser.source(attack,p);
        var t=target(h,p.position().add(3,0,0));t.getAttribute(Attributes.ARMOR).setBaseValue(1000);t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,4));t.invulnerableTime=200;
        check(h,ArkDamage.hurt(t,source,100),"authority bypasses vanilla hurt timer");near(h,t.getHealth(),965,"35 percent floor");
        t.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));check(h,ArkDamage.hurt(t,source,10000),"fatal hit");check(h,t.isAlive()&&t.getOffhandItem().isEmpty(),"totem consumed");near(h,t.getHealth(),1,"totem leaves one");
        check(h,ArkDamage.hurt(t,source,10000),"second fatal hit");check(h,!t.isAlive()&&t.isRemoved(),"terminal native death and removal");attack.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void aflameThrowAndReturn(GameTestHelper h){
        var p=player(h);Curse.apply(p.getMainHandItem(),Curse.AFLAME);var t=target(h,p.position().add(0,0,7));var attack=ViolenceEntity.launch(p,true);attack.tick();
        check(h,WitchFlames.strength(t)==2,"Aflame throwing applies strong Hex");check(h,WitchFlames.active(t),"existing Aflame still applies");check(h,!BossHealingLock.active(t),"no boss healing lock");
        float health=t.getHealth();set(attack,"left",1);set(attack,"hitstop",0);attack.setPos(p.position().add(0,0,30));set(attack,"normalVelocity",new Vec3(0,0,-1.5624375));attack.tick();
        check(h,!attack.isRemoved()&&attack.view().getBoolean("Returning"),"return mode retains entity until it reaches owner");near(h,t.getHealth(),health,"javelin return cannot damage");attack.discard();t.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=100) public static void independentStrongHex(GameTestHelper h){
        var p=player(h);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);p.getAttribute(Attributes.ARMOR).setBaseValue(20);
        WitchFlames.hex(p,80,1);WitchFlames.hex(p,30,2);near(h,p.getArmorValue(),14,"armor30percent");near(h,CalamitasHexEffect.outgoing(p.damageSources().playerAttack(p),100),70,"outgoing30percent");p.removeAllEffects();damageTick(p);near(h,p.getHealth(),94,"double DoT survives shell removal");
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));damageTick(p);near(h,p.getHealth(),91,"double fire resistant DoT");
        h.runAfterDelay(32,()->{check(h,WitchFlames.strength(p)==1,"ordinary longer timer remains");damageTick(p);near(h,p.getArmorValue(),17,"armor reverts15percent");near(h,CalamitasHexEffect.outgoing(p.damageSources().playerAttack(p),100),85,"outgoing reverts15percent");});
        h.runAfterDelay(82,()->{check(h,WitchFlames.strength(p)==0,"all Hex timers expire");damageTick(p);near(h,p.getArmorValue(),20,"armor restored");WitchFlames.hex(p,80,2);WitchFlames.clear(p);check(h,WitchFlames.strength(p)==0,"clear removes strong deadline");end(h,p);});
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void ownerCleanupAndImmunity(GameTestHelper h){
        var p=player(h);var boss=CalamitasContent.BOSS.get().create(h.getLevel());WitchFlames.hex(boss,80,2);check(h,WitchFlames.strength(boss)==0,"boss immune to strong Hex");
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);WitchFlames.hex(p,80,2);check(h,WitchFlames.strength(p)==0,"creative immune");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ViolenceCombat.input(p,new ArkNetwork.Input(true,false,false));var attack=h.getLevel().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(10)).get(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));attack.tick();check(h,attack.isRemoved()||attack.view().getBoolean("Returning"),"swapped left returns");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ArkOfCosmos.VIOLENCE.get()));attack.discard();end(h,p);
    }
    @GameTest(template="violence_empty",timeoutTicks=40) public static void nativeTagFallbackAndLooting(GameTestHelper h){
        var p=player(h);p.getMainHandItem().enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING,3);
        var attack=ViolenceEntity.launch(p,true);var source=WeaponUser.source(attack,p);
        var pig=EntityType.PIG.create(h.getLevel());pig.setPos(p.position().add(3,0,0));pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);pig.getAttribute(Attributes.ARMOR).setBaseValue(100);pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,4));h.getLevel().addFreshEntity(pig);
        check(h,pig.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","ignore_special_damage"))),"test datapack tags native fallback target");ArkDamage.hurt(pig,source,100);near(h,pig.getHealth(),965,"tag fallback retains existing WeaponDamage floor");near(h,ArkDamage.health(pig),-1,"no second health authority");pig.discard();
        var t=target(h,p.position().add(3,0,0));boolean[] dropped={false};int[] looting={-1};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDropsEvent> drops=e->{if(e.getEntity()==t)dropped[0]=true;};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LootingLevelEvent> loot=e->{if(e.getEntity()==t)looting[0]=e.getLootingLevel();};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(drops);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,loot);
        try{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));ArkDamage.hurt(t,source,10000);check(h,dropped[0],"native death drops event dispatched");near(h,looting[0],3,"snapshot looting survives weapon swap");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(drops);net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(loot);}
        attack.discard();end(h,p);
    }
}
