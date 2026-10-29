package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArkDummyGameTests {
    private static EntityType<RecordingDummy> type;
    private static EntityType<RecordingDummy> witchType;
    static EntityType<ArkDamageGameTests.DeferredLootPig> animatedType;
    private static final java.util.Map<String, EntityType<Pig>> enemyTypes = new java.util.LinkedHashMap<>();

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            type = EntityType.Builder.of(RecordingDummy::new, MobCategory.MISC).sized(1, 1)
                    .build("dummmmmmy:target_dummy");
            helper.register(new ResourceLocation("dummmmmmy", "target_dummy"), type);
            witchType = EntityType.Builder.of(RecordingDummy::new, MobCategory.MISC).sized(1, 1)
                    .build("touhou_little_maid_spell:stellar_witch");
            helper.register(new ResourceLocation("touhou_little_maid_spell", "stellar_witch"), witchType);
            animatedType = EntityType.Builder.<ArkDamageGameTests.DeferredLootPig>of(ArkDamageGameTests.DeferredLootPig::new, MobCategory.MISC)
                    .sized(1, 1).build("arkofcosmos:animated_test");
            helper.register(new ResourceLocation(ArkOfCosmos.MOD_ID, "animated_test"), animatedType);
            for (String id : new String[]{"alexsmobs:seagull", "iceandfire:if_pixie", "alexsmobs:fly", "alexsmobs:cockroach"}) {
                EntityType<Pig> enemyType = EntityType.Builder.of(Pig::new, MobCategory.MISC).sized(1, 1).build(id);
                helper.register(new ResourceLocation(id), enemyType);
                enemyTypes.put(id, enemyType);
            }
        });
    }

    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(type, Pig.createAttributes().build());
        event.put(witchType, Pig.createAttributes().build());
        event.put(animatedType, Pig.createAttributes().build());
        enemyTypes.values().forEach(enemyType -> event.put(enemyType, Pig.createAttributes().build()));
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ark_compatibility")
    public static void seagullFullDamage(GameTestHelper helper) {
        Pig seagull = new Pig(enemyTypes.get("alexsmobs:seagull"), helper.getLevel()) {
            @Override public float getHealth() { return Float.MAX_VALUE; }
        };
        seagull.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0);
        seagull.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0);
        seagull.getAttribute(Attributes.ARMOR).setBaseValue(30);
        seagull.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        seagull.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        for (var hand : net.minecraft.world.InteractionHand.values())
            seagull.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        helper.assertTrue(seagull.getHealth() == Float.MAX_VALUE, "Fixture does not have maximum float health");
        helper.assertTrue(ArkDamage.hurt(seagull, seagull.damageSources().generic(), 1)
                && seagull.getHealth() == 0 && seagull.isRemoved(),
                "Seagull damage was not full Float.MAX_VALUE or was prevented by a totem");
        helper.assertTrue(seagull.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                && seagull.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING),
                "Seagull activated or consumed a totem");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ark_compatibility")
    public static void taggedEnemiesAndFlyAdvancement(GameTestHelper helper) {
        // Forge deliberately refuses to award advancements to FakePlayer.
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkFlyRegression"));
        player.connection = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkNetworkStub")).connection;
        for (EntityType<Pig> enemyType : enemyTypes.values()) {
            Pig enemy = enemyType.create(helper.getLevel());
            helper.assertTrue(ArkCombat.enemy(player, enemy), "Tagged passive entity was not an enemy");
            enemy.setHealth(0);
            helper.assertFalse(ArkCombat.enemy(player, enemy), "Enemy tag bypassed survival filter");
        }
        Pig ordinary = EntityType.PIG.create(helper.getLevel());
        helper.assertFalse(ArkCombat.enemy(player, ordinary), "Untagged passive entity became an enemy");
        var advancement = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(ArkOfCosmos.MOD_ID, "adventure/fly_swatter"));
        helper.assertTrue(advancement != null && advancement.getDisplay().getFrame()
                == net.minecraft.advancements.FrameType.GOAL
                && advancement.getParent().getId().equals(new ResourceLocation("minecraft", "adventure/root")),
                "Fly goal missing from Adventure");
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        var source = player.damageSources().playerAttack(player);
        Pig normalFly = enemyTypes.get("alexsmobs:fly").create(helper.getLevel());
        normalFly.hurt(source, 1000);
        helper.assertFalse(progress.isDone(), "Ordinary fly kill awarded Ark advancement");
        ArkDamage.hurt(ordinary, source, 1000);
        helper.assertFalse(progress.isDone(), "Non-fly kill awarded fly advancement");
        Pig fly = enemyTypes.get("alexsmobs:fly").create(helper.getLevel());
        ArkDamage.hurt(fly, source, 1);
        helper.assertFalse(progress.isDone(), "Nonlethal fly hit awarded advancement");
        fly.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        ArkDamage.hurt(fly, source, 1000);
        helper.assertFalse(progress.isDone(), "Totem survivor awarded fly advancement");
        ArkDamage.hurt(fly, source, 1000);
        helper.assertTrue(progress.isDone(), "Ark fly kill did not award advancement");
        helper.assertFalse(player.getAdvancements().award(advancement, "kill_fly"), "Advancement awarded twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryDummy(GameTestHelper helper) {
        RecordingDummy dummy = new RecordingDummy(type, helper.getLevel());
        dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        dummy.setHealth(100);
        dummy.getAttribute(Attributes.ARMOR).setBaseValue(30);
        dummy.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        dummy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        helper.assertTrue(ArkDamage.hurt(dummy, dummy.damageSources().generic(), 20), "Dummy ordinary hit was rejected");
        helper.assertTrue(dummy.hurtCalls == 1 && Math.abs(dummy.getHealth() - 93) < .001,
                "Dummy did not retain ordinary hurt/setHealth and 35% floor");
        helper.assertTrue(ArkDamage.health(dummy) < 0 && !dummy.isRemoved(), "Dummy entered advanced damage");
        dummy.invulnerableTime = 0;
        dummy.hurt(dummy.damageSources().generic(), 20);
        helper.assertTrue(Math.abs(dummy.getHealth() - 93) < .001, "Floor leaked into an unrelated hit");
        dummy.invulnerableTime = 0;
        ArkDamage.hurt(dummy, dummy.damageSources().generic(), 1000);
        helper.assertTrue(!dummy.isRemoved() && ArkDamage.health(dummy) < 0, "Dummy used forced removal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryWitchDeath(GameTestHelper helper) {
        RecordingDummy witch = new RecordingDummy(witchType, helper.getLevel());
        ArkDamage.restore(witch, 0);
        helper.assertTrue(witch.isAlive() && ArkDamage.health(witch) < 0, "Witch restored special health");
        ArkDamage.hurt(witch, witch.damageSources().generic(), 1000);
        helper.assertTrue(witch.hurtCalls == 1 && witch.deathCalls == 1 && !witch.isRemoved()
                && ArkDamage.health(witch) < 0 && !witch.forcedDeath,
                "Witch did not retain its own hurt/death logic");
        helper.succeed();
    }

    /** Reproduces the dummy's ordinary Mob damage path and infinite-health setter. */
    public static final class RecordingDummy extends Pig {
        int hurtCalls;
        int deathCalls;
        boolean forcedDeath;
        public RecordingDummy(EntityType<? extends Pig> type, Level level) { super(type, level); }
        @Override public boolean hurt(DamageSource source, float amount) {
            hurtCalls++;
            return super.hurt(source, amount);
        }
        @Override public void setHealth(float health) {
            if (health > 0 || getType() == witchType) super.setHealth(health);
        }
        @Override public void die(DamageSource source) {
            deathCalls++;
            forcedDeath = ArkDamage.finishing(this);
            super.die(source);
        }
    }
}
