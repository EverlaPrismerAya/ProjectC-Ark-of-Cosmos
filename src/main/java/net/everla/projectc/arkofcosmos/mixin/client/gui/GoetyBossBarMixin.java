package net.everla.projectc.arkofcosmos.mixin.client.gui;

import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.Polarice3.Goety.client.events.BossBarEvent",remap=false)
public abstract class GoetyBossBarMixin {
    @Shadow public static Map<UUID,Mob> BOSS_BARS;
    @Inject(method="renderBossBar",at=@At("HEAD"),cancellable=true,remap=false)
    private static void arkofcosmos$apollyonBar(CustomizeGuiOverlayEvent.BossEventProgress event,CallbackInfo ci) {
        if(net.everla.projectc.arkofcosmos.client.ApollyonBossBar.render(event,BOSS_BARS))ci.cancel();
    }
}
