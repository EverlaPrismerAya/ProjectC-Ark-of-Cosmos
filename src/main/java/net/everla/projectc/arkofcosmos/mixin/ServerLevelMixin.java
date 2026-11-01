package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the vanilla event that sends the hurt animation and sound to clients. */
@Mixin(value=ServerLevel.class,remap=false)
abstract class ServerLevelMixin {
    @Inject(method={"broadcastDamageEvent","m_269196_"},at=@At("TAIL"))
    private void ark$damageFeedback(Entity target,DamageSource source,CallbackInfo ci){
        if(target instanceof MahouTsukaiEntity boss)boss.receivedDamageFeedback(source);
    }
}
