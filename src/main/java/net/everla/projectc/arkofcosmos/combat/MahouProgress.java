package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player progression survives death, including victories saved by older versions. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class MahouProgress {
    private static final String DEFEATED="arkofcosmos_mahou_defeated";

    private MahouProgress() {}

    public static boolean defeated(Player player) {
        var data=player.getPersistentData();
        return data.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(DEFEATED)
                ||data.getBoolean(DEFEATED);
    }

    public static void markDefeated(Player player) {
        var data=player.getPersistentData();
        var persisted=data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(DEFEATED,true);
        data.put(Player.PERSISTED_NBT_TAG,persisted);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if(defeated(event.getOriginal()))markDefeated(event.getEntity());
    }
}
