package net.everla.projectc.arkofcosmos.entity;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A world-side clock remains active when a boss's own AI has been suspended. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class MahouCombatWatchdog {
    private static final java.util.Set<MahouTsukaiEntity> BOSSES=
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private MahouCombatWatchdog(){}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof MahouTsukaiEntity boss){
            BOSSES.add(boss);
        }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event){
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof MahouTsukaiEntity boss)BOSSES.remove(boss);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var boss:BOSSES.toArray(MahouTsukaiEntity[]::new))
            if(!boss.isRemoved()&&boss.level() instanceof net.minecraft.server.level.ServerLevel level
                    &&level.getEntity(boss.getUUID())==boss)boss.monitorCombatStall();
    }
}
