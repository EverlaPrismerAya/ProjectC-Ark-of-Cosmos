package net.everla.projectc.arkofcosmos.revengeance;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModAttributes;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModMobEffects;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;


@EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public class RageSystemHandler {

    // === Event Listeners ===

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resetAttributes(event.getEntity());
        event.getEntity().getPersistentData().remove("lastRageCombatTime");
        event.getEntity().getPersistentData().remove("rageProximityUntil");
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player != null && !event.player.level().isClientSide() && event.player.isAlive() && !event.player.isSpectator()) {
            chargeRageForPlayer(event.player);
            handleRageAnimation(event.player);
            handleRageConsumption(event.player);
        }
    }

    /**
     * 处理怒气效果过期事件
     * 当怒气效果过期时播放音效
     */
    @SubscribeEvent
    public static void onMobEffectExpired(MobEffectEvent.Expired event) {
        // 检查是否为怒气效果过期
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect() == RevengeanceModMobEffects.RAGE_EFFECT.get()) {
            LivingEntity entity = event.getEntity();
            
            // 播放怒气结束音效
            if (!entity.level().isClientSide() && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null,
                        entity.getX(), entity.getY(), entity.getZ(),
                        RevengeanceModSounds.RAGE_END.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }


    /**
     * 重置实体的怒气和肾上腺素属性
     * 在玩家重生时调用此方法
     */
    public static void resetAttributes(Entity entity) {
        // 只处理LivingEntity
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        // 重置怒气值
        if (living.getAttributes().hasAttribute(RevengeanceModAttributes.RAGE_LEVEL.get())) {
            living.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).setBaseValue(0);
            // 同时清除相关标记
            living.getPersistentData().putBoolean("rageFullSoundPlayed", false);
            living.getPersistentData().putBoolean("enabledRage", false);
        }

        // 重置肾上腺素值
        if (living.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            living.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(0);
        }
    }

    // 创建一个静态的TagKey，避免重复创建
    private static final ResourceLocation RAGE_TAG_LOCATION = 
            ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":not_trigger_rage");
    private static final TagKey<EntityType<?>> NOT_TRIGGER_RAGE = 
            TagKey.create(ForgeRegistries.ENTITY_TYPES.getRegistryKey(), RAGE_TAG_LOCATION);

    // 怒气充能和流失的常量
    private static double config(String key) { return net.everla.projectc.arkofcosmos.ProjectCCommonConfig.boost("rage",key); }

    /** Calamity 1a8cebd: proximity rage uses the best target, never a sum of enemies. */
    public static void chargeRageForPlayer(Player player) {
        var meter=player.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get());
        if(player.level().isClientSide() || meter==null || player.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()))return;
        double rate=0;
        Vec3 center=player.getBoundingBox().getCenter();
        // Source checks all active enemies; a large half-extent can qualify a distant center.
        for(var entity:((ServerLevel)player.level()).getAllEntities()) {
            if(!(entity instanceof LivingEntity enemy))continue;
            if(enemy==player || !enemy.isAlive() || enemy.getType().is(NOT_TRIGGER_RAGE) || enemy instanceof Player)continue;
            boolean boss=BossBarDetection.hasBossBar(enemy);
            if(!boss && !isHostileToPlayer(enemy,player))continue;
            var box=enemy.getBoundingBox();
            // Source uses center distance minus the largest half-extent; extend that rule to three axes.
            double halfExtent=Math.max(box.getXsize(),Math.max(box.getYsize(),box.getZsize()))*.5;
            double distance=center.distanceTo(box.getCenter())-halfExtent;
            double candidate=proximityRate(distance);
            if(boss && !enemy.getType().is(NO_BOSS_RAGE_BONUS))candidate*=config("boss_charge_multiplier");
            rate=Math.max(rate,candidate);
        }
        long now=player.level().getGameTime();
        var data=player.getPersistentData();
        if(rate>0) {
            // Proximity only holds combat open briefly; it does not grant the 10-second hit grace.
            data.putLong("rageProximityUntil",now+1);
            meter.setBaseValue(Math.min(config("required_charge"),meter.getValue()+rate*config("required_charge")/config("charge_ticks")));
        } else {
            boolean hitGrace=data.contains("lastRageCombatTime") && now-data.getLong("lastRageCombatTime")<config("combat_delay_ticks");
            if(!hitGrace && now>=data.getLong("rageProximityUntil"))
                meter.setBaseValue(Math.max(0,meter.getValue()-config("required_charge")/config("decay_ticks")));
        }
    }

    private static final TagKey<EntityType<?>> NO_BOSS_RAGE_BONUS=TagKey.create(ForgeRegistries.ENTITY_TYPES.getRegistryKey(),
            ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"no_boss_rage_bonus"));

    /** Source curve in pixels, with 16 pixels mapped to one block. */
    public static double proximityRate(double distance) {
        double outer=config("proximity_radius"),inner=Math.min(config("full_charge_radius"),outer);
        if(distance>outer)return 0;
        if(distance<=inner)return 1;
        double d=(distance-inner)/(outer-inner)*640;
        return net.minecraft.util.Mth.clamp(1/(.034*d+2)+(590.5-d)/1181,0,1);
    }

    private static boolean isHostileToPlayer(LivingEntity enemy,LivingEntity player) {
        return !enemy.getType().getCategory().isFriendly() || enemy.getLastHurtByMob()==player
                || enemy.getLastHurtMob()==player || enemy instanceof net.minecraft.world.entity.Mob mob && mob.getTarget()==player;
    }

    public static int durationTicks() { return (int)config("duration_ticks")+20*(int)config("upgrade_count"); }

    /** Successful native and direct damage both count as combat, including player-owned projectiles. */
    public static void recordDamage(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,float damage) {
        if(victim.level().isClientSide() || !Float.isFinite(damage) || damage<=0)return;
        long now=victim.level().getGameTime();
        if(victim instanceof Player)victim.getPersistentData().putLong("lastRageCombatTime",now);
        if(source.getEntity() instanceof Player attacker)attacker.getPersistentData().putLong("lastRageCombatTime",now);
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        recordDamage(event.getEntity(),event.getSource(),event.getAmount());
    }

    /**
     * 激活怒气效果
     * 当怒气值达到100时，添加怒气效果并标记状态
     */
    public static void activateRage(Entity entity) {
        // 快速类型检查
        if (!(entity instanceof LivingEntity
                living)) {
            return;
        }

        // 检查是否有怒气属性
        if (!living.getAttributes().hasAttribute
                (RevengeanceModAttributes.RAGE_LEVEL.get())) {
            return;
        }

        // 检查怒气值是否达到100且没有激活怒气效果
        if (living.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).getValue() >= config("required_charge") 
                && !living.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get())) {
            // 仅在服务器端添加效果
            if (!entity.level().isClientSide())
            {
                // 添加怒气效果
                living.addEffect(new
                        MobEffectInstance(RevengeanceModMobEffects.RAGE_EFFECT.get(), durationTicks(), 0, true, true));

                // 播放音效
                if (living.level() instanceof
                        ServerLevel serverLevel) {
                    serverLevel.playSound(null,
                            living.getX(), living.getY(), living.getZ(),

                            RevengeanceModSounds.RAGE_ACTIVITE.get(),
                            SoundSource.PLAYERS, 1.0F, 1.0F);
                }

                // 生成粒子效果
                if (living.level() instanceof
                        ServerLevel serverLevel) {
                    // 愤怒村民粒子
                    //(angry_villager particles)
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            living.getX(),
                            living.getY(), living.getZ(),
                            20, 1.0, 1.0, 1.0,
                            1.0);

                    // 深红色孢子粒子
                    //(crimson_spore_particles)
                    serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                            living.getX(),
                            living.getY() + 1, living.getZ(),
                            100, 0.0, 0.0, 0.0,
                            1.0);
                }
            }
            entity.getPersistentData().putBoolean("enabledRage", true);
        }
    }

    /**
     * 处理怒气动画和音效
     * 当怒气值达到100时播放音效
     */
    public static void
    handleRageAnimation(Entity entity) {
        //快速检查：必须是LivingEntity且在服务器端
        if (!(entity instanceof LivingEntity
                living) || living.level().isClientSide()) {
            return;
        }

        // 获取怒气值，如果没有怒气属性则默认为0
        double rageValue =
                living.getAttributes().hasAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()) ?
                        living.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).getValue() : 0;

        // 处理音效播放逻辑
        boolean soundPlayed = living.getPersistentData().getBoolean("rageFullSoundPlayed");

        if (rageValue >= config("required_charge") && !soundPlayed) {
            // 播放怒气满值音效
            if (living.level() instanceof
                    ServerLevel serverLevel) {
                serverLevel.playSound(null,
                        living.getX(), living.getY(), living.getZ(),

                        RevengeanceModSounds.RAGE_FULL.get(),
                        living.getSoundSource(), 1.0F, 1.0F);
            }
            living.getPersistentData().putBoolean("rageFullSoundPlayed", true);
        } else if (rageValue < config("required_charge") &&
                soundPlayed) {
            // 重置音效状态
            living.getPersistentData().putBoolean("rageFullSoundPlayed", false);
        }
    }

    /**
     * 处理怒气消耗逻辑
     * 当玩家有怒气效果时，逐渐消耗rage_level
     */
    public static void handleRageConsumption(Player player) {
        // 检查玩家是否有怒气属性
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.RAGE_LEVEL.get())) {
            return;
        }

        // 检查玩家是否有怒气效果
        if (player.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get())) {
            double currentRage = player.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).getValue();
            
            if (currentRage > 0) {
                // Drain over the duration captured at activation, including permanent upgrades.
                double consumptionRate = currentRage / Math.max(1, player.getEffect(RevengeanceModMobEffects.RAGE_EFFECT.get()).getDuration()); // 每tick消耗的怒气值
                double newRage = Math.max(0, currentRage - consumptionRate);
                
                player.getAttribute(RevengeanceModAttributes.RAGE_LEVEL.get()).setBaseValue(newRage);
            }
        }
    }
}