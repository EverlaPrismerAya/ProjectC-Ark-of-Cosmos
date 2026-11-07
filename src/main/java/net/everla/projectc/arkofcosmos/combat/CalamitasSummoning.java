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
