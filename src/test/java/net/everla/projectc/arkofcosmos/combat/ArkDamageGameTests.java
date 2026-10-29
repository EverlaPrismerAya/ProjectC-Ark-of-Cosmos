package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArkDamageGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void totemAndDelayedRevival(GameTestHelper helper) {
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkRevivalRegression"));
        player.setGameMode(GameType.SURVIVAL);
        var hand = net.minecraft.world.InteractionHand.OFF_HAND;
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        var source = player.damageSources().generic();
        ArkDamage.hurt(player, source, 1000);
        helper.assertTrue(player.isAlive() && player.getHealth() == 1 && player.getOffhandItem().isEmpty(),
                "Vanilla offhand totem must be consumed and prevent death");
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "Totem effects missing");
        player.setHealth(20);
        helper.assertTrue(player.getHealth() == 20, "Totem survivor cannot heal");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        ArkDamage.hurt(player, source, 1000);
        helper.assertTrue(player.getHealth() == 1 && player.getMainHandItem().isEmpty(), "Main hand totem failed");
        ArkDamage.hurt(player, source, 1000);
        helper.runAfterDelay(2, () -> {
            player.setHealth(player.getMaxHealth());
            player.heal(10);
            helper.assertTrue(player.getHealth() == 0 && !player.isAlive() && player.isDeadOrDying(),
                    "A later tick resurrected a finished Ark victim");
            var fresh = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), player.getGameProfile());
            helper.assertTrue(ArkDamage.health(fresh) < 0 && fresh.isAlive(), "New respawn entity inherited terminal health");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deferredDeathLoot(GameTestHelper helper) {
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkLootRegression"));
        player.setGameMode(GameType.SURVIVAL);
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        sword.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, 3);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
        DamageSource source = player.damageSources().playerAttack(player);
        for (boolean ark : new boolean[]{false, true}) {
            DeferredLootPig pig = new DeferredLootPig(helper);
            pig.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(ark ? 16 : 1, 8, 1)));
            helper.getLevel().addFreshEntity(pig);
            var area = pig.getBoundingBox().inflate(2);
            if (ark) {
                helper.assertTrue(ArkDamage.hurt(pig, source, 1000) && pig.isRemoved(), "Ark kill was prevented");
                helper.assertFalse(ArkDamage.hurt(pig, source, 1000), "Removed victim was killed twice");
            } else {
                helper.assertTrue(pig.hurt(source, 1000), "Ordinary kill was prevented");
                helper.assertTrue(pig.customDrops == 0, "Ordinary death no longer defers loot");
                pig.finishDeath();
            }
            helper.assertTrue(pig.customDrops == 1 && pig.looting == 3 && pig.playerCredit,
                    "Death loot lost exactly-once execution, looting, or player credit");
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
            int diamonds = drops.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.DIAMOND))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int emeralds = drops.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.EMERALD))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int gold = drops.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.GOLD_INGOT))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int xp = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area)
                    .stream().mapToInt(e -> e.getValue() * e.saveWithoutId(new CompoundTag()).getInt("Count")).sum();
            helper.assertTrue(diamonds == 1 && emeralds == 1 && gold == 1 && xp == 45,
                    "Missing or duplicate table/custom/equipment/XP drops: " + diamonds + "/" + emeralds + "/" + gold + "/" + xp);
        }
        helper.succeed();
    }

    /** Models bosses that defer the entire loot dispatcher until their death animation ends. */
    public static final class DeferredLootPig extends Pig {
        private DamageSource deathSource;
        int customDrops, looting;
        boolean playerCredit;
        DeferredLootPig(GameTestHelper helper) { super(EntityType.PIG, helper.getLevel()); }
        DeferredLootPig(EntityType<? extends Pig> type, net.minecraft.world.level.Level level) { super(type, level); }
        @Override public void die(DamageSource source) { deathSource = source; super.die(source); }
        @Override protected void dropAllDeathLoot(DamageSource source) {
            if (deathTime > 0) super.dropAllDeathLoot(source);
        }
        void finishDeath() { deathTime = 20; dropAllDeathLoot(deathSource); discard(); }
        @Override public int getExperienceReward() { return 45; }
        @Override protected net.minecraft.resources.ResourceLocation getDefaultLootTable() {
            return new net.minecraft.resources.ResourceLocation(ArkOfCosmos.MOD_ID, "entities/deferred");
        }
        @Override protected void dropCustomDeathLoot(DamageSource source, int level, boolean recentlyHit) {
            customDrops++; looting = level; playerCredit = recentlyHit;
            spawnAtLocation(net.minecraft.world.item.Items.EMERALD);
        }
        @Override protected void dropEquipment() { spawnAtLocation(net.minecraft.world.item.Items.GOLD_INGOT); }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taggedAnimatedDeath(GameTestHelper helper) {
        DeferredLootPig boss = new DeferredLootPig(ArkDummyGameTests.animatedType, helper.getLevel());
        helper.assertTrue(ArkDamage.hurt(boss, boss.damageSources().generic(), 1000), "Boss hit rejected");
        helper.assertTrue(boss.deathSource != null && boss.isDeadOrDying() && !boss.isRemoved()
                && boss.customDrops == 0, "Boss death animation or deferred loot was bypassed");
        helper.assertFalse(ArkDamage.hurt(boss, boss.damageSources().generic(), 1000), "Boss killed twice");
        boss.finishDeath();
        helper.assertTrue(boss.isRemoved() && boss.customDrops == 1, "Boss did not finish its own death and loot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armorAndResistance(GameTestHelper helper) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10);
        pig.setHealth(100);
        ArkDamage.hurt(pig, pig.damageSources().generic(), 20);
        helper.assertTrue(Math.abs(pig.getHealth() - 81.6f) < .001, "Armor reduction was bypassed");
        pig.setHealth(100);
        pig.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        ArkDamage.hurt(pig, pig.damageSources().generic(), 20);
        helper.assertTrue(Math.abs(pig.getHealth() - 85.71429f) < .001, "Armor toughness was bypassed");
        pig.setHealth(100);
        pig.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 0));
        ArkDamage.hurt(pig, pig.damageSources().generic(), 20);
        helper.assertTrue(Math.abs(pig.getHealth() - 85.28f) < .001, "Resistance I reduction was bypassed");
        pig.setHealth(100);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
        ArkDamage.hurt(pig, pig.damageSources().generic(), 20);
        helper.assertTrue(Math.abs(pig.getHealth() - 93) < .001, "Resistance V defeated the 35% floor");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deathCancellation(GameTestHelper helper) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        helper.getLevel().addFreshEntity(pig);
        int[] deaths = {0};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> cancel = event -> {
            if (event.getEntity() == pig) {
                deaths[0]++;
                pig.setHealth(pig.getMaxHealth());
                event.setCanceled(true);
            }
        };
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false,
                net.minecraftforge.event.entity.living.LivingDeathEvent.class, cancel);
        try {
            pig.hurt(pig.damageSources().genericKill(), 1000);
            helper.assertTrue(deaths[0] == 1 && pig.isAlive(), "Ordinary death event must remain cancellable");
            ArkDamage.hurt(pig, pig.damageSources().genericKill(), 1000);
            helper.assertTrue(deaths[0] == 1 && pig.isRemoved(), "Death event resurrected an Ark victim");
        } finally {
            bus.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void universalDamage(GameTestHelper helper) {
        GuardedPig pig = new GuardedPig(helper);
        pig.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        helper.getLevel().addFreshEntity(pig);
        DamageSource source = pig.damageSources().generic();
        helper.assertTrue(pig.getHealth() == 100 && !pig.hurt(source, 7), "Fixture must reject ordinary damage");
        helper.assertTrue(ArkDamage.hurt(pig, source, 7) && pig.getHealth() == 93, "Overridden health/limit was not bypassed");
        pig.invulnerableTime = 100;
        helper.assertTrue(ArkDamage.hurt(pig, source, 7) && pig.getHealth() == 86, "Same-tick damage respected cooldown");
        helper.assertTrue(pig.isAlive() && !pig.isDeadOrDying(), "Nonlethal hit changed survival state");
        CompoundTag saved = new CompoundTag();
        pig.saveWithoutId(saved);
        GuardedPig restored = new GuardedPig(helper);
        restored.load(saved);
        helper.assertTrue(restored.getHealth() == 86, "Authoritative health did not survive save/load");
        pig.setHealth(90);
        helper.assertTrue(pig.getHealth() == 90, "Healing did not update authoritative health");
        helper.assertTrue(ArkDamage.hurt(pig, source, 1000), "Lethal hit was rejected");
        helper.assertTrue(pig.isRemoved() && !pig.isAlive() && pig.isDeadOrDying(), "Death/removal overrides prevented lethal damage");

        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkDamageRegression"));
        player.setGameMode(GameType.CREATIVE);
        float before = player.getHealth();
        helper.assertFalse(ArkDamage.hurt(player, source, 1000), "Creative player was damaged");
        helper.assertTrue(player.getHealth() == before && ArkDamage.health(player) < 0, "Creative player acquired an override");
        player.setGameMode(GameType.SPECTATOR);
        helper.assertFalse(ArkDamage.hurt(player, source, 1000), "Spectator player was damaged");
        helper.assertTrue(player.getHealth() == before, "Spectator health changed");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(ArkDamage.hurt(player, source, 1) && player.getHealth() == before - 1,
                "Survival players must use the same damage authority");
        helper.assertTrue(ArkDamage.hurt(player, source, 1000) && player.isDeadOrDying(),
                "Survival player death was prevented");

        Pig ordinary = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(ordinary != null && ArkDamage.health(ordinary) < 0, "Unhit entity acquired an authority");
        ordinary.hurt(source, 1);
        helper.assertTrue(ArkDamage.health(ordinary) < 0, "Ordinary damage activated Ark health");
        helper.succeed();
    }

    /** No external mod dependency: reproduces independent HP and hostile overrides generically. */
    public static final class GuardedPig extends Pig {
        public GuardedPig(GameTestHelper helper) {
            super(EntityType.PIG, helper.getLevel());
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            setInvulnerable(true);
        }
        @Override public float getHealth() { return 100; }
        @Override public void setHealth(float health) {}
        @Override public boolean hurt(DamageSource source, float amount) { return false; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isDeadOrDying() { return false; }
        @Override public void die(DamageSource source) {}
        @Override public void remove(RemovalReason reason) {}
    }
}
