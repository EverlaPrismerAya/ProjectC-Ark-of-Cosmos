package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MahouBossBarGameTests {
    private static ServerBossEvent bar(MahouTsukaiEntity entity) throws Exception {
        var field = MahouTsukaiEntity.class.getDeclaredField("bossEvent");
        field.setAccessible(true);
        return (ServerBossEvent) field.get(entity);
    }
    private static void state(GameTestHelper helper, MahouTsukaiEntity entity, int phase, float health) throws Exception {
        helper.assertTrue(entity.phase() == phase && Math.abs(entity.getHealth() - health) < .001f,
                "Incorrect phase/health: " + entity.phase() + "/" + entity.getHealth());
        helper.assertTrue(Math.abs(bar(entity).getProgress() - (health / entity.getMaxHealth() - (phase == 1 ? .5f : phase == 2 ? .25f : 0f)) / (phase == 1 ? .5f : phase == 2 ? .75f : 1f)) < .001f,
                "Recovery health failed to reach boss bar");
        helper.assertTrue(bar(entity).getName().equals(entity.getDisplayName()), "Stage name failed to reach boss bar");
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mahou_bossbar")
    public static void gradualRecoveryAndNames(GameTestHelper helper) throws Exception {
        var boss = ArkOfCosmos.MAHOU_TSUKAI.get().create(helper.getLevel());boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});
        var other = ArkOfCosmos.MAHOU_TSUKAI.get().create(helper.getLevel());
        helper.assertTrue(boss.phase() == 1 && !bar(boss).getId().equals(bar(other).getId()), "Default phase/independent bar IDs failed");
        Component firstName = boss.getName();
        net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,500);boss.aiStep();state(helper,boss,1,500);
        boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),2);
        helper.assertTrue(boss.getHealth() == 499 && boss.phase() == 1, "Damage instantly restored or advanced phase");
        boss.aiStep();state(helper,boss,1,549);
        CompoundTag saved = new CompoundTag();boss.saveWithoutId(saved);
        var restored = ArkOfCosmos.MAHOU_TSUKAI.get().create(helper.getLevel());
        restored.load(saved);restored.aiStep();state(helper,restored,1,599);
        for (int i=0;i<9;i++) boss.aiStep();
        state(helper,boss,1,999);
        boss.aiStep();state(helper,boss,2,1000);
        Component secondName = boss.getName();
        helper.assertTrue(!secondName.equals(firstName), "Phase two retained phase one name");
        for(int i=0;i<300;i++)boss.aiStep();
        net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,250);boss.aiStep();state(helper,boss,2,250);
        boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),1f/.67f);
        for(int i=0;i<15;i++) boss.aiStep();
        state(helper,boss,2,999);
        boss.aiStep();state(helper,boss,3,1000);
        helper.assertTrue(!boss.getName().equals(secondName), "Phase three retained phase two name");
        net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,100);boss.aiStep();state(helper,boss,3,100);
        boss.setCustomName(Component.literal("Custom Aya"));boss.aiStep();
        helper.assertTrue(bar(boss).getName().equals(boss.getDisplayName()), "Explicit custom name lost");
        boss.saveWithoutId(saved);restored.load(saved);restored.aiStep();state(helper,restored,3,100);
        helper.assertTrue(Component.literal("Custom Aya").equals(restored.getName()), "Custom name lost across save/load");
        helper.assertTrue(other.phase()==1 && other.getHealth()==1000, "Other boss changed");
        boss.discard();other.discard();restored.discard();helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mahou_bossbar")
    public static void lethalHitsDoNotSkipPhases(GameTestHelper helper) throws Exception {
        var boss = ArkOfCosmos.MAHOU_TSUKAI.get().create(helper.getLevel());boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});
        boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        helper.assertTrue(boss.isAlive() && boss.phase()==1 && boss.getHealth()==1, "Lethal hit skipped recovery");
        for(int i=0;i<20;i++)boss.aiStep();
        state(helper,boss,2,1000);
        for(int i=0;i<300;i++)boss.aiStep();
        boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        for(int i=0;i<20;i++)boss.aiStep();
        state(helper,boss,3,1000);
        boss.invulnerableTime=0;boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        helper.assertTrue(boss.isDeadOrDying(), "Phase three cannot die normally");
        boss.discard();helper.succeed();
    }
    private static MahouTsukaiEntity phaseBoss(GameTestHelper helper, int phase) {
        var boss = ArkOfCosmos.MAHOU_TSUKAI.get().create(helper.getLevel());boss.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag() {{ putInt("PhaseTransitionTicks",0); }});
        CompoundTag tag = new CompoundTag();boss.saveWithoutId(tag);
        tag.putByte("Phase", (byte) phase);boss.load(tag);
        return boss;
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mahou_bossbar")
    public static void stageDamageReductionIncludingArk(GameTestHelper helper) {
        for (int phase=1;phase<=3;phase++) {
            var boss = phaseBoss(helper,phase);
            float damage = phase==1 ? 50 : phase==2 ? 67 : 100;
            boss.hurt(boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),100);
            helper.assertTrue(Math.abs(boss.getHealth()-(1000-damage))<.001f,"Ordinary phase reduction failed");
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,1000);
            ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),100);
            ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),100);
            helper.assertTrue(Math.abs(boss.getHealth()-(1000-2*damage))<.001f,"Ark reduction or cooldown bypass failed in phase "+phase);
            helper.assertTrue(ArkDamage.health(boss)<0,"Ark replaced staged boss health authority");
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,1000);
            boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,200,4));
            ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),100);
            helper.assertTrue(Math.abs(boss.getHealth()-(1000-damage*.35f))<.001f,"35% floor bypassed the boss's phase reduction");
            boss.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mahou_bossbar")
    public static void remainingStageHealth(GameTestHelper helper) throws Exception {
        for(int phase=1;phase<=3;phase++) {
            var boss=phaseBoss(helper,phase);
            float threshold=phase==1?500:phase==2?250:0;
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,(1000+threshold)/2);boss.aiStep();
            helper.assertTrue(Math.abs(bar(boss).getProgress()-.5f)<.001f,"Half remaining stage pool must display half bar");
            net.everla.projectc.arkofcosmos.entity.MahouTestHealth.set(boss,threshold);
            if(phase<3) boss.aiStep();
            helper.assertTrue(boss.bossBarProgress()==0,"Threshold must display empty bar");
            boss.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mahou_bossbar")
    public static void arkCannotSkipPhaseRecovery(GameTestHelper helper) throws Exception {
        var boss=phaseBoss(helper,1);
        ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        helper.assertTrue(boss.isAlive() && boss.getHealth()==1 && boss.phase()==1,"Ark skipped first phase recovery");
        for(int i=0;i<20;i++)boss.aiStep();state(helper,boss,2,1000);
        for(int i=0;i<300;i++)boss.aiStep();
        ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        helper.assertTrue(boss.isAlive() && boss.getHealth()==1 && boss.phase()==2,"Ark skipped second phase recovery");
        for(int i=0;i<20;i++)boss.aiStep();state(helper,boss,3,1000);
        ArkDamage.hurt(boss,boss.damageSources().playerAttack(new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhaseTest"))),10000);
        helper.assertTrue(boss.isDeadOrDying(),"Ark cannot kill final phase");
        boss.discard();helper.succeed();
    }

}
