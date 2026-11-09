package net.everla.projectc.arkofcosmos.mixin.client;

import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip only the GR/RF death cage drawing, never the damage routine or other bosses. */
@Pseudo
@Mixin(targets={"z1gned.goetyrevelation.client.event.ClientHandler","com.mega.revelationfix.common.apollyon.client.ClientDeathPerformance"})
public abstract class SatsukiDomainCageMixin {
    @Inject(method="apollyonDeath",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ark$hideCage(RenderLivingEvent<?,?> event,CallbackInfo ci){
        if(event.getEntity() instanceof SatsukiBoss)ci.cancel();
    }
}
