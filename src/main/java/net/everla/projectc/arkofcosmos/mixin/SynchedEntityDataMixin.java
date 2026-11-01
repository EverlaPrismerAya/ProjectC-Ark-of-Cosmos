package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.combat.BossHealingLock;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clamp the final write after a custom container has applied its own damage rules. */
@Mixin(value=SynchedEntityData.class,remap=false)
abstract class SynchedEntityDataMixin {
    @Shadow(aliases="f_135344_") @Final private Entity entity;

    @ModifyVariable(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            "m_276349_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V"},
            at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Object ark$healingWrite(Object value,EntityDataAccessor<?> key,Object original,boolean force){
        return BossHealingLock.dataWrite(entity,key,value);
    }

    @Inject(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            "m_276349_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V"},at=@At("RETURN"))
    private void ark$healingCommitted(EntityDataAccessor<?> key,Object value,boolean force,CallbackInfo ci){
        if(entity instanceof LivingEntity living)BossHealingLock.synced(living,key);
    }
}
