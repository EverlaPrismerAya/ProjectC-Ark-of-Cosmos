package net.everla.projectc.arkofcosmos.revengeance;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;

import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModAttributes;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModMobEffects;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public class AdrenalineSystemHandler {

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { hurtPauseTime.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { hurtPauseTime.remove(event.getEntity().getUUID()); }
    // 肾上腺素常量
    private static double config(String key) { return net.everla.projectc.arkofcosmos.ProjectCCommonConfig.boost("adrenaline",key); }

    // 存储玩家受伤暂停充能的时间
    private static final Map<UUID, Long> hurtPauseTime = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player != null && !event.player.level().isClientSide() && event.player.isAlive() && !event.player.isSpectator()) {
            handleAdrenalineCharging(event.player);
            handleAdrenalineDecay(event.player);
            handleAdrenalineConsumption(event.player);
            handleAdrenalineFullEffect(event.player);
            handleAdrenalineFullSound(event.player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 清除暂停充能时间
        hurtPauseTime.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getAmount() > 0 && event.getEntity() instanceof Player player) {
            handleAdrenalineLossOnHurt(player, event.getAmount());
        }
    }


    /**
     * 处理肾上腺素充能逻辑
     * 当128格内有boss实体时开始充能
     */
    public static void handleAdrenalineCharging(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        // 如果肾上腺素效果正在激活，不进行充能
        if (player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get())) {
            return;
        }

        // 检查是否在受伤暂停期间
        long currentTime = player.level().getLevelData().getGameTime();
        UUID playerId = player.getUUID();
        
        if (hurtPauseTime.containsKey(playerId)) {
            long pauseEndTime = hurtPauseTime.get(playerId);
            if (currentTime < pauseEndTime) {
                return; // 还在暂停期间，不充能
            } else {
                hurtPauseTime.remove(playerId); // 暂停结束，移除记录
            }
        }

        // 检查是否有boss在128格范围内
        if (hasBossNearby(player)) {
            double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
            
            if (currentAdrenaline < config("required_charge")) {
                // 充能
                double newValue = Math.min(config("required_charge"), currentAdrenaline + (config("required_charge") / config("charge_ticks")));
                player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(newValue);
            } else {
                // 已充满，持续给予抗性提升2效果
                giveResistanceEffect(player);
            }
        }
    }

    /**
     * 处理肾上腺素自然损失
     * 当没有boss在附近时持续损失
     */
    public static void handleAdrenalineDecay(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        // 如果有肾上腺素效果激活，不进行自然损失
        if (player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get())) {
            return;
        }

        // 如果有boss在附近，不损失
        if (hasBossNearby(player)) {
            return;
        }

        double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
        
        if (currentAdrenaline > 0) {
            double newValue = Math.max(0, currentAdrenaline - (config("required_charge") / config("decay_ticks")));
            player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(newValue);
            
            // 重置充满音效标记
            if (newValue < config("required_charge")) {
                player.getPersistentData().putBoolean("adrenalineFullSoundPlayed", false);
            }
        }
    }

    /**
     * 处理肾上腺素效果激活时的消耗
     * 100刻内从满值逐渐减少至0
     */
    public static void handleAdrenalineConsumption(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        if (player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get())) {
            double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
            
            if (currentAdrenaline > 0) {
                // 100刻内均匀消耗完所有肾上腺素
                double consumptionRate = currentAdrenaline / Math.max(1, player.getEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get()).getDuration());
                double newValue = Math.max(0, currentAdrenaline - consumptionRate);
                player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(newValue);
            }
        }
    }

    /**
     * 处理肾上腺素满值时的效果
     */
    public static void handleAdrenalineFullEffect(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
        
        // 如果肾上腺素已满且有boss在附近
        if (currentAdrenaline >= config("required_charge") && hasBossNearby(player)) {
            giveResistanceEffect(player);
        }
    }

    /**
     * 处理肾上腺素充满音效
     */
    public static void handleAdrenalineFullSound(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
        boolean soundPlayed = player.getPersistentData().getBoolean("adrenalineFullSoundPlayed");

        if (currentAdrenaline >= config("required_charge") && !soundPlayed) {
            // 播放肾上腺素充满音效
            playAdrenalineFullSound(player);
            player.getPersistentData().putBoolean("adrenalineFullSoundPlayed", true);
        } else if (currentAdrenaline < config("required_charge") && soundPlayed) {
            // 重置音效状态
            player.getPersistentData().putBoolean("adrenalineFullSoundPlayed", false);
        }
    }

    /**
     * 激活肾上腺素效果
     */
    public static void activateAdrenaline(Player player) {
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
        
        // 检查是否已满且没有激活效果
        if (currentAdrenaline >= config("required_charge") && !player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get())) {
            if (!player.level().isClientSide()) {
                // 给予肾上腺素效果
                player.addEffect(new MobEffectInstance(RevengeanceModMobEffects.ADRENALINE_EFFECT.get(), 
                    (int)config("duration_ticks"), 0, true, true));
                
                // 播放激活音效
                playAdrenalineActivateSound(player);
                
                // 生成粒子效果
                generateAdrenalineParticles(player);
            }
        }
    }

    /**
     * 处理受伤时的肾上腺素损失
     */
    /** Shared by native hurt events and Ark's direct health settlement (which bypasses those events). */
    public static void handleAdrenalineLossOnHurt(Player player, float damage) {
        if (player.level().isClientSide() || !Float.isFinite(damage) || damage <= 0) return;
        if (!player.getAttributes().hasAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get())) {
            return;
        }

        // 暂停充能1秒
        long currentTime = player.level().getLevelData().getGameTime();
        hurtPauseTime.put(player.getUUID(), currentTime + (long)config("hurt_pause_ticks")); // 20刻 = 1秒

        double currentAdrenaline = player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).getValue();
        if (currentAdrenaline <= 0) {
            return;
        }

        // 检查肾上腺素是否已满，如果满了则损失全部
        if (currentAdrenaline >= config("required_charge")) {
            player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(0);
            
            // 重置充满音效标记
            player.getPersistentData().putBoolean("adrenalineFullSoundPlayed", false);
            
            // 播放充能中断音效
            if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    RevengeanceModSounds.ADRENALINE_CHARGE_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }

        float maxHealth = player.getMaxHealth();
        int damageInt = (int) Math.floor(damage);
        double lossAmount = 0;

        if (maxHealth < 20) {
            if (damageInt == 1) {
                lossAmount = config("required_charge") * 0.1; // 损失10%
            } else if (damageInt > 1) {
                lossAmount = config("required_charge"); // 损失全部
            }
        } else {
            if (damageInt == 1) {
                lossAmount = config("required_charge") * 0.05; // 损失5%
            } else if (damageInt > 1 && damage < maxHealth * 0.1) {
                lossAmount = config("required_charge") * 0.1; // 损失10%
            } else if (damage >= maxHealth * 0.1) {
                lossAmount = config("required_charge"); // 损失全部
            }
        }

        if (lossAmount > 0) {
            double newValue = Math.max(0, currentAdrenaline - lossAmount);
            player.getAttribute(RevengeanceModAttributes.ADRENALINE_LEVEL.get()).setBaseValue(newValue);
            
            // 重置充满音效标记
            player.getPersistentData().putBoolean("adrenalineFullSoundPlayed", false);
        }
    }

    /**
     * 检查128格范围内是否有boss
     */
    private static boolean hasBossNearby(Player player) {
        Vec3 playerPos = player.position();
        AABB searchArea = new AABB(
            playerPos.x - config("detection_radius"), playerPos.y - config("detection_radius"), playerPos.z - config("detection_radius"),
            playerPos.x + config("detection_radius"), playerPos.y + config("detection_radius"), playerPos.z + config("detection_radius")
        );

        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, searchArea)) {
            if (entity != player && BossBarDetection.hasBossBar(entity)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 给予抗性提升2效果（不显示粒子）
     */
    private static void giveResistanceEffect(Player player) {
        if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE) || 
            player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() < 1) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
        }
    }

    /**
     * 播放肾上腺素充满音效
     */
    private static void playAdrenalineFullSound(Player player) {
        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                RevengeanceModSounds.ADRENALINE_FULL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * 播放肾上腺素激活音效
     */
    private static void playAdrenalineActivateSound(Player player) {
        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                RevengeanceModSounds.ADRENALINE_ACTIVITE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * 生成肾上腺素激活粒子效果
     */
    private static void generateAdrenalineParticles(Player player) {
        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
            // 电火花粒子
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1, player.getZ(),
                20, 0, 0, 0, 3.0);
            
            // 附魔击中粒子
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1, player.getZ(),
                30, 0, 0, 0, 1.0);
        }
    }
}
