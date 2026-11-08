package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class HiddenAdvancements {
    public static void award(Player player,String id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var advancement=serverPlayer.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"adventure/"+id));
        if (advancement!=null) serverPlayer.getAdvancements().award(advancement,"complete");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        var victim=event.getEntity();
        if (!(victim instanceof WaterAnimal || victim instanceof Turtle || victim instanceof Guardian)) return;
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() instanceof NecromanticAttackEntity attack
                && switch(attack.kind()) { case RANCOR, ARM, LARGE_CINDER -> true; default -> false; })
            award(player,"brimstone_sea");
    }
}
