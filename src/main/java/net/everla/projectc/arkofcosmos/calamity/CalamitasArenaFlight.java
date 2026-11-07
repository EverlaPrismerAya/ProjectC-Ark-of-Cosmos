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
    private static final String SPEED = "ArkCalamitasArenaFlightSpeed";
    private CalamitasArenaFlight() {}

    public static void update(ServerPlayer player) {
        boolean inside = player.isAlive()
                && SupremeCalamitasEntity.loaded(player.level()).stream().anyMatch(boss -> {
                    var bounds = boss.arenaBounds();
                    return bounds != null && bounds.contains(player.getBoundingBox().getCenter());
                });
        if (!inside) { release(player); return; }
        var abilities = player.getAbilities();
        boolean changed = false;
        if (!player.isCreative() && !player.isSpectator() && !abilities.mayfly) {
            player.getPersistentData().putBoolean(LEASE, true);
            abilities.mayfly = true;
            changed = true;
        }
        var data = player.getPersistentData();
        if (!data.contains(SPEED)) data.putFloat(SPEED, abilities.getFlyingSpeed());
        float speed = data.getFloat(SPEED) * 2;
        if (Float.compare(abilities.getFlyingSpeed(), speed) != 0) {
            abilities.setFlyingSpeed(speed);
            changed = true;
        }
        if (changed) player.onUpdateAbilities();
    }

    public static void release(ServerPlayer player) {
        var data = player.getPersistentData();
        var abilities = player.getAbilities();
        boolean changed = false;
        if (data.contains(SPEED)) {
            abilities.setFlyingSpeed(data.getFloat(SPEED));
            data.remove(SPEED);
            changed = true;
        }
        if (data.getBoolean(LEASE)) {
            data.remove(LEASE);
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.fallDistance = 0;
                changed = true;
            }
        }
        if (changed) player.onUpdateAbilities();
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
