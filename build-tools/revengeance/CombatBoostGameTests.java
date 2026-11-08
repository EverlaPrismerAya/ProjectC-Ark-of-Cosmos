package net.everla.projectc.arkofcosmos.revengeance;

import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

@GameTestHolder("arkofcosmos") @PrefixGameTestTemplate(false)
public class CombatBoostGameTests {
    static class CustomBar extends net.minecraft.server.level.ServerBossEvent {
        CustomBar() { super(net.minecraft.network.chat.Component.literal("custom"), net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS); }
    }
    static class BarHolder extends net.minecraft.world.entity.animal.Cow {
        private final net.minecraft.world.BossEvent bar;
        private static final CustomBar UNRELATED_STATIC_BAR = new CustomBar();
        BarHolder(net.minecraft.world.level.Level level, boolean hasBar) { super(EntityType.COW,level); bar = hasBar ? new CustomBar() : null; }
    }
    static class InheritedBoss extends BarHolder {
        InheritedBoss(net.minecraft.world.level.Level level, boolean hasBar) { super(level,hasBar); }
    }
    static void near(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.00001,message+": "+actual+" != "+expected);
    }
    @GameTest(template="violence_empty",timeoutTicks=40)
    public static void chargeActivateDamageAndReset(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"boost-test"));
        h.getLevel().addNewPlayer(player);player.setPos(h.absoluteVec(new Vec3(4,150,4)));player.setNoGravity(true);
        var rage=player.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get());
        var adrenaline=player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get());
        h.assertTrue(rage!=null&&adrenaline!=null,"player receives both synced attributes");
        h.assertTrue(RevengeanceModAttributes.RAGE_LEVEL.get().isClientSyncable()&&RevengeanceModAttributes.ADRENALINE_LEVEL.get().isClientSyncable(),"HUD attributes synchronize");
        RageSystemHandler.activateRage(player);AdrenalineSystemHandler.activateAdrenaline(player);
        h.assertTrue(!player.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get())&&!player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get()),"cannot activate empty meters");
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(player.position().add(1,0,0));zombie.setNoAi(true);h.getLevel().addFreshEntity(zombie);
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),100.0/900,"inner radius charge per tick");
        zombie.discard();
        var boss=EntityType.WITHER.create(h.getLevel());boss.setPos(player.position().add(4,0,0));boss.setNoAi(true);h.getLevel().addFreshEntity(boss);
        h.assertTrue(BossBarDetection.hasBossBar(boss),"wither detected from server boss event");
        h.assertTrue(!BossBarDetection.hasBossBar(zombie),"ordinary/dead mob has no boss bar");
        var custom = new InheritedBoss(h.getLevel(),true);
        h.assertTrue(BossBarDetection.hasBossBar(custom),"inherited private custom ServerBossEvent subclass detected");
        h.assertTrue(!BossBarDetection.hasBossBar(new InheritedBoss(h.getLevel(),false)),"cached field metadata still checks instance value; static bars ignored");
        var ordinary = EntityType.COW.create(h.getLevel());
        h.assertTrue(!BossBarDetection.hasBossBar(ordinary),"ordinary living mob rejected");
        boss.discard();custom.setPos(player.position().add(4,0,0));custom.setNoAi(true);h.getLevel().addFreshEntity(custom);
        for(int i=0;i<601;i++)AdrenalineSystemHandler.handleAdrenalineCharging(player);
        near(h,adrenaline.getValue(),10000,"boss charges adrenaline in 30 seconds");
        AdrenalineSystemHandler.handleAdrenalineFullEffect(player);
        h.assertTrue(player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE).getAmplifier()==1,"full adrenaline grants resistance II");
        rage.setBaseValue(100);double base=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // Same two independent branches as the shared-key packet consumer.
        RageSystemHandler.activateRage(player);AdrenalineSystemHandler.activateAdrenaline(player);
        h.assertTrue(player.getEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()).getDuration()==240,"fully upgraded rage lasts 12 seconds");
        h.assertTrue(player.getEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get()).getDuration()==100,"adrenaline lasts 5 seconds");
        near(h,player.getAttributeValue(Attributes.ATTACK_DAMAGE),base*1.35*3.1,"both damage modifiers stack");
        RageSystemHandler.handleRageConsumption(player);AdrenalineSystemHandler.handleAdrenalineConsumption(player);
        near(h,rage.getValue(),100-100.0/240,"rage consumes over 240 ticks");near(h,adrenaline.getValue(),9900,"adrenaline consumes over 100 ticks");
        player.removeEffect(RevengeanceModMobEffects.RAGE_EFFECT.get());player.removeEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get());
        adrenaline.setBaseValue(5000);AdrenalineSystemHandler.onPlayerHurt(new LivingHurtEvent(player,player.damageSources().generic(),1));
        near(h,adrenaline.getValue(),4500,"one damage removes 5 percent");
        AdrenalineSystemHandler.handleAdrenalineCharging(player);near(h,adrenaline.getValue(),4500,"damage pauses charging for one second");
        adrenaline.setBaseValue(10000);AdrenalineSystemHandler.onPlayerHurt(new LivingHurtEvent(player,player.damageSources().generic(),1));near(h,adrenaline.getValue(),0,"damage breaks full meter");
        custom.setHealth(0);h.assertTrue(!BossBarDetection.hasBossBar(custom),"dead boss rejected");custom.discard();adrenaline.setBaseValue(1000);AdrenalineSystemHandler.handleAdrenalineDecay(player);near(h,adrenaline.getValue(),500,"no boss decays in one second");
        rage.setBaseValue(50);player.getPersistentData().putLong("lastRageCombatTime",h.getLevel().getGameTime()-200);player.getPersistentData().remove("rageProximityUntil");
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),50-100.0/600,"rage fades ten seconds after damage");
        rage.setBaseValue(100);adrenaline.setBaseValue(10000);RageSystemHandler.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player,false));
        near(h,rage.getValue(),0,"respawn clears rage");near(h,adrenaline.getValue(),0,"respawn clears adrenaline");
        var original=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.snapshot();
        var configured=original.copy();
        configured.putDouble("combat_boost.rage.required_charge",40);
        configured.putDouble("combat_boost.rage.duration_ticks",40);
        configured.putDouble("combat_boost.rage.upgrade_count",0);
        configured.putDouble("combat_boost.rage.damage_multiplier",2);
        configured.putDouble("combat_boost.adrenaline.required_charge",2000);
        configured.putDouble("combat_boost.adrenaline.duration_ticks",50);
        configured.putDouble("combat_boost.adrenaline.damage_multiplier",4);
        configured.putDouble("weapons.violence.base_damage",50);
        configured.putDouble("weapons.rancor.base_damage",27);
        configured.putDouble("weapons.gruesome_eminence.base_damage",72);
        configured.putDouble("weapons.gruesome_eminence.soul_cost",60);
        try {
            net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(configured);
            rage.setBaseValue(40);adrenaline.setBaseValue(2000);
            double unboosted=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            RageSystemHandler.activateRage(player);AdrenalineSystemHandler.activateAdrenaline(player);
            h.assertTrue(player.getEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()).getDuration()==40,"configured rage duration");
            h.assertTrue(player.getEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get()).getDuration()==50,"configured adrenaline duration");
            near(h,player.getAttributeValue(Attributes.ATTACK_DAMAGE),unboosted*8,"configured multipliers stack");
            RageSystemHandler.handleRageConsumption(player);AdrenalineSystemHandler.handleAdrenalineConsumption(player);
            near(h,rage.getValue(),39,"configured rage consumption");near(h,adrenaline.getValue(),1960,"configured adrenaline consumption");
            near(h,((net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem)net.everla.projectc.arkofcosmos.CalamitasContent.RANCOR.get()).damage(),27,"rancor configured damage");
            var eminence=(net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem)net.everla.projectc.arkofcosmos.CalamitasContent.GRUESOME_EMINENCE.get();
            near(h,eminence.damage(),72,"eminence configured damage");near(h,eminence.soulCost(),60,"eminence configured soul cost");
            var stack=new net.minecraft.world.item.ItemStack(net.everla.projectc.arkofcosmos.ArkOfCosmos.VIOLENCE.get());
            var modifiers=stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
            near(h,modifiers.get(Attributes.ATTACK_DAMAGE).iterator().next().getAmount(),49,"violence configured item damage");
        } finally {
            player.removeEffect(RevengeanceModMobEffects.RAGE_EFFECT.get());player.removeEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get());
            net.everla.projectc.arkofcosmos.ProjectCCommonConfig.receive(original);
        }
        AdrenalineSystemHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);h.succeed();
    }
    @GameTest(template="violence_empty",timeoutTicks=40)
    public static void directBossDamageBreaksAdrenaline(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"boss-boost-test"));
        h.getLevel().addNewPlayer(player);player.setPos(h.absoluteVec(new Vec3(4,150,4)));player.setNoGravity(true);
        var boss=net.everla.projectc.arkofcosmos.CalamitasContent.BOSS.get().create(h.getLevel());
        boss.setPos(player.position().add(4,0,0));boss.setNoAi(true);h.getLevel().addFreshEntity(boss);
        var source=player.damageSources().mobAttack(boss);
        var meter=player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get());
        double maximum=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.boost("adrenaline","required_charge");
        player.getAttribute(Attributes.ARMOR).setBaseValue(30);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,200,1));
        meter.setBaseValue(maximum);float before=player.getHealth();
        h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(player,source,10),"Calamitas direct damage accepted");
        h.assertTrue(player.getHealth()<before,"armored player actually loses health");
        near(h,meter.getValue(),0,"direct boss damage breaks full adrenaline");
        meter.setBaseValue(maximum);
        h.assertTrue(!net.everla.projectc.arkofcosmos.combat.ArkDamage.hurt(player,source,10),"boss shared cooldown rejects repeat hit");
        near(h,meter.getValue(),maximum,"rejected hit leaves adrenaline unchanged");
        player.getAttribute(Attributes.ARMOR).setBaseValue(0);player.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        meter.setBaseValue(maximum*.5);
        // Non-boss authority uses the same settlement, without the boss hit cooldown.
        h.assertTrue(net.everla.projectc.arkofcosmos.combat.ArkDamage.hurtUnmitigated(player,player.damageSources().generic(),1),"direct weapon damage accepted");
        near(h,meter.getValue(),maximum*.45,"partial adrenaline loses five percent exactly once");
        h.assertTrue(BossBarDetection.hasBossBar(boss),"nearby Calamitas would otherwise charge adrenaline");
        AdrenalineSystemHandler.handleAdrenalineCharging(player);near(h,meter.getValue(),maximum*.45,"direct damage pauses charging");
        boss.discard();
        AdrenalineSystemHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);h.succeed();
    }
    @GameTest(template="violence_empty",timeoutTicks=40)
    public static void calamityProximityRage(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"proximity-test"));
        h.getLevel().addNewPlayer(player);player.setPos(h.absoluteVec(new Vec3(4,220,4)));player.setNoGravity(true);
        var rage=player.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get());
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(player.position().add(2,0,0));zombie.setNoAi(true);h.getLevel().addFreshEntity(zombie);
        var second=EntityType.ZOMBIE.create(h.getLevel());second.setPos(player.position().add(3,0,0));second.setNoAi(true);h.getLevel().addFreshEntity(second);
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),100.0/900,"multiple enemies never stack");
        var boss=new InheritedBoss(h.getLevel(),true);boss.setPos(player.position().add(5,0,0));boss.setNoAi(true);h.getLevel().addFreshEntity(boss);
        rage.setBaseValue(0);RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),100.0/300,"boss fills in 15 seconds, even behind nearer enemies");
        boss.discard();zombie.discard();second.discard();
        near(h,RageSystemHandler.proximityRate(10),1,"inner hitbox distance yields full rate");
        double d=160;near(h,RageSystemHandler.proximityRate(20),1/(.034*d+2)+(590.5-d)/1181,"source nonlinear curve at 20 blocks");
        near(h,RageSystemHandler.proximityRate(50.0001),0,"beyond outer radius gives no charge");
        rage.setBaseValue(50);player.getPersistentData().remove("rageProximityUntil");
        RageSystemHandler.recordDamage(player,player.damageSources().generic(),1);
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),50,"taking damage holds combat grace");
        player.getPersistentData().putLong("lastRageCombatTime",h.getLevel().getGameTime()-200);player.getPersistentData().remove("rageProximityUntil");
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),50-100.0/600,"combat grace expires after ten seconds");
        RageSystemHandler.recordDamage(EntityType.COW.create(h.getLevel()),player.damageSources().playerAttack(player),1);
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),50-100.0/600,"dealing damage also holds combat grace");
        rage.setBaseValue(100);RageSystemHandler.activateRage(player);
        h.assertTrue(player.getEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()).getDuration()==240,"three permanent boosters give 12 seconds");
        RageSystemHandler.chargeRageForPlayer(player);near(h,rage.getValue(),100,"no proximity charging during active rage");
        player.removeEffect(RevengeanceModMobEffects.RAGE_EFFECT.get());
        h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);h.succeed();
    }
}
