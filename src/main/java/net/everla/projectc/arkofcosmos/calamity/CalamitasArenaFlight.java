package net.everla.projectc.arkofcosmos.calamity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A persisted lease prevents temporary arena flight from surviving logout or a restart. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class CalamitasArenaFlight {
    private static final String LEASE = "ArkCalamitasArenaFlight";
    private CalamitasArenaFlight() {}

    public static void update(ServerPlayer player) {
        boolean inside = !player.isCreative() && !player.isSpectator() && player.isAlive()
                && SupremeCalamitasEntity.loaded(player.level()).stream().anyMatch(boss -> {
                    var bounds = boss.arenaBounds();
                    return bounds != null && bounds.contains(player.getBoundingBox().getCenter());
                });
        if (!inside) { release(player); return; }
        var abilities = player.getAbilities();
        if (!abilities.mayfly) {
            player.getPersistentData().putBoolean(LEASE, true);
            abilities.mayfly = true;
            player.onUpdateAbilities();
        }
    }

    public static void release(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(LEASE)) return;
        player.getPersistentData().remove(LEASE);
        if (player.isCreative() || player.isSpectator()) return;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.fallDistance = 0;
        player.onUpdateAbilities();
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) update(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) release(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { release(player); update(player); }
    }
}
