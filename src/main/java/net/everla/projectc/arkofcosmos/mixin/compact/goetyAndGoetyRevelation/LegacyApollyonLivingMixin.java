package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.everla.projectc.arkofcosmos.combat.LegacyApollyonDamageWindow;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import z1gned.goetyrevelation.util.ApollyonAbilityHelper;

/** The 4.9 vanilla hurt/settlement hooks, gated to the old lower Apollyon implementation. */
@Mixin(value=LivingEntity.class,priority=-3000)
public abstract class LegacyApollyonLivingMixin {
    @ModifyExpressionValue(method="hurt",at=@At(value="CONSTANT",args="floatValue=10.0F",ordinal=0))
    private float ark$wholeWindow(float original){
        return LegacyApollyonDamageWindow.get((LivingEntity)(Object)this)==null?original:0;
    }
    @WrapOperation(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",opcode=Opcodes.GETFIELD))
    private int ark$windowTicks(LivingEntity self,Operation<Integer> original){
        return LegacyApollyonDamageWindow.get(self)==null?original.call(self):((ApollyonAbilityHelper)self).allTitlesApostle_1_20_1$getHitCooldown();
    }
    @WrapOperation(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",opcode=Opcodes.PUTFIELD))
    private void ark$deferWindowStart(LivingEntity self,int value,Operation<Void> original){
        if(LegacyApollyonDamageWindow.get(self)==null)original.call(self,value);
    }
    @WrapOperation(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;lastHurt:F",opcode=Opcodes.GETFIELD))
    private float ark$lastSuccessfulHit(LivingEntity self,Operation<Float> original){
        var window=LegacyApollyonDamageWindow.get(self);
        return window==null?original.call(self):window.getLastHurt();
    }
    @WrapOperation(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;lastHurt:F",opcode=Opcodes.PUTFIELD))
    private void ark$deferLastHit(LivingEntity self,float value,Operation<Void> original){
        if(LegacyApollyonDamageWindow.get(self)==null)original.call(self,value);
    }
    @WrapOperation(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean ark$protectWindow(DamageSource source,TagKey<DamageType> tag,Operation<Boolean> original){
        boolean result=original.call(source,tag);
        return tag.equals(DamageTypeTags.BYPASSES_COOLDOWN)&&LegacyApollyonDamageWindow.get((LivingEntity)(Object)this)!=null?false:result;
    }
    @WrapOperation(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),require=2)
    private void ark$dispatch(LivingEntity self,DamageSource source,float damage,Operation<Void> original,DamageSource incomingSource,float rawDamage){
        var window=LegacyApollyonDamageWindow.get(self);
        if(window==null)original.call(self,source,damage);
        else window.dispatchActuallyHurt(rawDamage,()->original.call(self,source,damage));
    }
    @WrapMethod(method="actuallyHurt",require=1)
    private void ark$settlement(DamageSource source,float damage,Operation<Void> original){
        var window=LegacyApollyonDamageWindow.get((LivingEntity)(Object)this);
        if(window==null)original.call(source,damage);
        else window.actuallyHurt(()->original.call(source,damage));
    }
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),require=1)
    private void ark$healthWrite(LivingEntity self,float health,Operation<Void> original){
        var window=LegacyApollyonDamageWindow.get(self);
        if(window==null)original.call(self,health);
        else window.writeHealth(health,()->original.call(self,health));
    }
}
