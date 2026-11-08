package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class CalamitasBattleEvents {
    private CalamitasBattleEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof SupremeCalamitasEntity boss)) return;
        for (var item : event.getDrops()) {
            item.setPos(boss.summonPosition());
            item.setGlowingTag(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pearl(EntityTeleportEvent.EnderPearl event) {
        // A pearl can already be outside when a fast wall overtakes it.
        if (CalamitasArenaBoundary.constrainDestination(event.getPlayer(), event.getTarget())
                .distanceToSqr(event.getTarget()) > 1e-16) event.setCanceled(true);
    }
}
