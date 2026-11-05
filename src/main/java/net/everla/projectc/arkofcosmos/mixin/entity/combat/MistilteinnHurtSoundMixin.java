package net.everla.projectc.arkofcosmos.mixin.entity.combat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.everla.projectc.arkofcosmos.item.MistilteinnItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MistilteinnHurtSoundMixin {

    // Client damage packets query getHurtSound directly, without calling playHurtSound.
    @ModifyExpressionValue(method="handleDamageEvent",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;"))
    private SoundEvent ark$bonkPacketSound(SoundEvent original,DamageSource source){
        return source.is(MistilteinnItem.BONK_DAMAGE) ? null : original;
    }

    @Inject(method="playHurtSound",at=@At("HEAD"),cancellable=true)
    private void ark$bonkOnly(DamageSource source,CallbackInfo ci){
        if(source.is(MistilteinnItem.BONK_DAMAGE))ci.cancel();
    }
}
