package net.everla.projectc.arkofcosmos.entity;

import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Expiry uses each weapon's source clock, never a cache-size eviction. */
@Mod.EventBusSubscriber(modid="arkofcosmos")
public final class WeaponImmunityCleanup {
    private WeaponImmunityCleanup() {}
    static void prune(Map<LivingEntity,Map<UUID,Long>> table, Level level, long now) {
        table.entrySet().removeIf(entry->{
            if(entry.getKey().level()!=level)return false;
            if(entry.getKey().isRemoved())return true;
            entry.getValue().values().removeIf(until->until<=now);
            return entry.getValue().isEmpty();
        });
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.level.isClientSide||event.level.getGameTime()%20!=0)return;
        long time=event.level.getGameTime();
        prune(BiomeEntity.NPC_IMMUNITY,event.level,time*18);
        prune(BrokenBiomeEntity.NPC_IMMUNITY,event.level,time*18);
        prune(TrueBiomeEntity.NPC_IMMUNITY,event.level,time*6);
        prune(OldOneEntity.OWNER_IMMUNITY,event.level,time*3);
        BiomeEntity.MIST_IMMUNITY.entrySet().removeIf(e->e.getKey().level()==event.level&&(e.getKey().isRemoved()||e.getValue()<=time*18));
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        var entity=event.getEntity();var reason=entity.getRemovalReason();
        if(event.getLevel().isClientSide||reason==null||!reason.shouldDestroy())return;
        BiomeEntity.NPC_IMMUNITY.remove(entity);BrokenBiomeEntity.NPC_IMMUNITY.remove(entity);
        TrueBiomeEntity.NPC_IMMUNITY.remove(entity);OldOneEntity.OWNER_IMMUNITY.remove(entity);BiomeEntity.MIST_IMMUNITY.remove(entity);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide())return;
        BiomeEntity.NPC_IMMUNITY.keySet().removeIf(e->e.level()==event.getLevel());
        BrokenBiomeEntity.NPC_IMMUNITY.keySet().removeIf(e->e.level()==event.getLevel());
        TrueBiomeEntity.NPC_IMMUNITY.keySet().removeIf(e->e.level()==event.getLevel());
        OldOneEntity.OWNER_IMMUNITY.keySet().removeIf(e->e.level()==event.getLevel());
        BiomeEntity.MIST_IMMUNITY.keySet().removeIf(e->e.level()==event.getLevel());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        BiomeEntity.NPC_IMMUNITY.clear();BrokenBiomeEntity.NPC_IMMUNITY.clear();
        TrueBiomeEntity.NPC_IMMUNITY.clear();OldOneEntity.OWNER_IMMUNITY.clear();BiomeEntity.MIST_IMMUNITY.clear();
    }
}
