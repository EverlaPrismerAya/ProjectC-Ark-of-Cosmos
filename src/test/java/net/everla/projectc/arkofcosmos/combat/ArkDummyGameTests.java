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

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            type = EntityType.Builder.of(RecordingDummy::new, MobCategory.MISC).sized(1, 1)
                    .build("dummmmmmy:target_dummy");
            helper.register(new ResourceLocation("dummmmmmy", "target_dummy"), type);
        });
    }

    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(type, Pig.createAttributes().build());
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

    /** Reproduces the dummy's ordinary Mob damage path and infinite-health setter. */
    public static final class RecordingDummy extends Pig {
        int hurtCalls;
        public RecordingDummy(EntityType<? extends Pig> type, Level level) { super(type, level); }
        @Override public boolean hurt(DamageSource source, float amount) {
            hurtCalls++;
            return super.hurt(source, amount);
        }
        @Override public void setHealth(float health) {
            if (health > 0) super.setHealth(health);
        }
    }
}
