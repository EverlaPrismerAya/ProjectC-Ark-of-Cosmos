package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Roll once after Apollyon's identity is set, before its first world insertion. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class CalamitasSummoning {
    private CalamitasSummoning() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void windup(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()
                || !(event.getEntity() instanceof SupremeCalamitasEntity boss)
                || boss.getPersistentData().getBoolean("ArkSummoningComplete")) return;
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety", "summon_apostle"));
        if (type == null) return;
        var circle = type.create(level);
        if (circle == null) return;
        circle.setPos(boss.position());
        circle.setCustomName(net.minecraft.network.chat.Component.translatable("dialogue.arkofcosmos.calamitas.summoning")
                .withStyle(s -> s.withColor(0xFF8000)));
        var saved = new net.minecraft.nbt.CompoundTag();
        boss.save(saved);
        circle.getPersistentData().put("ArkCalamitasSummon", saved);
        if (boss.getTarget() != null) circle.getPersistentData().putUUID("ArkCalamitasTarget", boss.getTarget().getUUID());
        if (level.addFreshEntity(circle)) {
            boss.getPersistentData().putBoolean("ArkSummoningQueued", true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void replaceApollyon(EntityJoinLevelEvent event) {
        if (!CalamitasContent.ENABLED || event.loadedFromDisk()
                || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.NETHER
                || !(event.getEntity() instanceof Mob original)
                || !(original instanceof ApollyonCompatTarget apollyon) || !apollyon.arkofcosmos$isNetherApollyon()) return;
        var data = original.getPersistentData();
        if (data.getBoolean("ArkCalamitasReplacementRolled")) return;
        data.putBoolean("ArkCalamitasReplacementRolled", true);
        double chance = ProjectCCommonConfig.calamitasValue("apollyon_replacement_chance");
        if (chance <= 0 || original.getRandom().nextDouble() >= chance) return;
        // If another mod rejects Calamitas, preserve the original summon.
        if (SupremeCalamitasEntity.spawn(level, original.position(), original.getTarget()) != null)
            event.setCanceled(true);
    }
}
